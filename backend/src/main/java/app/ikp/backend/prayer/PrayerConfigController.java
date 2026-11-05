package app.ikp.backend.prayer;

import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Prayer-times reference configuration (M4 §13). Times themselves are computed
 * ON DEVICE from these published method parameters using the MIT-licensed
 * Adhan library (SR-17) — offline-safe, no server round-trip needed.
 */
@RestController
@RequestMapping("/api/v1/prayer")
@Tag(name = "prayer")
public class PrayerConfigController {

    private final JdbcTemplate jdbc;

    public PrayerConfigController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/methods")
    @Operation(summary = "Calculation methods with published parameters")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> methods() {
        return ResponseEntity.ok(Pages.envelope(jdbc.queryForList("""
                SELECT code, name, fajr_angle AS "fajrAngle", isha_angle AS "ishaAngle",
                       isha_interval AS "ishaInterval", madhab_default AS "madhabDefault"
                FROM prayer_calculation_methods ORDER BY code
                """)));
    }
}
