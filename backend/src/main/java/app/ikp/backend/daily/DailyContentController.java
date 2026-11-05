package app.ikp.backend.daily;

import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily content API (M4 §6). Content comes ONLY from published
 * daily_content_items records managed in the admin CMS — nothing is hardcoded
 * here. Undated published items rotate deterministically by calendar day.
 */
@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "daily-content")
public class DailyContentController {

    private final JdbcTemplate jdbc;

    public DailyContentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/daily")
    @Operation(summary = "Daily content slots for a date (published records only)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> daily(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "UTC") String tz) {
        LocalDate day = date == null || date.isBlank()
                ? LocalDate.now(safeZone(tz)) : LocalDate.parse(date);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        for (String slot : List.of("ayah_of_day", "hadith_of_day", "daily_reminder", "islamic_event")) {
            data.put(slot, resolveSlot(slot, day));
        }
        return ResponseEntity.ok(Pages.envelope(data));
    }

    private Map<String, Object> resolveSlot(String slot, LocalDate day) {
        List<Map<String, Object>> dated = jdbc.queryForList("""
                SELECT id, content_type AS "contentType", ayah_surah_id AS "ayahSurahId",
                       ayah_number AS "ayahNumber", hadith_id AS "hadithId", dua_id AS "duaId",
                       event_code AS "eventCode", custom_text AS "customText", attribution
                FROM daily_content_items
                WHERE slot_code = ? AND status = 'published' AND publish_date = ?
                ORDER BY rotation_weight DESC, id
                """, slot, day);
        if (dated.isEmpty()) {
            List<Map<String, Object>> undated = jdbc.queryForList("""
                    SELECT id, content_type AS "contentType", ayah_surah_id AS "ayahSurahId",
                           ayah_number AS "ayahNumber", hadith_id AS "hadithId", dua_id AS "duaId",
                           event_code AS "eventCode", custom_text AS "customText", attribution
                    FROM daily_content_items
                    WHERE slot_code = ? AND status = 'published' AND publish_date IS NULL
                    ORDER BY rotation_weight DESC, id
                    """, slot);
            if (undated.isEmpty()) return null;
            return expand(undated.get(day.getDayOfYear() % undated.size()));
        }
        return expand(dated.get(0));
    }

    /** Expands referenced records (ayah text / hadith text) with attribution. */
    private Map<String, Object> expand(Map<String, Object> item) {
        Map<String, Object> out = new LinkedHashMap<>(item);
        String type = String.valueOf(item.get("contentType"));
        if ("ayah".equals(type) && item.get("ayahSurahId") != null) {
            List<Map<String, Object>> ayah = jdbc.queryForList("""
                    SELECT a.text_uthmani AS "textUthmani", s.name_transliterated AS "surahName",
                           s.name_ar AS "surahNameAr", a.surah_id AS "surahId", a.ayah_number AS "ayahNumber"
                    FROM quran_ayahs a JOIN quran_surahs s ON s.id = a.surah_id
                    WHERE a.surah_id = ? AND a.ayah_number = ?
                    """, item.get("ayahSurahId"), item.get("ayahNumber"));
            if (!ayah.isEmpty()) {
                out.put("ayah", ayah.get(0));
                List<Map<String, Object>> tr = jdbc.queryForList("""
                        SELECT e.code AS "editionCode", e.language, e.display_name AS "displayName",
                               e.is_fixture AS "isFixture", t.text
                        FROM quran_ayah_translations t
                        JOIN quran_translation_editions e ON e.id = t.edition_id
                        WHERE t.surah_id = ? AND t.ayah_number = ? AND e.publish_state = 'published'
                        ORDER BY (e.language = 'en') DESC, e.code LIMIT 1
                        """, item.get("ayahSurahId"), item.get("ayahNumber"));
                if (!tr.isEmpty()) out.put("translation", tr.get(0));
            }
        } else if ("hadith".equals(type) && item.get("hadithId") != null) {
            List<Map<String, Object>> hadith = jdbc.queryForList("""
                    SELECT h.id, h.text_ar AS "textAr", h.text_en AS "textEn", h.text_ur AS "textUr",
                           h.gradings, h.number_in_book AS "numberInBook", c.name_en AS "collectionName",
                           c.code AS "collectionCode", c.scholarly_qa_status AS "scholarlyQaStatus",
                           b.name_en AS "bookName"
                    FROM hadiths h JOIN hadith_collections c ON c.id = h.collection_id
                    JOIN hadith_books b ON b.id = h.book_id
                    WHERE h.id = ?
                    """, item.get("hadithId"));
            if (!hadith.isEmpty()) out.put("hadith", hadith.get(0));
        } else if ("event".equals(type) && item.get("eventCode") != null) {
            List<Map<String, Object>> ev = jdbc.queryForList("""
                    SELECT code, name_en AS "nameEn", name_ar AS "nameAr", hijri_month AS "hijriMonth",
                           hijri_day AS "hijriDay", basis, is_major AS "isMajor", description
                    FROM islamic_events WHERE code = ? AND publish_state = 'published'
                    """, item.get("eventCode"));
            if (!ev.isEmpty()) {
                Map<String, Object> event = new LinkedHashMap<>(ev.get(0));
                try {
                    HijrahDate now = HijrahDate.now();
                    int hy = now.get(ChronoField.YEAR);
                    HijrahDate hd = HijrahDate.of(hy, (Integer) event.get("hijriMonth"),
                            (Integer) event.get("hijriDay"));
                    event.put("gregorianDate", LocalDate.from(hd).toString());
                } catch (Exception ignored) { }
                out.put("event", event);
            }
        }
        return out;
    }

    private static ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
