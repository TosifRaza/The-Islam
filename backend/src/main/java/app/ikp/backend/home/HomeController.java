package app.ikp.backend.home;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.chrono.HijrahChronology;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Personalised home dashboard (M4 §6). Public blocks work for guests;
 * personalised blocks (continue reading, recently read) only for the
 * authenticated principal. M5+ modules report their honest empty state.
 */
@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "home")
public class HomeController {

    private final JdbcTemplate jdbc;

    public HomeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Operation(summary = "Aggregated dashboard payload")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> home(@AuthenticationPrincipal SecurityPrincipal principal,
                                                    @RequestParam(defaultValue = "UTC") String tz) {
        Map<String, Object> data = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(safeZone(tz));
        Map<String, Object> hijri = new LinkedHashMap<>();
        try {
            HijrahDate hd = HijrahChronology.INSTANCE.date(today);
            hijri.put("year", hd.get(ChronoField.YEAR));
            hijri.put("month", hd.get(ChronoField.MONTH_OF_YEAR));
            hijri.put("day", hd.get(ChronoField.DAY_OF_MONTH));
            hijri.put("calculated", true);
            hijri.put("gregorian", today.toString());
        } catch (Exception e) {
            hijri.put("calculated", false);
        }
        data.put("hijriToday", hijri);
        data.put("islamicEvents", jdbc.queryForList("""
                SELECT code, name_en AS "nameEn", hijri_month AS "hijriMonth", hijri_day AS "hijriDay",
                       basis, is_major AS "isMajor"
                FROM islamic_events WHERE publish_state = 'published'
                ORDER BY hijri_month, hijri_day LIMIT 3
                """));
        data.put("recommendedBooks", jdbc.queryForList("""
                SELECT b.public_id AS "bookId", b.title, b.cover_media_id AS "coverMediaId",
                       s.name AS "subject", b.language
                FROM books b JOIN subjects s ON s.id = b.subject_id
                WHERE b.status = 'published'
                ORDER BY b.published_at DESC NULLS LAST, b.id DESC LIMIT 6
                """));
        data.put("popularSubjects", jdbc.queryForList("""
                SELECT s.slug, s.name, count(b.id) AS "bookCount"
                FROM subjects s
                LEFT JOIN books b ON b.subject_id = s.id AND b.status = 'published'
                WHERE s.status <> 'hidden_unconfirmed'
                GROUP BY s.id, s.slug, s.name ORDER BY count(b.id) DESC, s.sort_order LIMIT 8
                """));
        List<Map<String, Object>> utilities = new ArrayList<>();
        utilities.add(Map.of("code", "prayer_times", "title", "Prayer Times", "route", "utilities/prayer", "offline", true));
        utilities.add(Map.of("code", "qibla", "title", "Qibla", "route", "utilities/qibla", "offline", true));
        utilities.add(Map.of("code", "tasbih", "title", "Tasbih", "route", "utilities/tasbih", "offline", true));
        utilities.add(Map.of("code", "duas", "title", "Duas", "route", "duas", "offline", false));
        utilities.add(Map.of("code", "quran", "title", "Quran", "route", "quran", "offline", false));
        utilities.add(Map.of("code", "hadith", "title", "Hadith", "route", "hadith", "offline", false));
        data.put("quickUtilities", utilities);
        if (principal != null) {
            data.put("continueReading", jdbc.queryForList("""
                    SELECT p.ref_key AS "refKey", p.percent, p.position_data AS "positionData",
                           p.updated_at::text AS "updatedAt"
                    FROM reading_progress p WHERE p.user_id = ? AND p.percent BETWEEN 1 AND 99
                    ORDER BY p.updated_at DESC LIMIT 5
                    """, principal.id()));
            data.put("recentlyRead", jdbc.queryForList("""
                    SELECT DISTINCT ON (h.ref_key) h.ref_key, h.title, h.accessed_at::text AS "accessedAt"
                    FROM reading_history h WHERE h.user_id = ?
                    ORDER BY h.ref_key, h.accessed_at DESC LIMIT 10
                    """, principal.id()));
        }
        data.put("continueLearning", List.of());
        data.put("recommendedLessons", List.of());
        return ResponseEntity.ok(Pages.envelope(data));
    }

    private static ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
