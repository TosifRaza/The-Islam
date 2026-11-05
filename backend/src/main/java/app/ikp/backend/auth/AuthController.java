package app.ikp.backend.auth;

import app.ikp.backend.auth.dto.LoginRequest;
import app.ikp.backend.auth.dto.MeResponse;
import app.ikp.backend.auth.dto.RefreshRequest;
import app.ikp.backend.auth.dto.SessionInfo;
import app.ikp.backend.auth.dto.TokenResponse;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Authentication endpoints per approved spec §3.1. Public: login/refresh.
 * Session management (§3.1.9–3.1.10) is available to any authenticated user.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Issue access + refresh tokens (spec §3.1.3)")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest http) {
        TokenResponse tokens = auth.login(request, clientIp(http));
        return ResponseEntity.ok(Pages.envelope(tokens));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token, issue new pair (spec §3.1.4)")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(Pages.envelope(auth.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke presented session (spec §3.1.5)")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SecurityPrincipal principal,
                                       @RequestBody(required = false) RefreshRequest request) {
        auth.logout(principal, request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Principal profile + roles + flattened permissions (spec §3.1.8)")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ResponseEntity.ok(Pages.envelope(auth.me(principal)));
    }

    @GetMapping("/sessions")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "List active sessions/devices (spec §3.1.9)")
    public ResponseEntity<Map<String, Object>> sessions(@AuthenticationPrincipal SecurityPrincipal principal) {
        List<SessionInfo> sessions = auth.sessions(principal);
        return ResponseEntity.ok(Pages.envelope(sessions, new Pages.PageMeta(1, sessions.size(), sessions.size(), 1)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Revoke one own session (spec §3.1.10)")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal SecurityPrincipal principal,
                                              @PathVariable Long sessionId) {
        auth.revokeSession(principal, sessionId);
        return ResponseEntity.noContent().build();
    }

    private static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : http.getRemoteAddr();
    }
}
