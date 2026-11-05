package app.ikp.backend.search;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global grouped search (M4 §6 / SRCH-01). Serves only published platform
 * content. Personal uploads are structurally absent from every query —
 * privacy does not depend on a filter. Groups whose content arrives in M5+
 * return empty results with an explicit availability note.
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "search")
public class GlobalSearchController {

    private final JdbcTemplate jdbc;
    private final boolean allowFixtures;

    public GlobalSearchController(JdbcTemplate jdbc,
                                  @Value("${app.content.allow-fixtures:true}") boolean allowFixtures) {
        this.jdbc = jdbc;
        this.allowFixtures = allowFixtures;
    }

    private static final List<String> ALL_GROUPS = List.of(
            "quran", "hadith", "books", "authors", "subjects", "duas", "dictionary",
            "research", "courses", "lessons", "scholars", "fatwas");
    private static final List<String> LATER_MILESTONES = List.of();

    @GetMapping
    @Operation(summary = "Grouped search across platform content")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(required = false) String groups,
            @RequestParam(defaultValue = "5") int perGroup) {
        if (q == null || q.isBlank()) throw ApiException.validation("q is required");
        String like = "%" + q + "%";
        String fts = app.ikp.backend.quran.QuranRepository.ftsQuery(q);
        List<String> wanted = groups == null || groups.isBlank()
                ? ALL_GROUPS
                : List.of(groups.split(",")).stream().map(String::trim).filter(ALL_GROUPS::contains).toList();
        int cap = Math.min(Math.max(1, perGroup), 20);
        Map<String, Object> data = new LinkedHashMap<>();
        for (String g : ALL_GROUPS) {
            if (!wanted.contains(g)) continue;
            if (LATER_MILESTONES.contains(g)) {
                Map<String, Object> stub = new LinkedHashMap<>();
                stub.put("total", 0);
                stub.put("items", List.of());
                stub.put("note", "content arrives in a later milestone");
                data.put(g, stub);
                continue;
            }
            data.put(g, groupResult(g, q, fts, like, cap));
        }
        return ResponseEntity.ok(Pages.envelope(data));
    }

    private Map<String, Object> groupResult(String group, String q, String fts, String like, int cap) {
        List<Map<String, Object>> items = switch (group) {
            case "quran" -> jdbc.queryForList("""
                    SELECT DISTINCT 'ayah:' || a.surah_id || ':' || a.ayah_number AS id,
                           a.text_uthmani AS title, s.name_transliterated AS subtitle
                    FROM quran_ayahs a JOIN quran_surahs s ON s.id = a.surah_id
                    WHERE to_tsvector('simple', a.text_uthmani) @@ plainto_tsquery('simple', ?)
                       OR a.text_uthmani ILIKE ?
                    ORDER BY 1 LIMIT ?
                    """, fts, like, cap);
            case "hadith" -> jdbc.queryForList("""
                    SELECT 'hadith:' || h.id AS id, coalesce(h.text_en, h.text_ar) AS title,
                           c.name_en || ' ' || b.name_en AS subtitle
                    FROM hadiths h JOIN hadith_collections c ON c.id = h.collection_id
                    JOIN hadith_books b ON b.id = h.book_id
                    WHERE c.publish_state IN ('published','approved')
                      AND (to_tsvector('simple', coalesce(h.text_en,'') || ' ' || coalesce(h.text_ar,''))
                           @@ plainto_tsquery('simple', ?) OR h.text_en ILIKE ?)
                    ORDER BY 1 LIMIT ?
                    """, fts, like, cap);
            case "books" -> jdbc.queryForList("""
                    SELECT 'book:' || b.public_id AS id, b.title AS title, s.name AS subtitle
                    FROM books b JOIN subjects s ON s.id = b.subject_id
                    WHERE b.status = 'published'
                      AND (to_tsvector('english', coalesce(b.title,'') || ' ' || coalesce(b.description,''))
                           @@ plainto_tsquery('english', ?) OR b.title ILIKE ?)
                    ORDER BY 2 LIMIT ?
                    """, fts, like, cap);
            case "authors" -> jdbc.queryForList("""
                    SELECT 'author:' || a.public_id AS id, a.name AS title, coalesce(a.name_ar,'') AS subtitle
                    FROM authors a WHERE a.name ILIKE ? ORDER BY 2 LIMIT ?
                    """, like, cap);
            case "subjects" -> jdbc.queryForList("""
                    SELECT 'subject:' || s.slug AS id, s.name AS title, coalesce(s.description,'') AS subtitle
                    FROM subjects s WHERE s.status <> 'hidden_unconfirmed' AND s.name ILIKE ?
                    ORDER BY 2 LIMIT ?
                    """, like, cap);
            case "duas" -> jdbc.queryForList("""
                    SELECT 'dua:' || d.id AS id, coalesce(d.title, left(coalesce(d.meaning_en,''), 80)) AS title,
                           c.name_en AS subtitle
                    FROM duas d JOIN dua_categories c ON c.id = d.category_id
                    WHERE d.publish_state = 'published' AND (d.is_fixture = false OR ?)
                      AND (d.title ILIKE ? OR d.meaning_en ILIKE ?)
                    ORDER BY 1 LIMIT ?
                    """, allowFixtures, like, like, cap);
            case "dictionary" -> jdbc.queryForList("""
                    SELECT 'dict:' || e.id AS id, e.headword_ar AS title,
                           coalesce(e.transliteration, '') AS subtitle
                    FROM dictionary_entries e
                    WHERE e.publish_state = 'published' AND (e.is_fixture = false OR ?)
                      AND (e.headword_ar ILIKE ? OR e.meaning_en ILIKE ?)
                    ORDER BY 2 LIMIT ?
                    """, allowFixtures, like, like, cap);
            case "research" -> jdbc.queryForList("""
                    SELECT 'research:' || a.public_id AS id, a.title AS title,
                           'tier:' || a.source_tier AS subtitle
                    FROM research_articles a
                    WHERE a.status = 'published'
                      AND (to_tsvector('english', coalesce(a.title,'') || ' ' || coalesce(a.summary,'') || ' ' || coalesce(a.body,''))
                           @@ plainto_tsquery('english', ?) OR a.title ILIKE ?)
                    ORDER BY 2 LIMIT ?
                    """, fts, like, cap);
            case "courses" -> jdbc.queryForList("""
                    SELECT 'course:' || c.public_id AS id, c.title AS title, s.name AS subtitle
                    FROM academy_courses c JOIN subjects s ON s.id = c.subject_id
                    WHERE c.status = 'published'
                      AND (to_tsvector('english', coalesce(c.title,'') || ' ' || coalesce(c.description,''))
                           @@ plainto_tsquery('english', ?) OR c.title ILIKE ?)
                    ORDER BY 2 LIMIT ?
                    """, fts, like, cap);
            case "lessons" -> jdbc.queryForList("""
                    SELECT 'lesson:' || l.public_id AS id, l.title AS title, ch.title AS subtitle
                    FROM academy_lessons l
                    JOIN academy_chapters ch ON ch.id = l.chapter_id
                    JOIN academy_courses c ON c.id = ch.course_id
                    WHERE c.status = 'published' AND l.title ILIKE ?
                    ORDER BY 2 LIMIT ?
                    """, like, cap);
            case "scholars" -> jdbc.queryForList("""
                    SELECT 'scholar:' || sc.public_id AS id, sc.name AS title,
                           coalesce(sc.areas_of_expertise, '') AS subtitle
                    FROM scholars sc
                    WHERE sc.verification_status = 'verified'
                      AND (sc.name ILIKE ? OR sc.areas_of_expertise ILIKE ?)
                    ORDER BY 2 LIMIT ?
                    """, like, like, cap);
            case "fatwas" -> jdbc.queryForList("""
                    SELECT 'fatwa:' || f.public_id AS id, f.question AS title,
                           coalesce(f.category, '') AS subtitle
                    FROM fatwas f
                    WHERE f.state = 'published'
                      AND (f.question ILIKE ? OR coalesce(f.answer, '') ILIKE ?)
                    ORDER BY 2 LIMIT ?
                    """, like, like, cap);
            default -> List.of();
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", items.size());
        out.put("items", items);
        return out;
    }
}
