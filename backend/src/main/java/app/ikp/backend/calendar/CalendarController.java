package app.ikp.backend.calendar;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
 * Islamic Calendar API (M4 §12). Dates are CALCULATED (java.time HijrahChronology,
 * islamic-umalqura variant) unless the caller applies a locally-confirmed
 * offset (offsetDays). Every response states calculated=true/false so
 * calculated dates are never presented as confirmed moon-sighting dates.
 */
@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "calendar")
public class CalendarController {

    private final JdbcTemplate jdbc;

    public CalendarController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/today")
    @Operation(summary = "Hijri + Gregorian today (calculated, optional confirmed offset)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> today(
            @RequestParam(defaultValue = "umm_al_qura") String convention,
            @RequestParam(required = false) Integer offsetDays,
            @RequestParam(defaultValue = "UTC") String tz) {
        int offset = resolveOffset(convention, offsetDays);
        ZoneId zone = safeZone(tz);
        LocalDate today = LocalDate.now(zone).plusDays(offset);
        return ResponseEntity.ok(Pages.envelope(hijriPayload(today, offset, convention, zone)));
    }

    @GetMapping("/month")
    @Operation(summary = "Hijri month navigation (days mapped to Gregorian)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> month(@RequestParam int hijriYear,
                                                     @RequestParam int hijriMonth,
                                                     @RequestParam(defaultValue = "umm_al_qura") String convention,
                                                     @RequestParam(required = false) Integer offsetDays,
                                                     @RequestParam(defaultValue = "UTC") String tz) {
        int offset = resolveOffset(convention, offsetDays);
        try {
            HijrahDate first = HijrahDate.of(hijriYear, hijriMonth, 1);
            int length = first.lengthOfMonth();
            List<Map<String, Object>> days = new ArrayList<>();
            for (int d = 1; d <= length; d++) {
                HijrahDate hd = HijrahDate.of(hijriYear, hijriMonth, d);
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("hijriDay", d);
                day.put("gregorian", LocalDate.from(hd).toString());
                days.add(day);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("hijriYear", hijriYear);
            data.put("hijriMonth", hijriMonth);
            data.put("monthLength", length);
            data.put("calculated", offset == 0);
            data.put("offsetDays", offset);
            data.put("convention", convention);
            data.put("days", days);
            return ResponseEntity.ok(Pages.envelope(data));
        } catch (java.time.DateTimeException e) {
            throw ApiException.validation("hijri date out of supported range (Umm al-Qura 1300–1600 AH): " + e.getMessage());
        }
    }

    @GetMapping("/events")
    @Operation(summary = "Islamic events for a Hijri year (calculated gregorian dates)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> events(@RequestParam(required = false) Integer hijriYear,
                                                      @RequestParam(defaultValue = "umm_al_qura") String convention,
                                                      @RequestParam(required = false) Integer offsetDays) {
        int offset = resolveOffset(convention, offsetDays);
        int year = hijriYear == null ? HijrahDate.now().get(ChronoField.YEAR) : hijriYear;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code, name_en AS "nameEn", name_ar AS "nameAr", hijri_month AS "hijriMonth",
                       hijri_day AS "hijriDay", basis, is_major AS "isMajor", description
                FROM islamic_events WHERE publish_state = 'published' ORDER BY hijri_month, hijri_day
                """);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> ev : rows) {
            Map<String, Object> out = new LinkedHashMap<>(ev);
            try {
                HijrahDate hd = HijrahDate.of(year, (Integer) ev.get("hijriMonth"), (Integer) ev.get("hijriDay"));
                out.put("gregorianDate", LocalDate.from(hd).minusDays(offset).toString());
                out.put("calculated", offset == 0 && "calculated".equals(ev.get("basis")));
            } catch (java.time.DateTimeException e) {
                continue;
            }
            data.add(out);
        }
        return ResponseEntity.ok(Pages.envelope(data));
    }

    @GetMapping("/conventions")
    @Operation(summary = "Available calendar conventions")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> conventions() {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList(
                "SELECT code, name, default_offset_days AS \"defaultOffsetDays\", is_default AS \"isDefault\" "
                        + "FROM hijri_conventions ORDER BY is_default DESC, code")));
    }

    private int resolveOffset(String convention, Integer offsetDays) {
        if (offsetDays != null) {
            if (offsetDays < -3 || offsetDays > 3) throw ApiException.validation("offsetDays must be within -3..3");
            return offsetDays;
        }
        Integer def = jdbc.queryForObject(
                "SELECT default_offset_days FROM hijri_conventions WHERE code = ?", Integer.class, convention);
        return def == null ? 0 : def;
    }

    private Map<String, Object> hijriPayload(LocalDate gregorian, int offset, String convention, ZoneId zone) {
        try {
            HijrahDate hd = HijrahChronology.INSTANCE.date(gregorian);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("gregorian", gregorian.minusDays(offset).toString());
            data.put("hijri", Map.of(
                    "year", hd.get(ChronoField.YEAR),
                    "month", hd.get(ChronoField.MONTH_OF_YEAR),
                    "day", hd.get(ChronoField.DAY_OF_MONTH),
                    "monthLength", hd.lengthOfMonth()));
            data.put("calculated", offset == 0);
            data.put("offsetDays", offset);
            data.put("convention", convention);
            data.put("timeZone", zone.getId());
            data.put("note", offset == 0
                    ? "Calculated Umm al-Qura date — not a confirmed local moon-sighting date."
                    : "Calculated date adjusted by a locally-confirmed offset of " + offset + " day(s).");
            return data;
        } catch (java.time.DateTimeException e) {
            throw ApiException.validation("date outside the supported Hijrah range");
        }
    }

    private static ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            throw ApiException.validation("unknown time zone: " + tz);
        }
    }
}
