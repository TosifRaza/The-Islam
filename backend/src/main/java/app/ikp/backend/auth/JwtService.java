package app.ikp.backend.auth;

import app.ikp.backend.common.ApiException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 access-token signing/verification (15-minute tokens per the approved
 * auth design, docs/07-security-plan.md §2). Identity is proven by the JWT;
 * fresh roles/permissions/status are resolved from the database per request by
 * the token filter — so suspensions take effect immediately and role changes
 * never rely on stale token claims.
 */
@Service
public class JwtService {

    private final byte[] secret;
    private final long accessMinutes;

    public JwtService(@Value("${app.security.jwt-secret}") String secret,
                      @Value("${app.security.access-token-minutes}") long accessMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be >= 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessMinutes = accessMinutes;
    }

    public String issue(UUID userPublicId, Long internalId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userPublicId.toString())
                .claim("uid", internalId)
                .issuer("ikp-backend")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(accessMinutes))))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims);
        try {
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("jwt signing failed", e);
        }
    }

    /** @return the internal user id (uid claim) or null when the token is invalid/expired. */
    public Long verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) return null;
            if (jwt.getJWTClaimsSet().getExpirationTime() == null
                    || jwt.getJWTClaimsSet().getExpirationTime().toInstant().isBefore(Instant.now())) {
                return null;
            }
            Object uid = jwt.getJWTClaimsSet().getClaim("uid");
            return uid instanceof Number n ? n.longValue() : null;
        } catch (Exception parseOrVerify) {
            return null;
        }
    }

    public long accessMinutes() {
        return accessMinutes;
    }
}
