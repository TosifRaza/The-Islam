package app.ikp.backend.quran;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side Quran repository. Serving rules baked into every query:
 * fixture rows are served ONLY when app.content.allow-fixtures=true (dev);
 * only publish_state='published' editions are visible; the canonical Arabic
 * text has no write path anywhere in the codebase.
 */
@Repository
public class QuranRepository {

    private final JdbcTemplate jdbc;
    private final boolean allowFixtures;

    public QuranRepository(JdbcTemplate jdbc,
                           @Value("${app.content.allow-fixtures:true}") boolean allowFixtures) {
        this.jdbc = jdbc;
        this.allowFixtures = allowFixtures;
    }

    private static final String FIXTURE_OK = "(e.is_fixture = false OR ?)";

    public List<Map<String, Object>> surahs() {
        return jdbc.queryForList("""
                SELECT id, name_ar AS "nameAr", name_transliterated AS "nameTransliterated",
                       name_en AS "nameEn", revelation_place AS "revelationPlace",
                       ayah_count AS "ayahCount", bismillah_prefix AS "bismillahPrefix"
                FROM quran_surahs ORDER BY id
                """);
    }

    public Map<String, Object> surah(int id) {
        var rows = jdbc.queryForList("""
                SELECT id, name_ar AS "nameAr", name_transliterated AS "nameTransliterated",
                       name_en AS "nameEn", revelation_place AS "revelationPlace",
                       ayah_count AS "ayahCount", bismillah_prefix AS "bismillahPrefix"
                FROM quran_surahs WHERE id = ?
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> publishedTranslationEditions() {
        return jdbc.queryForList("""
                SELECT e.code, e.language, e.display_name AS "displayName", e.translator,
                       e.is_fixture AS "isFixture", l.title AS "licence", l.attribution_required AS "attributionRequired"
                FROM quran_translation_editions e LEFT JOIN licences l ON l.id = e.licence_id
                WHERE e.publish_state = 'published' AND """ + FIXTURE_OK + """
                 ORDER BY e.language, e.code
                """, allowFixtures);
    }

    public List<Map<String, Object>> publishedTafsirEditions() {
        return jdbc.queryForList("""
                SELECT e.code, e.name_en AS "nameEn", e.name_ar AS "nameAr", e.author, e.language,
                       e.is_fixture AS "isFixture"
                FROM tafsir_editions e
                WHERE e.publish_state = 'published' AND """ + FIXTURE_OK + """
                 ORDER BY e.code
                """, allowFixtures);
    }

    public List<Map<String, Object>> ayahs(int surahId, int offset, int limit) {
        return jdbc.queryForList("""
                SELECT a.surah_id AS "surahId", a.ayah_number AS "ayahNumber", a.text_uthmani AS "textUthmani",
                       a.juz, a.sajdah
                FROM quran_ayahs a WHERE a.surah_id = ?
                ORDER BY a.ayah_number LIMIT ? OFFSET ?
                """, surahId, limit, offset);
    }

    public Map<String, Object> ayah(int surahId, int ayahNumber) {
        var rows = jdbc.queryForList("""
                SELECT a.surah_id AS "surahId", a.ayah_number AS "ayahNumber", a.text_uthmani AS "textUthmani",
                       a.juz, a.sajdah, s.name_transliterated AS "surahName"
                FROM quran_ayahs a JOIN quran_surahs s ON s.id = a.surah_id
                WHERE a.surah_id = ? AND a.ayah_number = ?
                """, surahId, ayahNumber);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Map<String, Object>> translationsForAyah(int surahId, int ayahNumber) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT e.code, e.language, e.display_name AS "displayName", e.is_fixture AS "isFixture", t.text
                FROM quran_ayah_translations t
                JOIN quran_translation_editions e ON e.id = t.edition_id
                WHERE t.surah_id = ? AND t.ayah_number = ?
                  AND e.publish_state = 'published' AND """ + FIXTURE_OK, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("editionCode", rs.getString("code"));
            m.put("language", rs.getString("language"));
            m.put("displayName", rs.getString("displayName"));
            m.put("isFixture", rs.getBoolean("isFixture"));
            m.put("text", rs.getString("text"));
            out.put(rs.getString("code"), m);
        }, surahId, ayahNumber, allowFixtures);
        return out;
    }

    public List<Map<String, Object>> translationsForSurah(int surahId, String editionCode,
                                                          int offset, int limit) {
        return jdbc.query("""
                SELECT t.surah_id AS "surahId", t.ayah_number AS "ayahNumber", t.text,
                       e.is_fixture AS "isFixture"
                FROM quran_ayah_translations t
                JOIN quran_translation_editions e ON e.id = t.edition_id
                WHERE e.code = ? AND t.surah_id = ? AND e.publish_state = 'published' AND """ + FIXTURE_OK + """
                 ORDER BY t.ayah_number LIMIT ? OFFSET ?
                """, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("surahId", rs.getInt("surahId"));
            m.put("ayahNumber", rs.getInt("ayahNumber"));
            m.put("text", rs.getString("text"));
            m.put("isFixture", rs.getBoolean("isFixture"));
            return m;
        }, editionCode, surahId, limit, offset);
    }

    public List<Map<String, Object>> tafsirForSurah(int surahId, String editionCode, int offset, int limit) {
        return jdbc.query("""
                SELECT t.surah_id AS "surahId", t.ayah_number AS "ayahNumber", t.text, e.is_fixture AS "isFixture"
                FROM tafsir_entries t JOIN tafsir_editions e ON e.id = t.edition_id
                WHERE e.code = ? AND t.surah_id = ? AND e.publish_state = 'published' AND """ + FIXTURE_OK + """
                 ORDER BY t.ayah_number LIMIT ? OFFSET ?
                """, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("surahId", rs.getInt("surahId"));
            m.put("ayahNumber", rs.getInt("ayahNumber"));
            m.put("text", rs.getString("text"));
            m.put("isFixture", rs.getBoolean("isFixture"));
            return m;
        }, editionCode, surahId, limit, offset);
    }

    public List<Map<String, Object>> wordsForAyah(int surahId, int ayahNumber) {
        return jdbc.queryForList("""
                SELECT word_position AS "position", text_ar AS "textAr", transliteration,
                       meaning_en AS "meaningEn", meaning_ur AS "meaningUr", meaning_hi AS "meaningHi",
                       is_fixture AS "isFixture"
                FROM quran_word_entries
                WHERE surah_id = ? AND ayah_number = ? AND publish_state = 'published' AND (is_fixture = false OR ?)
                ORDER BY word_position
                """, surahId, ayahNumber, allowFixtures);
    }

    public List<Map<String, Object>> studyNotes(int surahId, int ayahNumber) {
        return jdbc.queryForList("""
                SELECT kind, text, source_ref AS "sourceRef", is_fixture AS "isFixture"
                FROM ayah_study_notes
                WHERE surah_id = ? AND ayah_number = ? AND publish_state = 'published' AND (is_fixture = false OR ?)
                ORDER BY kind
                """, surahId, ayahNumber, allowFixtures);
    }

    public int ayahCount(int surahId) {
        Integer n = jdbc.queryForObject("SELECT ayah_count FROM quran_surahs WHERE id = ?", Integer.class, surahId);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> searchArabic(String q, int offset, int limit) {
        String fts = ftsQuery(q);
        return jdbc.queryForList("""
                SELECT DISTINCT a.surah_id AS "surahId", a.ayah_number AS "ayahNumber",
                       a.text_uthmani AS "textUthmani", s.name_transliterated AS "surahName"
                FROM quran_ayahs a JOIN quran_surahs s ON s.id = a.surah_id
                WHERE ? <> '' AND (to_tsvector('simple', a.text_uthmani) @@ plainto_tsquery('simple', ?)
                       OR a.text_uthmani ILIKE '%' || ? || '%')
                ORDER BY a.surah_id, a.ayah_number LIMIT ? OFFSET ?
                """, q, fts, q, limit, offset);
    }

    public List<Map<String, Object>> searchTranslations(String q, int offset, int limit) {
        String fts = ftsQuery(q);
        return jdbc.query("""
                SELECT DISTINCT t.surah_id AS "surahId", t.ayah_number AS "ayahNumber", t.text,
                       e.code AS "editionCode", e.is_fixture AS "isFixture", s.name_transliterated AS "surahName"
                FROM quran_ayah_translations t
                JOIN quran_translation_editions e ON e.id = t.edition_id
                JOIN quran_ayahs a ON a.surah_id = t.surah_id AND a.ayah_number = t.ayah_number
                JOIN quran_surahs s ON s.id = t.surah_id
                WHERE e.publish_state = 'published' AND (e.is_fixture = false OR ?)
                  AND (to_tsvector('simple', t.text) @@ plainto_tsquery('simple', ?)
                       OR t.text ILIKE '%' || ? || '%')
                ORDER BY t.surah_id, t.ayah_number LIMIT ? OFFSET ?
                """, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("surahId", rs.getInt("surahId"));
            m.put("ayahNumber", rs.getInt("ayahNumber"));
            m.put("text", rs.getString("text"));
            m.put("editionCode", rs.getString("editionCode"));
            m.put("isFixture", rs.getBoolean("isFixture"));
            m.put("surahName", rs.getString("surahName"));
            return m;
        }, allowFixtures, fts, q, limit, offset);
    }

    public static String ftsQuery(String q) {
        return q == null ? "" : q.trim().replaceAll("[\\s\\p{Punct}]+", " ").trim();
    }
}
