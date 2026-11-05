package app.ikp.backend.dictionary;

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
 * Arabic Dictionary API (M4 §11). No verified dataset exists yet (SR-18
 * conditional): only clearly-labelled fixture entries are served, gated by
 * app.content.allow-fixtures. Roots/forms are NULL unless a real source
 * provides them — none are invented.
 */
@RestController
@RequestMapping("/api/v1/dictionary")
@Tag(name = "dictionary")
public class DictionaryController {

    private final JdbcTemplate jdbc;
    private final boolean allowFixtures;

    public DictionaryController(JdbcTemplate jdbc,
                                @Value("${app.content.allow-fixtures:true}") boolean allowFixtures) {
        this.jdbc = jdbc;
        this.allowFixtures = allowFixtures;
    }

    @GetMapping("/search")
    @Operation(summary = "Word lookup (Arabic headword or English meaning)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q,
                                                      @RequestParam(defaultValue = "20") int limit,
                                                      @RequestParam(defaultValue = "1") int page) {
        if (q == null || q.isBlank()) throw ApiException.validation("q is required");
        int capped = Math.min(Math.max(1, limit), 50);
        int offset = (Math.max(1, page) - 1) * capped;
        String like = "%" + q + "%";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, headword_ar AS "headwordAr", root, part_of_speech AS "partOfSpeech",
                       transliteration, pronunciation, meaning_en AS "meaningEn",
                       meaning_ur AS "meaningUr", meaning_hi AS "meaningHi", forms, related,
                       is_fixture AS "isFixture"
                FROM dictionary_entries
                WHERE publish_state = 'published' AND (is_fixture = false OR ?)
                  AND (headword_ar ILIKE ? OR meaning_en ILIKE ? OR transliteration ILIKE ?)
                ORDER BY headword_ar LIMIT ? OFFSET ?
                """, allowFixtures, like, like, like, capped, offset);
        return ResponseEntity.ok(Pages.envelope(rows));
    }

    @GetMapping("/entries/{id}")
    @Operation(summary = "Word detail")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> entry(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, headword_ar AS "headwordAr", root, part_of_speech AS "partOfSpeech",
                       transliteration, pronunciation, meaning_en AS "meaningEn",
                       meaning_ur AS "meaningUr", meaning_hi AS "meaningHi", forms, related,
                       is_fixture AS "isFixture"
                FROM dictionary_entries
                WHERE id = ? AND publish_state = 'published' AND (is_fixture = false OR ?)
                """, id, allowFixtures);
        if (rows.isEmpty()) throw ApiException.notFound("dictionary entry not found");
        return ResponseEntity.ok(Pages.envelope(rows.get(0)));
    }
}
