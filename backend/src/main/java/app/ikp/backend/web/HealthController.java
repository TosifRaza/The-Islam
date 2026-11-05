package app.ikp.backend.web;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops health endpoint following the approved API envelope convention
 * ({"data": ...}, docs/05-api-specification.md §2).
 * Returns 503 when the database is unreachable so orchestrators can react.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String db = "UP";
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            db = "DOWN";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP".equals(db) ? "UP" : "DEGRADED");
        data.put("service", "ikp-backend");
        data.put("db", db);
        data.put("timestamp", Instant.now().toString());
        return ResponseEntity
                .status("UP".equals(db) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("data", data));
    }
}
