package app.ikp.backend.hadith;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Hadith read side (SR-08, The Unlicense). Numbering/edition provenance is
 * stored on the collection row and surfaced in API responses; gradings are
 * passed through exactly as recorded (never merged into one universal grade).
 */
@Repository
public class HadithRepository {

    private final JdbcTemplate jdbc;

    public HadithRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> collections() {
        return jdbc.queryForList("""
                SELECT c.code, c.name_en AS "nameEn", c.name_ar AS "nameAr",
                       c.edition_note AS "editionNote", c.numbering_note AS "numberingNote",
                       c.scholarly_qa_status AS "scholarlyQaStatus",
                       (SELECT count(*) FROM hadiths h WHERE h.collection_id = c.id) AS "hadithCount",
                       (SELECT count(*) FROM hadith_books b WHERE b.collection_id = c.id) AS "bookCount"
                FROM hadith_collections c
                WHERE c.publish_state IN ('published','approved') ORDER BY c.code
                """);
    }

    public Map<String, Object> collection(String code) {
        var rows = jdbc.queryForList("""
                SELECT id, code, name_en AS "nameEn", name_ar AS "nameAr", edition_note AS "editionNote",
                       numbering_note AS "numberingNote", scholarly_qa_status AS "scholarlyQaStatus"
                FROM hadith_collections WHERE code = ? AND publish_state IN ('published','approved')
                """, code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> books(String collectionCode) {
        return jdbc.queryForList("""
                SELECT b.id, b.book_number AS "bookNumber", b.name_en AS "nameEn", b.name_ar AS "nameAr",
                       b.hadith_first AS "hadithFirst", b.hadith_last AS "hadithLast",
                       (SELECT count(*) FROM hadiths h WHERE h.book_id = b.id) AS "hadithCount"
                FROM hadith_books b JOIN hadith_collections c ON c.id = b.collection_id
                WHERE c.code = ? ORDER BY b.book_number
                """, collectionCode);
    }

    public Map<String, Object> book(long bookId) {
        var rows = jdbc.queryForList("""
                SELECT b.id, b.book_number AS "bookNumber", b.name_en AS "nameEn", b.name_ar AS "nameAr",
                       b.hadith_first AS "hadithFirst", b.hadith_last AS "hadithLast",
                       c.code AS "collectionCode", c.name_en AS "collectionName", c.numbering_note AS "numberingNote"
                FROM hadith_books b JOIN hadith_collections c ON c.id = b.collection_id
                WHERE b.id = ? AND c.publish_state IN ('published','approved')
                """, bookId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> hadiths(long bookId, int offset, int limit) {
        return jdbc.queryForList("""
                SELECT h.id, h.number_in_book AS "numberInBook", h.arabic_number AS "arabicNumber",
                       h.text_ar AS "textAr", h.text_en AS "textEn", h.text_ur AS "textUr",
                       h.narrator_chain AS "narratorChain", h.gradings, h.takhrij,
                       h.reference_note AS "referenceNote"
                FROM hadiths h WHERE h.book_id = ?
                ORDER BY h.number_in_book LIMIT ? OFFSET ?
                """, bookId, limit, offset);
    }

    public Map<String, Object> hadith(long id) {
        var rows = jdbc.queryForList("""
                SELECT h.id, h.number_in_book AS "numberInBook", h.arabic_number AS "arabicNumber",
                       h.text_ar AS "textAr", h.text_en AS "textEn", h.text_ur AS "textUr",
                       h.narrator_chain AS "narratorChain", h.gradings, h.takhrij,
                       h.reference_note AS "referenceNote",
                       c.code AS "collectionCode", c.name_en AS "collectionName",
                       c.numbering_note AS "numberingNote", c.scholarly_qa_status AS "scholarlyQaStatus",
                       b.name_en AS "bookName", b.book_number AS "bookNumber"
                FROM hadiths h
                JOIN hadith_collections c ON c.id = h.collection_id
                JOIN hadith_books b ON b.id = h.book_id
                WHERE h.id = ? AND c.publish_state IN ('published','approved')
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> search(String q, String collectionCode, int offset, int limit) {
        String fts = app.ikp.backend.quran.QuranRepository.ftsQuery(q);
        String like = "%" + q + "%";
        return jdbc.queryForList("""
                SELECT h.id, h.number_in_book AS "numberInBook", h.text_ar AS "textAr", h.text_en AS "textEn",
                       c.code AS "collectionCode", c.name_en AS "collectionName", b.name_en AS "bookName"
                FROM hadiths h
                JOIN hadith_collections c ON c.id = h.collection_id
                JOIN hadith_books b ON b.id = h.book_id
                WHERE c.publish_state IN ('published','approved')
                  AND (c.code = ? OR ?::text IS NULL)
                  AND (to_tsvector('simple', coalesce(h.text_ar,'') || ' ' || coalesce(h.text_en,'')) @@ plainto_tsquery('simple', ?)
                       OR h.text_en ILIKE ? OR h.text_ar ILIKE ?)
                ORDER BY c.code, h.number_in_book LIMIT ? OFFSET ?
                """, collectionCode, collectionCode, fts, like, like, limit, offset);
    }
}
