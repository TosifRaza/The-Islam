package app.ikp.backend.audit;

import app.ikp.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Audit service — every admin/workflow/state-changing call writes an audit
 * entry in the SAME service transaction (docs/05-api-specification.md §1 Audit;
 * docs/07-security-plan.md §7). Records are append-only: this service only
 * inserts; no update or delete path exists anywhere.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repo;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    /** Joins the caller's transaction (REQUIRED) so the audit row is atomic with the change. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long actorId, String action, String entityType, Long entityId,
                       Object before, Object after, String reason) {
        AuditLog row = new AuditLog();
        row.actorId = actorId != null ? actorId : currentUserIdOrNull();
        row.action = action;
        row.entityType = entityType;
        row.entityId = entityId;
        row.beforeHash = hash(before);
        row.afterHash = hash(after == null && reason != null ? Map.of("reason", reason) : after);
        applyRequestMeta(row);
        repo.save(row);
        if ("WORKFLOW_DENY".equals(action)) {
            log.warn("AUDIT workflow-deny entity={}/{} actor={}", entityType, entityId, row.actorId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Long entityId, Object before, Object after) {
        record(null, action, entityType, entityId, before, after, null);
    }

    /**
     * Denial events (WORKFLOW-001 paths) are written in their OWN transaction so
     * the attempt stays audited even though the caller's transaction rolls back —
     * "denied attempts must be logged" (docs/07-security-plan.md §3) requires
     * persistence independent of the failed operation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(String action, String entityType, Long entityId, String detail) {
        AuditLog row = new AuditLog();
        row.actorId = currentUserIdOrNull();
        row.action = action;
        row.entityType = entityType;
        row.entityId = entityId;
        row.afterHash = hash(Map.of("denied", true, "detail", detail == null ? "" : detail));
        applyRequestMeta(row);
        repo.save(row);
        log.warn("AUDIT denial entity={}/{} actor={}: {}", entityType, entityId, row.actorId, detail);
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof app.ikp.backend.auth.SecurityPrincipal p) {
            return p.id();
        }
        return null;
    }

    private void applyRequestMeta(AuditLog row) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            row.ip = xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
            String ua = req.getHeader("User-Agent");
            row.userAgent = ua != null && ua.length() > 300 ? ua.substring(0, 300) : ua;
        }
    }

    private String hash(Object snapshot) {
        if (snapshot == null) return null;
        try {
            byte[] bytes = mapper.writeValueAsBytes(snapshot);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            try {
                byte[] fallback = String.valueOf(snapshot).getBytes(StandardCharsets.UTF_8);
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fallback));
            } catch (Exception ex) {
                throw new IllegalStateException("audit hashing failed", ex);
            }
        }
    }
}
