package app.ikp.backend.user;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import app.ikp.backend.user.dto.ReasonRequest;
import app.ikp.backend.user.dto.RoleChangeRequest;
import app.ikp.backend.user.dto.UserDetail;
import app.ikp.backend.user.dto.UserSummary;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin user management endpoints per approved spec §3.2.14–20. Every sensitive
 * action is permission-gated server-side (hiding UI controls is never the
 * security boundary) and audited.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users.view') or hasAuthority('users.manage')")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        boolean mask = isSupportOnly(actor);
        Page<UserSummary> result = service.search(q, status,
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "id")), mask);
        return ResponseEntity.ok(Pages.envelope(result, u -> u));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('users.view') or hasAuthority('users.manage')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.detail(userId, isSupportOnly(actor))));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('roles.manage')")
    public ResponseEntity<Map<String, Object>> patch(@PathVariable UUID userId,
                                                     @RequestBody RoleChangeRequest request,
                                                     @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.changeRoles(userId, request, actor)));
    }

    @PostMapping("/{userId}/suspend")
    @PreAuthorize("hasAuthority('users.suspend')")
    public ResponseEntity<Map<String, Object>> suspend(@PathVariable UUID userId,
                                                       @Valid @RequestBody ReasonRequest request,
                                                       @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.suspend(userId, request, actor)));
    }

    @PostMapping("/{userId}/unsuspend")
    @PreAuthorize("hasAuthority('users.suspend')")
    public ResponseEntity<Map<String, Object>> unsuspend(@PathVariable UUID userId,
                                                         @Valid @RequestBody ReasonRequest request,
                                                         @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.unsuspend(userId, request, actor)));
    }

    @PostMapping("/{userId}/delete")
    @PreAuthorize("hasAuthority('users.delete')")
    public ResponseEntity<Map<String, Object>> requestDeletion(@PathVariable UUID userId,
                                                               @Valid @RequestBody ReasonRequest request,
                                                               @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.accepted()
                .body(Pages.envelope(Map.of("deletionRequest", service.requestDeletion(userId, request, actor))));
    }

    @GetMapping("/{userId}/activity")
    @PreAuthorize("hasAuthority('users.view') or hasAuthority('users.manage')")
    public ResponseEntity<Map<String, Object>> activity(@PathVariable UUID userId,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> result = service.activity(userId,
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100)));
        return ResponseEntity.ok(Pages.envelope(result, a -> a));
    }

    /** Support Manager sees masked PII unless they also hold users.manage. */
    static boolean isSupportOnly(SecurityPrincipal actor) {
        boolean manage = actor.authorities().stream().anyMatch(a -> a.getAuthority().equals("users.manage"));
        boolean support = actor.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPPORT_MANAGER"));
        return support && !manage;
    }
}
