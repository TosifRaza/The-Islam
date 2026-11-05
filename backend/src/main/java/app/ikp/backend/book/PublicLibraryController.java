package app.ikp.backend.book;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import app.ikp.backend.book.dto.BookPublicRow;
import app.ikp.backend.storage.DownloadTokenService;
import app.ikp.backend.storage.MediaAsset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public library endpoints (approved spec §3.5.1/3.5.2/3.5.4). Published books
 * only; private user uploads never appear here (different storage domain and
 * different table — enforced server-side). Download tokens respect the
 * licence-driven download_allowed flag (LIC-001).
 */
@RestController
@RequestMapping("/api/v1/library")
@Tag(name = "library")
public class PublicLibraryController {

    private final BookService books;
    private final DownloadTokenService downloadTokens;

    public PublicLibraryController(BookService books, DownloadTokenService downloadTokens) {
        this.books = books;
        this.downloadTokens = downloadTokens;
    }

    @GetMapping("/books")
    @Operation(summary = "Browse published platform books (spec §3.5.1)")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> browse(@RequestParam(required = false) String subject,
                                                      @RequestParam(required = false) String language,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String format,
                                                      @RequestParam(defaultValue = "20") int limit,
                                                      @RequestParam(required = false) String cursor) {
        int page = 1;
        if (cursor != null && !cursor.isBlank()) {
            try {
                page = Integer.parseInt(new String(java.util.Base64.getUrlDecoder().decode(cursor)));
                if (page < 1) page = 1;
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("invalid cursor");
            }
        }
        int capped = Math.min(Math.max(1, limit), 100);
        Page<Book> result = books.search(q, subject, language, Book.Status.published.name(), format, page, capped);
        String next = result.hasNext() ? java.util.Base64.getUrlEncoder()
                .withoutPadding().encodeToString(String.valueOf(page + 1).getBytes()) : null;
        List<BookPublicRow> rows = result.getContent().stream().map(books::toPublicRow).toList();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("limit", capped);
        meta.put("nextCursor", next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", rows);
        body.put("meta", meta);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/books/{bookId}")
    @Operation(summary = "Published book detail (spec §3.5.2; unpublished → 404)")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID bookId) {
        Book b = books.byPublicId(bookId);
        if (b.getStatus() != Book.Status.published) {
            throw ApiException.notFound("book not found");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        BookPublicRow row = books.toPublicRow(b);
        data.put("bookId", row.bookId());
        data.put("title", row.title());
        data.put("subtitle", row.subtitle());
        data.put("description", b.getDescription());
        data.put("language", row.language());
        data.put("subject", row.subject());
        data.put("authors", row.authors());
        data.put("publisher", b.getPublisher());
        data.put("edition", b.getEdition());
        data.put("publicationYear", b.getPublicationYear());
        data.put("pageCount", b.getPageCount());
        data.put("copyrightInfo", b.getCopyrightInfo());
        data.put("downloadAllowed", row.downloadAllowed());
        data.put("formats", row.formats());
        // M4: per-format file refs so an authenticated reader can fetch a token
        List<Map<String, Object>> files = new ArrayList<>();
        for (BookPublicRow.FileRef f : row.files()) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("fileId", String.valueOf(f.fileId()));
            fm.put("format", f.format());
            fm.put("downloadAllowed", f.downloadAllowed());
            fm.put("ocrAvailable", f.ocrAvailable());
            files.add(fm);
        }
        data.put("files", files);
        data.put("publishedAt", String.valueOf(row.publishedAt()));
        return ResponseEntity.ok(Pages.envelope(data));
    }

    @PostMapping("/books/{bookId}/download-token")
    @Operation(summary = "Authorised download → short-lived signed URL (spec §3.5.4)")
    @PreAuthorize("isAuthenticated()")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> downloadToken(@PathVariable UUID bookId,
                                                             @RequestBody Map<String, Object> body,
                                                             HttpServletRequest http,
                                                             @AuthenticationPrincipal SecurityPrincipal principal) {
        Book b = books.byPublicId(bookId);
        if (b.getStatus() != Book.Status.published) {
            throw ApiException.notFound("book not found");
        }
        if (!b.isDownloadAllowed()) {
            // licence-driven restriction
            throw ApiException.of("LIC-001", org.springframework.http.HttpStatus.FORBIDDEN,
                    "downloads are not permitted for this content");
        }
        UUID mediaId = parseUuid(body);
        BookFile file = b.getFiles().stream()
                .filter(f -> f.getMedia().getPublicId().equals(mediaId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("file not found for this book"));
        MediaAsset asset = file.getMedia();
        if (!asset.isClean()) {
            throw ApiException.of("UPLOAD-002", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "media is not scan-clean");
        }
        String base = http.getScheme() + "://" + http.getServerName()
                + (http.getServerPort() == 80 || http.getServerPort() == 8080 ? "" : ":" + http.getServerPort());
        DownloadTokenService.SignedUrl url = downloadTokens.sign(base, asset.getPublicId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url.url());
        data.put("expiresAt", String.valueOf(url.expiresAt()));
        data.put("sizeBytes", asset.getSizeBytes());
        data.put("sha256", asset.getSha256());
        return ResponseEntity.ok(Pages.envelope(data));
    }

    private static UUID parseUuid(Map<String, Object> body) {
        Object v = body.get("fileId");
        if (v == null) throw ApiException.validation("fileId is required");
        try {
            return UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("fileId must be a uuid");
        }
    }
}
