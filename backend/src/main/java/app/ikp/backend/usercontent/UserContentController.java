package app.ikp.backend.usercontent;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cross-module user content (M4: bookmarks, notes, favourites, reading
 * progress, history — PRF-03; M5 adds research/course/lesson/fatwa/scholar
 * ref kinds so saving research, bookmarking lessons and fatwas reuse this
 * exact ownership-scoped pipeline). Every row is scoped by the authenticated
 * principal. ref_key scheme: ayah:{s}:{a} · hadith:{id} · book:{uuid} ·
 * dua:{id} · dict:{id} · research:{uuid} · course:{uuid} · lesson:{uuid} ·
 * fatwa:{uuid} · scholar:{uuid}. Android Room queues offline writes here via
 * WorkManager (full two-way sync lands in M7 — documented).
 */
@RestController
@RequestMapping("/api/v1/user-content")
@Tag(name = "user-content")
@PreAuthorize("isAuthenticated()")
public class UserContentController {

    private static final Pattern REF = Pattern.compile(
            "^(ayah:\\d{1,3}:\\d{1,3}|hadith:\\d+|book:[0-9a-fA-F-]{36}|dua:\\d+|dict:\\d+"
                    + "|research:[0-9a-fA-F-]{36}|course:[0-9a-fA-F-]{36}|lesson:[0-9a-fA-F-]{36}"
                    + "|fatwa:[0-9a-fA-F-]{36}|scholar:[0-9a-fA-F-]{36})$");

    private final JdbcTemplate jdbc;

    public UserContentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/bookmarks")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> bookmarks(@AuthenticationPrincipal SecurityPrincipal p) {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT ref_key AS "refKey", note, created_at::text AS "createdAt"
                FROM user_bookmarks WHERE user_id = ? ORDER BY created_at DESC LIMIT 500
                """, p.id())));
    }

    @PutMapping("/bookmarks")
    @Transactional
    public ResponseEntity<Map<String, Object>> addBookmark(@RequestBody Map<String, Object> body,
                                                           @AuthenticationPrincipal SecurityPrincipal p) {
        String ref = refKey(body);
        jdbc.update("INSERT INTO user_bookmarks (user_id, ref_key, note) VALUES (?, ?, ?) "
                + "ON CONFLICT (user_id, ref_key) DO UPDATE SET note = EXCLUDED.note",
                p.id(), ref, body.get("note") == null ? null : String.valueOf(body.get("note")));
        return ResponseEntity.ok(Pages.envelope(Map.of("refKey", ref)));
    }

    @DeleteMapping("/bookmarks")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeBookmark(@RequestParam String refKey,
                                                              @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("DELETE FROM user_bookmarks WHERE user_id = ? AND ref_key = ?", p.id(), validRef(refKey));
        return ResponseEntity.ok(Pages.envelope(Map.of("removed", true)));
    }

    @GetMapping("/notes")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> notes(@AuthenticationPrincipal SecurityPrincipal p) {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT ref_key AS "refKey", body, created_at::text AS "createdAt", updated_at::text AS "updatedAt"
                FROM user_notes WHERE user_id = ? ORDER BY updated_at DESC LIMIT 500
                """, p.id())));
    }

    @PutMapping("/notes")
    @Transactional
    public ResponseEntity<Map<String, Object>> putNote(@RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal SecurityPrincipal p) {
        String ref = refKey(body);
        String text = String.valueOf(body.get("body"));
        if (text.isBlank()) throw ApiException.validation("body is required");
        jdbc.update("INSERT INTO user_notes (user_id, ref_key, body) VALUES (?, ?, ?) "
                + "ON CONFLICT (user_id, ref_key) DO UPDATE SET body = EXCLUDED.body, updated_at = now()",
                p.id(), ref, text);
        return ResponseEntity.ok(Pages.envelope(Map.of("refKey", ref)));
    }

    @DeleteMapping("/notes")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeNote(@RequestParam String refKey,
                                                          @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("DELETE FROM user_notes WHERE user_id = ? AND ref_key = ?", p.id(), validRef(refKey));
        return ResponseEntity.ok(Pages.envelope(Map.of("removed", true)));
    }

    @GetMapping("/favourites")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> favourites(@AuthenticationPrincipal SecurityPrincipal p) {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT ref_key AS "refKey", created_at::text AS "createdAt"
                FROM user_favourites WHERE user_id = ? ORDER BY created_at DESC LIMIT 500
                """, p.id())));
    }

    @PutMapping("/favourites")
    @Transactional
    public ResponseEntity<Map<String, Object>> addFavourite(@RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal SecurityPrincipal p) {
        String ref = refKey(body);
        jdbc.update("INSERT INTO user_favourites (user_id, ref_key) VALUES (?, ?) ON CONFLICT DO NOTHING",
                p.id(), ref);
        return ResponseEntity.ok(Pages.envelope(Map.of("refKey", ref)));
    }

    @DeleteMapping("/favourites")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeFavourite(@RequestParam String refKey,
                                                               @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("DELETE FROM user_favourites WHERE user_id = ? AND ref_key = ?", p.id(), validRef(refKey));
        return ResponseEntity.ok(Pages.envelope(Map.of("removed", true)));
    }

    @GetMapping("/progress")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> progress(@AuthenticationPrincipal SecurityPrincipal p) {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT ref_key AS "refKey", percent, position_data AS "positionData",
                       updated_at::text AS "updatedAt"
                FROM reading_progress WHERE user_id = ? ORDER BY updated_at DESC LIMIT 200
                """, p.id())));
    }

    @PutMapping("/progress")
    @Transactional
    public ResponseEntity<Map<String, Object>> putProgress(@RequestBody Map<String, Object> body,
                                                           @AuthenticationPrincipal SecurityPrincipal p) {
        String ref = refKey(body);
        Integer percent = body.get("percent") == null ? null
                : Math.max(0, Math.min(100, ((Number) body.get("percent")).intValue()));
        String position = body.get("positionData") == null ? "{}" : String.valueOf(body.get("positionData"));
        jdbc.update("INSERT INTO reading_progress (user_id, ref_key, percent, position_data) VALUES (?, ?, ?, ?::jsonb) "
                + "ON CONFLICT (user_id, ref_key) DO UPDATE SET percent = EXCLUDED.percent, "
                + "position_data = EXCLUDED.position_data, updated_at = now()", p.id(), ref, percent, position);
        return ResponseEntity.ok(Pages.envelope(Map.of("refKey", ref)));
    }

    @PostMapping("/history")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordHistory(@RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal SecurityPrincipal p) {
        String ref = refKey(body);
        jdbc.update("INSERT INTO reading_history (user_id, ref_key, title) VALUES (?, ?, ?)",
                p.id(), ref, body.get("title") == null ? null : String.valueOf(body.get("title")));
        return ResponseEntity.ok(Pages.envelope(Map.of("recorded", true)));
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> history(@AuthenticationPrincipal SecurityPrincipal p) {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT ref_key AS "refKey", title, accessed_at::text AS "accessedAt"
                FROM reading_history WHERE user_id = ? ORDER BY accessed_at DESC LIMIT 100
                """, p.id())));
    }

    private static String refKey(Map<String, Object> body) {
        Object v = body.get("refKey");
        if (v == null) throw ApiException.validation("refKey is required");
        return validRef(String.valueOf(v));
    }

    private static String validRef(String ref) {
        if (!REF.matcher(ref).matches()) throw ApiException.validation("unsupported refKey format");
        return ref;
    }
}
