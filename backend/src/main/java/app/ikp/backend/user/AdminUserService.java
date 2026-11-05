package app.ikp.backend.user;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.audit.AuditLog;
import app.ikp.backend.audit.AuditLogRepository;
import app.ikp.backend.audit.AuditService;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.user.dto.ReasonRequest;
import app.ikp.backend.user.dto.RoleChangeRequest;
import app.ikp.backend.user.dto.UserDetail;
import app.ikp.backend.user.dto.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin user management (approved spec §3.2.14–20; Permission Matrix rows
 * "Users"). Sensitive actions (suspend, role changes, deletion) are
 * permission-protected, guarded against lockouts and audited with reason.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository sessions;
    private final AccountDeletionRequestRepository deletions;
    private final AuditLogRepository auditRepo;
    private final AuditService audit;

    public AdminUserService(UserRepository users, RoleRepository roles, RefreshTokenRepository sessions,
                            AccountDeletionRequestRepository deletions, AuditLogRepository auditRepo,
                            AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.deletions = deletions;
        this.auditRepo = auditRepo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> search(String q, String status, Pageable pageable, boolean maskEmail) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        String st = (status == null || status.isBlank()) ? null : status;
        // the caller's Pageable (including its sort — newest first) is passed
        // through untouched; rebuilding it would drop ordering and make page 1
        // content depend on heap order
        return users.findAll(UserRepository.filter(query, st), pageable)
                .map(u -> toSummary(u, maskEmail));
    }

    @Transactional(readOnly = true)
    public UserDetail detail(UUID publicId, boolean maskEmail) {
        User user = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("user not found"));
        List<UserDetail.DeletionRequestView> deletionsView = deletions.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(d -> new UserDetail.DeletionRequestView(d.getPublicId(), d.getState(), d.getReason(),
                        d.getCreatedAt(), d.getPurgeAt()))
                .toList();
        UserSummary s = toSummary(user, maskEmail);
        return new UserDetail(s.userId(), s.email(), s.displayName(), s.status(), user.getDeletionState(),
                s.roles(), s.registeredAt(), s.lastSeenAt(), deletionsView);
    }

    @Transactional
    public UserSummary suspend(UUID publicId, ReasonRequest request, SecurityPrincipal actor) {
        User user = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("user not found"));
        if (user.getId().equals(actor.id())) {
            throw ApiException.validation("administrators cannot suspend their own account");
        }
        if (!user.isActive()) {
            throw ApiException.workflow("user is not active");
        }
        if (isLastActiveSuperAdmin(user)) {
            throw ApiException.workflow("cannot suspend the last active Super Admin");
        }
        Map<String, Object> before = Map.of("status", user.getStatus());
        user.setStatus(User.Status.suspended.name());
        sessions.revokeAllByUserId(user.getId(), Instant.now()); // session revocation
        audit.record(actor.id(), "user.suspend", "user", user.getId(), before,
                Map.of("status", user.getStatus()), request.reason());
        return toSummary(user, false);
    }

    @Transactional
    public UserSummary unsuspend(UUID publicId, ReasonRequest request, SecurityPrincipal actor) {
        User user = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("user not found"));
        if (User.Status.active.name().equals(user.getStatus())) {
            throw ApiException.workflow("user is not suspended");
        }
        Map<String, Object> before = Map.of("status", user.getStatus());
        user.setStatus(User.Status.active.name());
        audit.record(actor.id(), "user.unsuspend", "user", user.getId(), before,
                Map.of("status", user.getStatus()), request == null ? null : request.reason());
        return toSummary(user, false);
    }

    /** Role assignment — Super Admin only (matrix "Roles & permissions"). */
    @Transactional
    public UserDetail changeRoles(UUID publicId, RoleChangeRequest request, SecurityPrincipal actor) {
        User user = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("user not found"));
        List<String> before = user.getRoles().stream().map(Role::getCode).sorted().toList();
        if (request.add() != null) {
            for (String code : request.add()) {
                Role role = roles.findByCode(code).orElseThrow(() -> ApiException.validation("unknown role: " + code));
                user.getRoles().add(role);
            }
        }
        if (request.remove() != null) {
            for (String code : request.remove()) {
                if (user.getId().equals(actor.id()) && "super_admin".equals(code)) {
                    throw ApiException.validation("administrators cannot revoke their own Super Admin role");
                }
                user.getRoles().removeIf(r -> r.getCode().equals(code));
            }
        }
        List<String> after = user.getRoles().stream().map(Role::getCode).sorted().toList();
        if (before.contains("super_admin") && !after.contains("super_admin")
                && users.countByStatus(User.Status.active.name()) > 0 && countActiveSuperAdmins() == 0) {
            throw ApiException.workflow("at least one active Super Admin must remain");
        }
        audit.record(actor.id(), "user.roles_changed", "user", user.getId(), Map.of("roles", before),
                Map.of("roles", after), null);
        return detail(publicId, false);
    }

    /** Deletion workflow start — Super Admin schedules (+7 d grace), Admin creates a review task. */
    @Transactional
    public UserDetail.DeletionRequestView requestDeletion(UUID publicId, ReasonRequest request, SecurityPrincipal actor) {
        User user = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("user not found"));
        if (user.getId().equals(actor.id())) {
            throw ApiException.validation("administrators cannot start deletion on their own account here");
        }
        boolean superAdmin = actor.authorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        AccountDeletionRequest d = new AccountDeletionRequest();
        d.setUser(user);
        d.setRequestedBy(actor.id());
        d.setReason(request.reason());
        if (superAdmin) {
            d.setState("scheduled");
            d.setPurgeAt(Instant.now().plus(7, ChronoUnit.DAYS));
        } else {
            d.setState("pending_review");
        }
        user.setDeletionState("requested");
        deletions.save(d);
        audit.record(actor.id(), "user.deletion_requested", "user", user.getId(),
                Map.of("deletion_state", "none"),
                Map.of("deletion_state", "requested", "workflow", d.getState()), request.reason());
        return new UserDetail.DeletionRequestView(d.getPublicId(), d.getState(), d.getReason(),
                d.getCreatedAt(), d.getPurgeAt());
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> activity(UUID publicId, Pageable pageable) {
        User user = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("user not found"));
        return auditRepo.findByActorIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(AdminUserService::activityRow);
    }

    static Map<String, Object> activityRow(AuditLog a) {
        return Map.ofEntries(
                Map.entry("at", String.valueOf(a.getCreatedAt())),
                Map.entry("action", a.getAction()),
                Map.entry("entityType", a.getEntityType()),
                Map.entry("entityId", a.getEntityId() == null ? "—" : String.valueOf(a.getEntityId())));
    }

    private long countActiveSuperAdmins() {
        return users.findAll().stream()
                .filter(u -> User.Status.active.name().equals(u.getStatus()))
                .filter(u -> u.getRoles().stream().anyMatch(r -> "super_admin".equals(r.getCode())))
                .count();
    }

    private boolean isLastActiveSuperAdmin(User candidate) {
        return candidate.getRoles().stream().anyMatch(r -> "super_admin".equals(r.getCode()))
                && countActiveSuperAdmins() <= 1;
    }

    static UserSummary toSummary(User user, boolean maskEmail) {
        String email = maskEmail ? mask(user.getEmail()) : user.getEmail();
        return new UserSummary(user.getPublicId(), email, displayName(user.getEmail()),
                user.getStatus(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.getCreatedAt(), user.getLastLoginAt(), user.getDeletionState());
    }

    /** Support Managers see masked PII (approved spec §3.2.14; permission description). */
    static String mask(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String displayName(String email) {
        if (email == null) return "user";
        return email.substring(0, email.indexOf('@'));
    }
}
