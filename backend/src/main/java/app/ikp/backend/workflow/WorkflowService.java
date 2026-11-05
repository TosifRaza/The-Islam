package app.ikp.backend.workflow;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.book.Book;
import app.ikp.backend.book.BookFile;
import app.ikp.backend.book.BookRepository;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unified content publication workflow (approved spec §3.19.5) for BOOKS —
 * the state machine lives here, never in clients:
 *
 *   draft --submit-review--> in_editorial_review
 *   in_editorial_review --approve-editorial--> in_scholarly_review | approved
 *   in_editorial_review --reject--> draft            (notes required)
 *   in_scholarly_review --approve (Scholar Reviewer)--> approved
 *   in_scholarly_review --reject--> draft            (notes required)
 *   approved --publish--> published                  (content.publish)
 *   published --unpublish--> approved
 *   draft|approved --archive--> archived
 *
 * Invariants enforced server-side (docs/07-security-plan.md §4 / spec §4):
 * every transition checks the CURRENT state (WORKFLOW-001 otherwise), requires
 * the authorised role/permission, records a review row (decision + reviewer
 * identity + notes), writes an audit event in the same transaction, and
 * publication additionally requires ALL attached files to be scan-clean.
 */
@Service
public class WorkflowService {

    private static final Set<String> ACTIONS = Set.of(
            "submit-review", "approve-editorial", "approve", "reject", "publish", "unpublish", "archive");

