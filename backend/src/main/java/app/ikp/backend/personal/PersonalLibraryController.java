package app.ikp.backend.personal;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import app.ikp.backend.storage.DownloadTokenService;
import app.ikp.backend.storage.MediaAsset;
import app.ikp.backend.storage.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Personal Knowledge Library API (M4 §10). STRICT PRIVACY (PRS-05):
 * every query is scoped by user_id = principal; ownership re-verified on every
 * single-item access; no admin endpoint exists for personal_books; uploads go
 * to the private personal_* media buckets — never public.
 */
@RestController
@RequestMapping("/api/v1/personal")
@Tag(name = "personal-library")
@PreAuthorize("isAuthenticated()")
public class PersonalLibraryController {

    private final JdbcTemplate jdbc;
    private final MediaService mediaService;
    private final DownloadTokenService downloadTokens;

    public PersonalLibraryController(JdbcTemplate jdbc, MediaService mediaService,
                                     DownloadTokenService downloadTokens) {
        this.jdbc = jdbc;
        this.mediaService = mediaService;
        this.downloadTokens = downloadTokens;
    }

    @PostMapping("/books")
    @Operation(summary = "Upload a personal PDF/EPUB (multipart)")
    @Transactional
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) MultipartFile cover,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        if (title == null || title.isBlank()) throw ApiException.validation("title is required");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String format = name.endsWith(".pdf") ? "pdf" : name.endsWith(".epub") ? "epub" : null;
        if (format == null) throw ApiException.validation("only .pdf and .epub files are supported");
        String purpose = "pdf".equals(format) ? "personal_pdf" : "personal_epub";
        long mediaId;
        long mediaSize;
        try {
            MediaAsset asset = mediaService.upload(file, purpose, null, principal);
            mediaId = asset.getId();
            mediaSize = asset.getSizeBytes() == null ? 0L : asset.getSizeBytes();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // media_assets.sha256 is UNIQUE: two users may legitimately upload the
            // SAME personal file — reuse the existing media row (identical bytes).
            String sha = sha256(file);
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id, size_bytes FROM media_assets WHERE sha256 = ? LIMIT 1", sha);
            if (existing.isEmpty()) {
                throw e;
            }
            mediaId = ((Number) existing.get(0).get("id")).longValue();
            Number sz = (Number) existing.get(0).get("size_bytes");
            mediaSize = sz == null ? 0L : sz.longValue();
        }
        MediaAsset coverAsset = null;
        if (cover != null && !cover.isEmpty()) {
            coverAsset = mediaService.upload(cover, "personal_cover", null, principal);
        }
        jdbc.update("""
                INSERT INTO personal_books (user_id, title, author, description, language, format,
                    media_id, cover_media_id, file_size_bytes)
                VALUES (?, ?, ?, ?, coalesce(?, 'en'), ?, ?, ?, ?)
                """, principal.id(), title.trim(), author, description, language, format,
                mediaId, coverAsset == null ? null : coverAsset.getId(), mediaSize);
        UUID publicId = jdbc.queryForObject(
                "SELECT public_id FROM personal_books WHERE media_id = ? AND user_id = ? "
                        + "ORDER BY id DESC LIMIT 1", UUID.class, mediaId, principal.id());
        return ResponseEntity.ok(Pages.envelope(detailMap(publicId, principal)));
    }

    @GetMapping("/books")
    @Operation(summary = "My personal books (owner-scoped)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String collection,
                                                    @RequestParam(defaultValue = "20") int limit,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @AuthenticationPrincipal SecurityPrincipal principal) {
        int capped = Math.min(Math.max(1, limit), 100);
        int offset = (Math.max(1, page) - 1) * capped;
        String like = "%" + (q == null ? "" : q) + "%";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.public_id AS "bookId", b.title, b.author, b.format, b.language,
                       b.file_size_bytes AS "fileSizeBytes", b.text_searchable AS "textSearchable",
                       b.created_at::text AS "createdAt"
                FROM personal_books b
                WHERE b.user_id = ? AND b.deleted_at IS NULL
                  AND (b.title ILIKE ? OR coalesce(b.author,'') ILIKE ?)
                  AND (?::uuid IS NULL OR EXISTS (SELECT 1 FROM personal_book_items i
                       JOIN personal_collections c ON c.id = i.collection_id
                       WHERE i.book_id = b.id AND c.public_id = ?::uuid AND c.user_id = b.user_id))
                ORDER BY b.created_at DESC LIMIT ? OFFSET ?
                """, principal.id(), like, like, collection, collection, capped, offset);
        long total = jdbc.queryForObject("""
                SELECT count(*) FROM personal_books b
                WHERE b.user_id = ? AND b.deleted_at IS NULL
                  AND (b.title ILIKE ? OR coalesce(b.author,'') ILIKE ?)
                """, Long.class, principal.id(), like, like);
        return ResponseEntity.ok(Pages.envelope(rows,
                new Pages.PageMeta(Math.max(1, page), capped, total, (int) Math.ceil(total / (double) capped))));
    }

    @GetMapping("/books/{bookId}")
    @Operation(summary = "Personal book detail (ownership enforced)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID bookId,
                                                      @AuthenticationPrincipal SecurityPrincipal principal) {
        return ResponseEntity.ok(Pages.envelope(detailMap(bookId, principal)));
    }

    @PatchMapping("/books/{bookId}")
    @Operation(summary = "Edit personal book metadata")
    @Transactional
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID bookId,
                                                      @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal SecurityPrincipal principal) {
        long id = ownedId(bookId, principal);
        if (body.containsKey("title")) {
            String t = String.valueOf(body.get("title"));
            if (t.isBlank()) throw ApiException.validation("title cannot be empty");
            jdbc.update("UPDATE personal_books SET title = ?, updated_at = now() WHERE id = ?", t.trim(), id);
        }
        if (body.containsKey("author")) {
            jdbc.update("UPDATE personal_books SET author = ?, updated_at = now() WHERE id = ?",
                    body.get("author") == null ? null : String.valueOf(body.get("author")), id);
        }
        if (body.containsKey("description")) {
            jdbc.update("UPDATE personal_books SET description = ?, updated_at = now() WHERE id = ?",
                    body.get("description") == null ? null : String.valueOf(body.get("description")), id);
        }
        return ResponseEntity.ok(Pages.envelope(detailMap(bookId, principal)));
    }

    @DeleteMapping("/books/{bookId}")
    @Operation(summary = "Delete a personal book (soft delete)")
    @Transactional
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID bookId,
                                                      @AuthenticationPrincipal SecurityPrincipal principal) {
        long id = ownedId(bookId, principal);
        jdbc.update("UPDATE personal_books SET deleted_at = now() WHERE id = ?", id);
        return ResponseEntity.ok(Pages.envelope(Map.of("deleted", true)));
    }

    @PostMapping("/books/{bookId}/download-token")
    @Operation(summary = "Signed URL for my own file (ownership enforced)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> downloadToken(@PathVariable UUID bookId,
                                                             HttpServletRequest http,
                                                             @AuthenticationPrincipal SecurityPrincipal principal) {
        long id = ownedId(bookId, principal);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT b.media_id, b.text_searchable AS "textSearchable", b.format,
                       m.public_id AS "mediaPublicId", m.size_bytes AS "mediaSize", m.scan_status AS "scanStatus"
                FROM personal_books b JOIN media_assets m ON m.id = b.media_id
                WHERE b.id = ?
                """, id);
        if (!"clean".equals(row.get("scanStatus"))) {
            throw ApiException.of("UPLOAD-002", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "file is not scan-clean yet");
        }
        String base = http.getScheme() + "://" + http.getServerName()
                + (http.getServerPort() == 80 || http.getServerPort() == 8080 ? "" : ":" + http.getServerPort());
        DownloadTokenService.SignedUrl url = downloadTokens.sign(base,
                (java.util.UUID) row.get("mediaPublicId"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url.url());
        data.put("expiresAt", String.valueOf(url.expiresAt()));
        data.put("sizeBytes", ((Number) row.get("mediaSize")).longValue());
        data.put("format", row.get("format"));
        data.put("textSearchable", row.get("textSearchable"));
        return ResponseEntity.ok(Pages.envelope(data));
    }

    // ---------------------------------------------------------- collections

    @GetMapping("/collections")
    @Operation(summary = "My personal collections")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> collections(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT c.id, c.public_id AS "collectionId", c.name, c.created_at::text AS "createdAt",
                       (SELECT count(*) FROM personal_book_items i WHERE i.collection_id = c.id) AS "bookCount"
                FROM personal_collections c WHERE c.user_id = ? ORDER BY c.name
                """, principal.id())));
    }

    @PostMapping("/collections")
    @Operation(summary = "Create a collection")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCollection(@RequestBody Map<String, Object> body,
                                                                @AuthenticationPrincipal SecurityPrincipal principal) {
        String name = String.valueOf(body.get("name"));
        if (name.isBlank()) throw ApiException.validation("name is required");
        int inserted = jdbc.update("INSERT INTO personal_collections (user_id, name) SELECT ?, ? WHERE NOT EXISTS "
                + "(SELECT 1 FROM personal_collections WHERE user_id = ? AND name = ?)",
                principal.id(), name.trim(), principal.id(), name.trim());
        if (inserted == 0) throw ApiException.workflow("collection already exists");
        UUID id = jdbc.queryForObject(
                "SELECT public_id FROM personal_collections WHERE user_id = ? AND name = ?",
                UUID.class, principal.id(), name.trim());
        return ResponseEntity.ok(Pages.envelope(Map.of("collectionId", String.valueOf(id), "name", name.trim())));
    }

    @PutMapping("/collections/{collectionId}/books/{bookId}")
    @Operation(summary = "Add my book to my collection (both ownership-checked)")
    @Transactional
    public ResponseEntity<Map<String, Object>> addToCollection(@PathVariable UUID collectionId,
                                                               @PathVariable UUID bookId,
                                                               @AuthenticationPrincipal SecurityPrincipal principal) {
        Long cid = jdbc.queryForObject(
                "SELECT id FROM personal_collections WHERE public_id = ? AND user_id = ?",
                Long.class, collectionId, principal.id());
        if (cid == null) throw ApiException.notFound("collection not found");
        long bid = ownedId(bookId, principal);
        jdbc.update("INSERT INTO personal_book_items (collection_id, book_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                cid, bid);
        return ResponseEntity.ok(Pages.envelope(Map.of("added", true)));
    }

    @DeleteMapping("/collections/{collectionId}/books/{bookId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeFromCollection(@PathVariable UUID collectionId,
                                                                    @PathVariable UUID bookId,
                                                                    @AuthenticationPrincipal SecurityPrincipal principal) {
        Long cid = jdbc.queryForObject(
                "SELECT id FROM personal_collections WHERE public_id = ? AND user_id = ?",
                Long.class, collectionId, principal.id());
        if (cid == null) throw ApiException.notFound("collection not found");
        long bid = ownedId(bookId, principal);
        jdbc.update("DELETE FROM personal_book_items WHERE collection_id = ? AND book_id = ?", cid, bid);
        return ResponseEntity.ok(Pages.envelope(Map.of("removed", true)));
    }

    // ------------------------------------------------------------- internals

    private static String sha256(MultipartFile f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (var in = f.getInputStream()) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }

    /** Ownership re-verified here on EVERY access — the privacy invariant. */
    private long ownedId(UUID bookId, SecurityPrincipal principal) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM personal_books WHERE public_id = ? AND user_id = ? AND deleted_at IS NULL",
                Long.class, bookId, principal.id());
        if (ids.isEmpty()) throw ApiException.notFound("personal book not found");
        return ids.get(0);
    }

    private Map<String, Object> detailMap(UUID bookId, SecurityPrincipal principal) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.public_id AS "bookId", b.title, b.author, b.description, b.format, b.language,
                       b.file_size_bytes AS "fileSizeBytes", b.text_searchable AS "textSearchable",
                       b.created_at::text AS "createdAt", b.updated_at::text AS "updatedAt",
                       m.public_id AS "mediaId", c.public_id AS "coverMediaId"
                FROM personal_books b
                JOIN media_assets m ON m.id = b.media_id
                LEFT JOIN media_assets c ON c.id = b.cover_media_id
                WHERE b.public_id = ? AND b.user_id = ? AND b.deleted_at IS NULL
                """, bookId, principal.id());
        if (rows.isEmpty()) throw ApiException.notFound("personal book not found");
        return rows.get(0);
    }
}
