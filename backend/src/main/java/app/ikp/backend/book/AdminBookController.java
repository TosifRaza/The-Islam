package app.ikp.backend.book;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.book.dto.BookAdminView;
import app.ikp.backend.book.dto.BookRequest;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin book management (M2 Module D; approved spec §3.5). Auth per matrix:
 * books metadata = content.edit (Super Admin Full, Admin Edit, Content Manager
 * Full); every write is audited. Books are created as drafts — never public
 * merely because they were saved.
 */
@RestController
@RequestMapping("/api/v1/admin/books")
@Tag(name = "admin-books")
public class AdminBookController {

    private final BookService books;

    public AdminBookController(BookService books) {
        this.books = books;
    }

    @PostMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a book (draft) — spec §3.5, Module D")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody BookRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Book book = books.create(request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(books.toAdminView(book)));
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Book management list with filters (Module D)")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String subject,
                                                    @RequestParam(required = false) String language,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String format,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<BookAdminView> result = books.adminSearch(q, subject, language, status, format, page, size);
        return ResponseEntity.ok(Pages.envelope(result, v -> v));
    }

    @GetMapping("/{bookId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Book detail — complete metadata + files")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID bookId) {
        return ResponseEntity.ok(Pages.envelope(books.adminViewById(bookId)));
    }

    @PatchMapping("/{bookId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update book metadata (draft only — spec governance)")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID bookId,
                                                      @Valid @RequestBody BookRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(books.toAdminView(books.update(bookId, request, actor))));
    }

    @PostMapping("/{bookId}/files")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Attach a scan-clean uploaded file (pdf/epub)")
    @PreAuthorize("hasAuthority('files.upload')")
    public ResponseEntity<Map<String, Object>> attachFile(@PathVariable UUID bookId,
                                                          @RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        Object media = body.get("mediaId");
        Object format = body.get("format");
        if (media == null || format == null) {
            throw ApiException.validation("mediaId and format are required");
        }
        boolean downloadAllowed = Boolean.TRUE.equals(body.get("downloadAllowed"));
        UUID mediaId;
        try {
            mediaId = UUID.fromString(String.valueOf(media));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("mediaId must be a uuid");
        }
        String fmt = String.valueOf(format);
        if (!List.of("pdf", "epub").contains(fmt)) {
            throw ApiException.validation("format must be pdf or epub");
        }
        BookAdminView view = books.attachFile(bookId, mediaId, fmt, downloadAllowed, actor);
        return ResponseEntity.status(201).body(Pages.envelope(view));
    }
}
