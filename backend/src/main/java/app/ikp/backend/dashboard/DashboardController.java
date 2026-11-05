package app.ikp.backend.dashboard;

import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin dashboard KPI aggregate (approved spec §3.19.1; M2 Module A). All
 * metrics are computed from actual PostgreSQL data — empty databases produce
 * honest zeros, never fabricated statistics. Course/research counters report
 * zero with a "not yet implemented" marker until their approved phases arrive.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "admin-dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "KPI aggregate (spec §3.19.1) — real data only")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER','QURAN_EDITOR','HADITH_EDITOR'," +
            "'RESEARCH_EDITOR','SCHOLAR_REVIEWER','ACADEMY_MANAGER','SUPPORT_MANAGER')")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(Pages.envelope(service.metrics()));
    }
}