    private final BookRepository books;
    private final ContentReviewRecordRepository reviews;
    private final ContentVersionRepository versions;
    private final UserRepository users;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public WorkflowService(BookRepository books, ContentReviewRecordRepository reviews,
                           ContentVersionRepository versions, UserRepository users,
                           AuditService audit, ObjectMapper mapper) {
        this.books = books;
        this.reviews = reviews;
        this.versions = versions;
        this.users = users;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public Book transition(UUIDBookRef ref, String action, String notes, SecurityPrincipal actor) {
        if (!ACTIONS.contains(action)) {
            throw ApiException.validation("action must be one of " + ACTIONS);
        }
        Book book = books.findByPublicId(ref.id())
                .orElseThrow(() -> ApiException.notFound("book not found"));
        Book.Status before = book.getStatus();
        switch (action) {
            case "submit-review" -> {
                requireState(book, Book.Status.draft, action);
                requirePermission(actor, "content.edit", action, book);
                book.setStatus(Book.Status.in_editorial_review);
            }
            case "approve-editorial" -> {
                requireState(book, Book.Status.in_editorial_review, action);
                requireEditorialReviewer(actor, action, book);
                saveReview("book", book.getId(), ContentReviewRecord.Stage.editorial_review,
                        ContentReviewRecord.Decision.approved, actor, notes);
                book.setStatus(book.isScholarlyReviewRequired()
                        ? Book.Status.in_scholarly_review
                        : Book.Status.approved);
            }
            case "approve" -> {
                requireState(book, Book.Status.in_scholarly_review, action);
                requireScholarReviewer(actor, action, book);
                saveReview("book", book.getId(), ContentReviewRecord.Stage.scholarly_review,
                        ContentReviewRecord.Decision.approved, actor, notes);
                book.setStatus(Book.Status.approved);
            }
            case "reject" -> {
                if (notes == null || notes.isBlank()) {
                    throw ApiException.validation("rejection requires notes (reason)");
                }
                requireState(book, Book.Status.in_editorial_review, Book.Status.in_scholarly_review, action);
                if (book.getStatus() == Book.Status.in_scholarly_review) {
                    requireScholarReviewer(actor, action, book);
                    saveReview("book", book.getId(), ContentReviewRecord.Stage.scholarly_review,
                            ContentReviewRecord.Decision.rejected, actor, notes);
                } else {
                    requireEditorialReviewer(actor, action, book);
                    saveReview("book", book.getId(), ContentReviewRecord.Stage.editorial_review,
                            ContentReviewRecord.Decision.rejected, actor, notes);
                }
                book.setStatus(Book.Status.draft);
            }
            case "publish" -> {
                requireState(book, Book.Status.approved, action);
                requirePermission(actor, "content.publish", action, book);
                // Permission ≠ publication: files must be scan-clean as well.
                boolean allClean = book.getFiles().stream()
                        .allMatch(f -> f.getMedia().isClean());
                if (!allClean) {
                    audit.record(actor.id(), "workflow.deny", "book", book.getId(),
                            Map.of("state", before.name()), Map.of("action", action),
                            "publish refused: attached media not scan-clean");
                    throw ApiException.workflow("publish refused: attached media is not scan-clean");
                }
                book.setStatus(Book.Status.published);
                book.setPublishedAt(java.time.Instant.now());
                saveVersionSnapshot(book, actor);
            }
            case "unpublish" -> {
                requireState(book, Book.Status.published, action);
                requirePermission(actor, "content.publish", action, book);
                book.setStatus(Book.Status.approved);
            }
            case "archive" -> {
                requireState(book, Book.Status.draft, Book.Status.approved, action);
                requirePermission(actor, "content.publish", action, book);
                book.setStatus(Book.Status.archived);
                book.setArchivedAt(java.time.Instant.now());
            }
            default -> throw ApiException.validation("unsupported action");
        }
        audit.record(actor.id(), "workflow." + action, "book", book.getId(),
                Map.of("state", before.name()), Map.of("state", book.getStatus().name()), notes);
        return book;
    }

    public record TransitionResult(Book book, Book.Status before, Book.Status after,
                                   String actorPublicId, java.time.Instant changedAt) {
    }

    private void requireState(Book book, Book.Status expected, String action) {
        if (book.getStatus() != expected) {
            deny(book, action, "state " + book.getStatus() + " does not permit " + action + " (requires " + expected + ")");
        }
    }

    private void requireState(Book book, Book.Status e1, Book.Status e2, String action) {
        if (book.getStatus() != e1 && book.getStatus() != e2) {
            deny(book, action, "state " + book.getStatus() + " does not permit " + action);
        }
    }

    private void requirePermission(SecurityPrincipal actor, String permission, String action, Book book) {
        boolean has = actor.authorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
        if (!has) {
            deny(book, action, "actor lacks permission " + permission);
        }
    }

    /** Editorial review: Super Admin/Admin/Content Manager only (books matrix). */
    private void requireEditorialReviewer(SecurityPrincipal actor, String action, Book book) {
        Set<String> allowed = Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_CONTENT_MANAGER");
        boolean has = actor.authorities().stream().anyMatch(a -> allowed.contains(a.getAuthority()));
        if (!has) {
            deny(book, action, "editorial decisions require Super Admin, Admin or Content Manager");
        }
    }

    /** Scholarly review: Scholar Reviewer is the sole authority (security plan §3). */
    private void requireScholarReviewer(SecurityPrincipal actor, String action, Book book) {
        boolean has = actor.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SCHOLAR_REVIEWER"));
        if (!has) {
            deny(book, action, "scholarly approval requires the Scholar Reviewer role");
        }
    }

    private void deny(Book book, String action, String detail) {
        // WORKFLOW-001: nothing changed; the denied attempt IS audited in its own
        // transaction so the rollback of the failed operation cannot erase it.
        audit.recordDenial("workflow.deny", "book", book.getId(), action + ": " + detail);
        throw ApiException.workflow(detail);
    }

    private void saveReview(String entityType, Long entityId, ContentReviewRecord.Stage stage,
                            ContentReviewRecord.Decision decision, SecurityPrincipal actor, String notes) {
        ContentReviewRecord row = new ContentReviewRecord();
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setStage(stage);
        row.setReviewerId(actor.id());
        row.setDecision(decision);
        row.setNotes(notes);
        reviews.save(row);
    }

    private void saveVersionSnapshot(Book book, SecurityPrincipal actor) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("title", book.getTitle());
        snap.put("subtitle", book.getSubtitle());
        snap.put("description", book.getDescription());
        snap.put("language", book.getLanguage());
        snap.put("subject", book.getSubject().getSlug());
        snap.put("publisher", book.getPublisher());
        snap.put("edition", book.getEdition());
        snap.put("publicationYear", book.getPublicationYear());
        snap.put("pageCount", book.getPageCount());
        snap.put("isbn", book.getIsbn());
        snap.put("copyrightInfo", book.getCopyrightInfo());
        snap.put("downloadAllowed", book.isDownloadAllowed());
        snap.put("files", book.getFiles().stream()
                .map(f -> Map.of("format", f.getFormat(), "mediaId", f.getMedia().getPublicId().toString()))
                .toList());
        ContentVersion v = new ContentVersion();
        v.setEntityType("book");
        v.setEntityId(book.getId());
        v.setVersion(versions.maxVersion("book", book.getId()) + 1);
        v.setChangeSummary("published snapshot");
        v.setChangedBy(actor.id());
        try {
            v.setSnapshot(mapper.writeValueAsString(snap));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("snapshot serialisation failed", e);
        }
        versions.save(v);
    }

    /** Book reference by public uuid (keeps the controller layer uuid-only). */
    public record UUIDBookRef(java.util.UUID id) {
    }

    /**
     * Controller entry point — MUST open the transaction here: it invokes
     * {@link #transition} through `this` (self-invocation bypasses the Spring
     * proxy), so without this annotation the status change would happen on a
     * detached entity and silently never persist.
     */
    @Transactional
    public TransitionResult transitionAndReport(UUIDBookRef ref, String action, String notes, SecurityPrincipal actor) {
        Book book = transition(ref, action, notes, actor);
        return new TransitionResult(book, null, book.getStatus(), actor.publicId().toString(), java.time.Instant.now());
    }
}
