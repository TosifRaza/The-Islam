package app.ikp.backend.dua;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Duas & Adhkar API (M4 §14). Categories and records are admin-managed; only
 * published rows are served; fixture rows (SR-16 rights unresolved) are
 * labelled and gated by app.content.allow-fixtures.
 */
@RestController
@RequestMapping("/api/v1/duas")
@Tag(name = "duas")
public class DuaController {

    private final JdbcTemplate jdbc;
    private final boolean allowFixtures;

    public DuaController(JdbcTemplate jdbc,
                         @Value("${app.content.allow-fixtures:true}") boolean allowFixtures) {
        this.jdbc = jdbc;
        this.allowFixtures = allowFixtures;
    }

    @GetMapping("/categories")
    @Operation(summary = "Dua categories")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> categories() {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT code, name_en AS "nameEn", name_ar AS "nameAr", name_ur AS "nameUr",
                       name_hi AS "nameHi", sort_order AS "sortOrder"
                FROM dua_categories WHERE publish_state = 'published' ORDER BY sort_order, code
                """)));
    }

    @GetMapping("")
    @Operation(summary = "Browse duas (optionally by category code)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String category,
                                                    @RequestParam(defaultValue = "20") int limit,
                                                    @RequestParam(defaultValue = "1") int page) {
        int capped = Math.min(Math.max(1, limit), 100);
        int offset = (Math.max(1, page) - 1) * capped;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.title, d.text_ar AS "textAr", d.transliteration,
                       d.meaning_en AS "meaningEn", d.meaning_ur AS "meaningUr", d.meaning_hi AS "meaningHi",
                       d.reference, d.source_attribution AS "sourceAttribution",
                       d.authenticity_note AS "authenticityNote", d.audio_media_id AS "audioMediaId",
                       d.is_fixture AS "isFixture", c.code AS "categoryCode", c.name_en AS "categoryName"
                FROM duas d JOIN dua_categories c ON c.id = d.category_id
                WHERE d.publish_state = 'published' AND (d.is_fixture = false OR ?)
                  AND (?::text IS NULL OR c.code = ?)
                ORDER BY c.sort_order, d.id LIMIT ? OFFSET ?
                """, allowFixtures, category, category, capped, offset);
        return ResponseEntity.ok(Pages.envelope(rows));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dua detail")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.title, d.text_ar AS "textAr", d.transliteration,
                       d.meaning_en AS "meaningEn", d.meaning_ur AS "meaningUr", d.meaning_hi AS "meaningHi",
                       d.reference, d.source_attribution AS "sourceAttribution",
                       d.authenticity_note AS "authenticityNote", d.audio_media_id AS "audioMediaId",
                       d.is_fixture AS "isFixture", c.code AS "categoryCode", c.name_en AS "categoryName"
                FROM duas d JOIN dua_categories c ON c.id = d.category_id
                WHERE d.id = ? AND d.publish_state = 'published' AND (d.is_fixture = false OR ?)
                """, id, allowFixtures);
        if (rows.isEmpty()) throw ApiException.notFound("dua not found");
        return ResponseEntity.ok(Pages.envelope(rows.get(0)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search duas")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q,
                                                      @RequestParam(defaultValue = "20") int limit,
                                                      @RequestParam(defaultValue = "1") int page) {
        if (q == null || q.isBlank()) throw ApiException.validation("q is required");
        int capped = Math.min(Math.max(1, limit), 50);
        int offset = (Math.max(1, page) - 1) * capped;
        String like = "%" + q + "%";
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT d.id, d.title, d.meaning_en AS "meaningEn", c.code AS "categoryCode",
                       c.name_en AS "categoryName", d.is_fixture AS "isFixture"
                FROM duas d JOIN dua_categories c ON c.id = d.category_id
                WHERE d.publish_state = 'published' AND (d.is_fixture = false OR ?)
                  AND (d.title ILIKE ? OR d.meaning_en ILIKE ? OR d.transliteration ILIKE ?)
                ORDER BY c.sort_order, d.id LIMIT ? OFFSET ?
                """, allowFixtures, like, like, like, capped, offset)));
    }
}
