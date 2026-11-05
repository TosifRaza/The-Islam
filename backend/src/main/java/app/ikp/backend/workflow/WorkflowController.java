package app.ikp.backend.workflow;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.book.Book;
import app.ikp.backend.book.BookRepository;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import app.ikp.backend.fatwa.Fatwa;
import app.ikp.backend.fatwa.FatwaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central review queue + unified publication workflow family (approved spec
 * §3.19.5). Books use this family; Fatwas use the dedicated stricter family
 * (§3.17) — {entity:"fatwas"} here is refused with NF-001 so no weaker path to
 * Fatwa publication can exist.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "admin-workflow")
public class WorkflowController {

    private final WorkflowService workflow;
    private final BookRepository books;
    private final FatwaRepository fatwas;
    private final ContentReviewRecordRepository reviewRecords;
    private final app.ikp.backend.book.BookService bookService;

    public WorkflowController(WorkflowService workflow, BookRepository books, FatwaRepository fatwas,
                              ContentReviewRecordRepository reviewRecords,
                              app.ikp.backend.book.BookService bookService) {
        this.workflow = workflow;
        this.books = books;
        this.fatwas = fatwas;
        this.reviewRecords = reviewRecords;
        this.bookService = bookService;
    }

    /** Unified family — books only (fatwas → NF-001 by design, spec §3.19.5 note). */
    @PostMapping("/{entity}/{entityId}/{action}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Workflow transition: submit-review | approve-editorial | approve | reject | publish | unpublish | archive")
    public ResponseEntity<Map<String, Object>> transition(@PathVariable String entity,
                                                          @PathVariable UUID entityId,
                                                          @PathVariable String action,
                                                          @RequestBody(required = false) Map<String, Object> body,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        if ("fatwas".equals(entity)) {
            // No weaker Fatwa path exists — the dedicated family is the only route.
            throw ApiException.notFound("unknown workflow entity: " + entity);
        }
        if (!"books".equals(entity)) {
            throw ApiException.notFound("unknown workflow entity: " + entity);
        }
        String notes = body == null || body.get("notes") == null ? null : String.valueOf(body.get("notes"));
        WorkflowService.TransitionResult result =
                workflow.transitionAndReport(new WorkflowService.UUIDBookRef(entityId), action, notes, actor);
        return ResponseEntity.ok(Pages.envelope(Map.of(
                "entity", "books",
                "entityId", entityId,
                "state", result.book().getStatus().name(),
                "changedBy", result.actorPublicId(),
                "changedAt", String.valueOf(result.changedAt()))));
    }

    /** Review queue across content awaiting decisions (spec §3.19.1 awaitingApproval + queue). */
    @GetMapping("/review-queue")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Content awaiting review / scholarly review / publication; rejected & archived views")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER','SCHOLAR_REVIEWER')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> queue(@RequestParam(defaultValue = "all") String state,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Map<String, Object>> items;
        switch (state) {
            case "editorial" -> items = books.findByStatus(Book.Status.in_editorial_review).stream()
                    .map(this::queueRow).toList();
            case "scholarly" -> items = books.findByStatus(Book.Status.in_scholarly_review).stream()
                    .map(this::queueRow).toList();
            case "ready_to_publish" -> items = books.findByStatus(Book.Status.approved).stream()
                    .map(this::queueRow).toList();
            case "rejected" -> {
                List<Map<String, Object>> rejected = new java.util.ArrayList<>();
                for (ContentReviewRecord r : reviewRecords.findByEntityTypeAndDecisionOrderByDecidedAtDesc(
                        "book", ContentReviewRecord.Decision.rejected)) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("entity", "books");
                    row.put("note", String.valueOf(r.getNotes()));
                    row.put("decidedAt", String.valueOf(r.getDecidedAt()));
                    row.put("stage", r.getStage().name());
                    rejected.add(row);
                }
                items = rejected;
            }
            case "archived" -> items = books.findByStatus(Book.Status.archived).stream()
                    .map(this::queueRow).toList();
            case "fatwas" -> items = fatwas.findByState(Fatwa.State.in_review).stream()
                    .map(f -> Map.<String, Object>of(
                            "entity", "fatwas", "entityId", f.getPublicId().toString(),
                            "title", truncate(f.getQuestion(), 80), "state", f.getState().name()))
                    .toList();
            default -> items = java.util.stream.Stream.concat(
                            java.util.stream.Stream.concat(
                                    books.findByStatus(Book.Status.in_editorial_review).stream(),
                                    books.findByStatus(Book.Status.in_scholarly_review).stream())
                            .map(this::queueRow),
                            fatwas.findByState(Fatwa.State.in_review).stream()
                                    .map(f -> Map.<String, Object>of(
                                            "entity", "fatwas", "entityId", f.getPublicId().toString(),
                                            "title", truncate(f.getQuestion(), 80), "state", f.getState().name())))
                    .toList();
        }
        return ResponseEntity.ok(Pages.envelope(items,
                new Pages.PageMeta(1, items.size(), items.size(), 1)));
    }

    /** Review history of a book (reviewer identity + decisions, governance §3). */
    @GetMapping("/books/{bookId}/reviews")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Review records of a book")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> bookReviews(@PathVariable UUID bookId) {
        Book book = books.findByPublicId(bookId).orElseThrow(() -> ApiException.notFound("book not found"));
        return ResponseEntity.ok(Pages.envelope(
                reviewRecords.findByEntityTypeAndEntityIdOrderByDecidedAtDesc("book", book.getId()).stream()
                        .map(r -> Map.<String, Object>of(
                                "stage", r.getStage().name(), "decision", r.getDecision().name(),
                                "notes", String.valueOf(r.getNotes()), "decidedAt", String.valueOf(r.getDecidedAt())))
                        .toList()));
    }

    private Map<String, Object> queueRow(Book b) {
        long waitingDays = b.getUpdatedAt() == null ? 0
                : java.time.Duration.between(b.getUpdatedAt().truncatedTo(ChronoUnit.DAYS),
                        java.time.Instant.now().truncatedTo(ChronoUnit.DAYS)).toDays();
        return Map.of(
                "entity", "books",
                "entityId", b.getPublicId().toString(),
                "title", truncate(b.getTitle(), 120),
                "state", b.getStatus().name(),
                "subject", b.getSubject().getSlug(),
                "waitingDays", waitingDays);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
