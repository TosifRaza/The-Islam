package app.ikp.backend.audit;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Searchable audit trail (approved spec §3.19.6; matrix "Audit logs": Super
 * Admin Full, Admin View). Read-only: no endpoint creates, edits or erases
 * audit rows — ordinary administration cannot rewrite history.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "admin-audit")
public class AuditAdminController {

    private final AuditLogRepository repo;

    public AuditAdminController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Filterable audit trail (spec §3.19.6)")
    @PreAuthorize("hasAuthority('audit.view')")
    public ResponseEntity<Map<String, Object>> search(@RequestParam(required = false) Long actorId,
                                                      @RequestParam(required = false) String entity,
                                                      @RequestParam(required = false) String action,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Instant fromTs = parseTs(from);
        Instant toTs = parseTs(to);
        Page<AuditLog> result = repo.findAll(AuditLogRepository.filter(actorId,
                        entity == null || entity.isBlank() ? null : entity,
                        action == null || action.isBlank() ? null : action,
                        fromTs, toTs),
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(Pages.envelope(result, AuditAdminController::toRow));
    }

    static Map<String, Object> toRow(AuditLog a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("at", String.valueOf(a.getCreatedAt()));
        row.put("actorId", a.getActorId());
        row.put("action", a.getAction());
        row.put("entityType", a.getEntityType());
        row.put("entityId", a.getEntityId());
        row.put("beforeHash", a.getBeforeHash());
        row.put("afterHash", a.getAfterHash());
        row.put("ip", a.getIp());
        row.put("userAgent", a.getUserAgent());
        return row;
    }

    private static Instant parseTs(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw app.ikp.backend.common.ApiException.validation("timestamp must be ISO-8601 instant");
        }
    }
}
