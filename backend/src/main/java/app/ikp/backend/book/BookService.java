package app.ikp.backend.book;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.audit.AuditService;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.book.dto.BookAdminView;
import app.ikp.backend.book.dto.BookPublicRow;
import app.ikp.backend.book.dto.BookRequest;
import app.ikp.backend.subject.Subject;
import app.ikp.backend.subject.SubjectRepository;
import app.ikp.backend.storage.MediaAsset;
import app.ikp.backend.storage.MediaAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Book management service (M2 Module D). Metadata persists in PostgreSQL;
 * files attach only from scan-clean media; publication is never automatic —
 * it happens exclusively through the authorised workflow transitions
 * ({@link app.ikp.backend.workflow.WorkflowService}) which write review
 * records, version snapshots and audit events.
 */
@Service
public class BookService {

    /** Subjects whose books always require scholarly review (content governance §3). */
    private static final List<String> SCHOLARLY_SUBJECTS =
            List.of("cat_quran_tafsir", "cat_hadith", "cat_fiqh", "cat_aqeedah");

    private final BookRepository books;
    private final AuthorRepository authors;
    private final SubjectRepository subjects;
    private final MediaAssetRepository media;
    private final app.ikp.backend.user.UserRepository users;
    private final AuditService audit;

    public BookService(BookRepository books, AuthorRepository authors, SubjectRepository subjects,
                       MediaAssetRepository media, app.ikp.backend.user.UserRepository users, AuditService audit) {
        this.books = books;
        this.authors = authors;
        this.subjects = subjects;
        this.media = media;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public Book create(BookRequest request, SecurityPrincipal actor) {
        Book book = new Book();
        apply(book, request, actor);
        book.setCreatedBy(users.getReferenceById(actor.id()));
        book.setUpdatedBy(actor.id());
        // Default scholarly-review requirement: true for governance-flagged subjects.
        if (request.scholarlyReviewRequired() == null) {
            book.setScholarlyReviewRequired(SCHOLARLY_SUBJECTS.contains(book.getSubject().getSlug()));
        }
        books.save(book);
        audit.record(actor.id(), "book.create", "book", book.getId(), null, snapshot(book), null);
        return book;
    }

    @Transactional
    public Book update(UUID publicId, BookRequest request, SecurityPrincipal actor) {
        Book book = byPublicId(publicId);
        if (book.getStatus() != Book.Status.draft) {
            // Review/published content is immutable through this path; approved
            // edits re-enter the workflow as a new draft revision (future phase).
            throw ApiException.workflow("only draft books can be edited (state: " + book.getStatus() + ")");
        }
        Map<String, Object> before = snapshot(book);
        apply(book, request, actor);
        book.setUpdatedBy(actor.id());
        audit.record(actor.id(), "book.update", "book", book.getId(), before, snapshot(book), null);
        return book;
    }

    @Transactional
    public BookAdminView attachFile(UUID publicId, UUID mediaId, String format, boolean downloadAllowed, SecurityPrincipal actor) {
        Book book = byPublicId(publicId);
        if (book.getStatus() == Book.Status.archived) {
            throw ApiException.workflow("archived books cannot receive files");
        }
        MediaAsset asset = media.findByPublicId(mediaId)
                .orElseThrow(() -> ApiException.notFound("media not found"));
        // fail closed: only scan-clean media may attach; publishing also re-checks
        if (!asset.isClean()) {
            throw ApiException.of("UPLOAD-002", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "media is not scan-clean; attach refused");
        }
        String expectedFormat = "book_pdf".equals(asset.getPurpose()) ? "pdf"
                : "book_epub".equals(asset.getPurpose()) ? "epub" : null;
        if (expectedFormat == null || !expectedFormat.equals(format)) {
            throw ApiException.validation("media purpose does not match the requested file format");
        }
        BookFile file = new BookFile();
        file.setBook(book);
        file.setMedia(asset);
        file.setFormat(format);
        file.setDownloadAllowed(downloadAllowed);
        book.getFiles().add(file);
        audit.record(actor.id(), "book.file_attached", "book", book.getId(), null,
                Map.of("mediaId", mediaId.toString(), "format", format), null);
        // mapped INSIDE the transaction: the freshly loaded book's subject/files
        // are lazy proxies that cannot be touched after the session closes
        return toAdminView(book);
    }

    /** Admin list mapping inside a read-only transaction (lazy subject proxies). */
    @Transactional(readOnly = true)
    public Page<BookAdminView> adminSearch(String q, String subject, String language, String status,
                                           String format, int page, int size) {
        Book.Status st = nullIfBlank(status) == null ? null : Book.Status.valueOf(nullIfBlank(status));
        return books.findAll(BookRepository.filter(nullIfBlank(q), nullIfBlank(subject),
                        nullIfBlank(language), st, nullIfBlank(format), null),
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(this::toAdminView);
    }

    /** Admin detail mapping inside a read-only transaction. */
    @Transactional(readOnly = true)
    public BookAdminView adminViewById(UUID publicId) {
        return toAdminView(byPublicId(publicId));
    }

    @Transactional(readOnly = true)
    public Page<Book> search(String q, String subject, String language, String status,
                             String format, int page, int size) {
        Book.Status st = nullIfBlank(status) == null ? null : Book.Status.valueOf(nullIfBlank(status));
        return books.findAll(BookRepository.filter(nullIfBlank(q), nullIfBlank(subject),
                        nullIfBlank(language), st, nullIfBlank(format), null),
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @Transactional(readOnly = true)
    public Book byPublicId(UUID publicId) {
        return books.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("book not found"));
    }

    // ----- mapping -----

    public BookAdminView toAdminView(Book b) {
        return new BookAdminView(
                b.getPublicId(), b.getTitle(), b.getSubtitle(), b.getDescription(), b.getLanguage(),
                new BookAdminView.SubjectRef(b.getSubject().getSlug(), b.getSubject().getName()),
                b.getAuthors().stream()
                        .map(ba -> new BookAdminView.AuthorRef(ba.getAuthor().getPublicId(), ba.getAuthor().getName()))
                        .toList(),
                b.getPublisher(), b.getEdition(), b.getPublicationYear(), b.getPageCount(), b.getIsbn(),
                b.getCopyrightInfo(),
                b.getLicenceId() == null ? null : new BookAdminView.LicenceRef(b.getLicenceId(), null, null, false),
                b.isDownloadAllowed(), b.isScholarlyReviewRequired(), b.getStatus().name(),
                b.getCover() == null ? null : new BookAdminView.FileRef(b.getCover().getPublicId(), "cover",
                        b.getCover().getMime(), b.getCover().getSizeBytes(), false, b.getCover().getScanStatus()),
                b.getFiles().stream()
                        .map(f -> new BookAdminView.FileRef(f.getMedia().getPublicId(), f.getFormat(),
                                f.getMedia().getMime(), f.getMedia().getSizeBytes(), f.isDownloadAllowed(),
                                f.getMedia().getScanStatus()))
                        .toList(),
                b.getCreatedAt(), b.getUpdatedAt(), b.getPublishedAt());
    }

    public BookPublicRow toPublicRow(Book b) {
        return new BookPublicRow(b.getPublicId(), b.getTitle(), b.getSubtitle(), b.getLanguage(),
                new BookPublicRow.SubjectRef(b.getSubject().getSlug(), b.getSubject().getName()),
                b.getAuthors().stream()
                        .map(ba -> new BookPublicRow.AuthorRef(ba.getAuthor().getPublicId(), ba.getAuthor().getName()))
                        .toList(),
                b.getFiles().stream().map(BookFile::getFormat).distinct().toList(),
                b.getFiles().stream()
                        .map(f -> new BookPublicRow.FileRef(f.getMedia().getPublicId(), f.getFormat(),
                                f.isDownloadAllowed() && f.getMedia().isClean(), f.isOcrAvailable()))
                        .toList(),
                b.isDownloadAllowed(), b.getPublishedAt());
    }

    // ----- internals -----

    private void apply(Book book, BookRequest request, SecurityPrincipal actor) {
        book.setTitle(request.title());
        book.setSubtitle(request.subtitle());
        book.setDescription(request.description());
        book.setLanguage(request.language());
        Subject subject = subjects.findBySlug(request.subjectId())
                .orElseThrow(() -> ApiException.validation("unknown subject"));
        book.setSubject(subject);
        book.setPublisher(request.publisher());
        book.setEdition(request.edition());
        book.setPublicationYear(request.publicationYear());
        book.setPageCount(request.pageCount());
        book.setIsbn(request.isbn());
        book.setCopyrightInfo(request.copyrightInfo());
        book.setLicenceId(request.licenceId());
        if (request.downloadAllowed() != null) {
            book.setDownloadAllowed(request.downloadAllowed());
        }
        if (request.scholarlyReviewRequired() != null) {
            book.setScholarlyReviewRequired(request.scholarlyReviewRequired());
        }
        if (request.coverMediaId() != null) {
            MediaAsset cover = media.findByPublicId(request.coverMediaId())
                    .orElseThrow(() -> ApiException.validation("unknown cover media"));
            if (!cover.isClean()) {
                throw ApiException.of("UPLOAD-002", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "cover media is not scan-clean");
            }
            book.setCover(cover);
        }
        book.getAuthors().clear();
        if (request.authorIds() != null) {
            int order = 1;
            for (UUID authorId : request.authorIds()) {
                Author author = authors.findByPublicId(authorId)
                        .orElseThrow(() -> ApiException.validation("unknown author " + authorId));
                BookAuthor ba = new BookAuthor();
                ba.setBook(book);
                ba.setAuthor(author);
                ba.setSortOrder(order++);
                book.getAuthors().add(ba);
            }
        }
    }

    private app.ikp.backend.user.User userRef(SecurityPrincipal actor) {
        return users.getReferenceById(actor.id());
    }

    static Map<String, Object> snapshot(Book b) {
        return Map.ofEntries(
                Map.entry("title", b.getTitle()),
                Map.entry("language", b.getLanguage()),
                Map.entry("subject", b.getSubject().getSlug()),
                Map.entry("status", b.getStatus().name()),
                Map.entry("downloadAllowed", b.isDownloadAllowed()),
                Map.entry("scholarlyReviewRequired", b.isScholarlyReviewRequired()),
                Map.entry("updatedAt", String.valueOf(b.getUpdatedAt())));
    }

    private static String nullIfBlank(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
