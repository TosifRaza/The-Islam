package app.ikp.backend.auth;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.dto.LoginRequest;
import app.ikp.backend.auth.dto.MeResponse;
import app.ikp.backend.auth.dto.SessionInfo;
import app.ikp.backend.auth.dto.TokenResponse;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.user.RefreshToken;
import app.ikp.backend.user.RefreshTokenRepository;
import app.ikp.backend.user.Role;
import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Authentication service on the M1 identity foundation — no duplicate auth
 * system: users/roles/permissions/refresh_tokens tables from the approved
 * migrations; Argon2id password verification; 15-min HS256 access tokens;
 * rotating refresh tokens (hashed at rest) with reuse detection that revokes
 * the whole family (docs/07-security-plan.md §2). OD-04 remains open: this is
 * the email+password baseline only; no OTP/social provider is chosen here.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final LoginRateLimiter rateLimiter;
    private final long refreshDays;

    public AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder,
                       JwtService jwt, AuditService audit, LoginRateLimiter rateLimiter,
                       @Value("${app.security.refresh-token-days}") long refreshDays) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(clientIp == null ? "unknown" : clientIp)) {
            throw ApiException.of("RATE-001", org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "too many attempts; retry later");
        }
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> ApiException.of("AUTH-001", org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "invalid credentials"));
        if (user.getPasswordHash() == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.of("AUTH-001", org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "invalid credentials");
        }
        if (!user.isActive()) {
            throw ApiException.of("AUTH-004", org.springframework.http.HttpStatus.FORBIDDEN,
                    "account suspended or deleted");
        }
        user.setLastLoginAt(Instant.now());
        String refresh = issueRefresh(user, request.deviceName());
        audit.record(user.getId(), "auth.login", "user", user.getId(), null, Map.of("device", String.valueOf(request.deviceName())), null);
        return toTokenResponse(user, refresh);
    }

    @Transactional
    public TokenResponse refresh(String presentedRefresh) {
        String hash = sha256(presentedRefresh);
        RefreshToken stored = tokens.findByTokenHash(hash)
                .orElseThrow(() -> invalidRefresh());
        if (stored.isRevoked()) {
            // Reuse of a rotated token → revoke the entire family, force re-auth.
            tokens.revokeAllByUserId(stored.getUser().getId(), Instant.now());
            audit.record(stored.getUser().getId(), "auth.refresh.reuse_detected", "user", stored.getUser().getId(),
                    null, null, null);
            throw invalidRefresh();
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw invalidRefresh();
        }
        User user = stored.getUser();
        if (!user.isActive()) {
            throw ApiException.of("AUTH-004", org.springframework.http.HttpStatus.FORBIDDEN,
                    "account suspended or deleted");
        }
        stored.setRevokedAt(Instant.now()); // rotation
        String next = issueRefresh(user, stored.getDeviceName());
        return toTokenResponse(user, next);
    }

    @Transactional
    public void logout(SecurityPrincipal principal, String presentedRefresh) {
        if (presentedRefresh != null && !presentedRefresh.isBlank()) {
            tokens.findByTokenHash(sha256(presentedRefresh))
                    .filter(t -> t.getUser().getId().equals(principal.id()) && !t.isRevoked())
                    .ifPresent(t -> t.setRevokedAt(Instant.now()));
        } else {
            tokens.revokeAllByUserId(principal.id(), Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(SecurityPrincipal principal) {
        User user = users.findById(principal.id()).orElseThrow(() -> ApiException.notFound("user not found"));
        return new MeResponse(user.getPublicId(), user.getEmail(), displayNameOf(user),
                user.getLanguagePref(), user.getStatus(),
                principal.roleCodes(), principal.permissionCodes(), user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(SecurityPrincipal principal) {
        return tokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(principal.id(), Instant.now())
                .stream()
                .map(t -> new SessionInfo(t.getId(), t.getDeviceName(), t.getCreatedAt(), t.getExpiresAt(), false))
                .toList();
    }

    @Transactional
    public void revokeSession(SecurityPrincipal principal, Long sessionId) {
        RefreshToken t = tokens.findById(sessionId).orElseThrow(() -> ApiException.notFound("session not found"));
        if (!t.getUser().getId().equals(principal.id())) {
            audit.record(principal.id(), "auth.session.revoke_denied", "user", t.getUser().getId(), null, null, "not owner");
            throw ApiException.of("AUTHZ-002", org.springframework.http.HttpStatus.FORBIDDEN, "not the resource owner");
        }
        t.setRevokedAt(Instant.now());
    }

    private String issueRefresh(User user, String deviceName) {
        byte[] raw = new byte[48];
        RANDOM.nextBytes(raw);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken row = new RefreshToken();
        row.setUser(user);
        row.setTokenHash(sha256(token));
        row.setDeviceName(deviceName == null || deviceName.isBlank() ? "web-admin" : deviceName.substring(0, Math.min(60, deviceName.length())));
        row.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));
        tokens.save(row);
        return token;
    }

    private TokenResponse toTokenResponse(User user, String refreshToken) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        String display = displayNameOf(user);
        return new TokenResponse(jwt.issue(user.getPublicId(), user.getId()), refreshToken,
                jwt.accessMinutes() * 60, new TokenResponse.UserInfo(user.getPublicId(), display, roles));
    }

    private static String displayNameOf(User user) {
        // M1 identity keeps email as the only identity attribute for now; the
        // admin UI displays the local part until profile display names arrive
        // with their approved phase. Never expose secrets.
        String email = user.getEmail() == null ? "user" : user.getEmail();
        return email.substring(0, email.indexOf('@'));
    }

    private static ApiException invalidRefresh() {
        return ApiException.of("AUTH-005", org.springframework.http.HttpStatus.BAD_REQUEST,
                "refresh token invalid, reused or expired");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
