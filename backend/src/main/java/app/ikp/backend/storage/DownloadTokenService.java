package app.ikp.backend.storage;

import app.ikp.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Short-lived authorised download URLs (approved spec §1 Caching: signed
 * storage URLs expire ≤ 15 min; docs/07-security-plan.md §5: access only via
 * short-lived signed URLs after authorisation). HMAC-SHA256 over
 * mediaId + expiry with the JWT secret (dev); objects stay private — the URL
 * authorises a range-gated stream, never a bucket listing.
 */
@Service
public class DownloadTokenService {

    private final byte[] secret;
    private final long ttlMinutes;

    public DownloadTokenService(@Value("${app.security.jwt-secret}") String secret,
                                @Value("${app.storage.download-url-minutes}") long ttlMinutes) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMinutes = ttlMinutes;
    }

    public record SignedUrl(String url, Instant expiresAt) {
    }

    public SignedUrl sign(String baseUrl, java.util.UUID mediaId) {
        Instant exp = Instant.now().plusSeconds(ttlMinutes * 60);
        String token = mac(mediaId.toString() + ":" + exp.getEpochSecond());
        String url = baseUrl + "/api/v1/files/" + mediaId + "?token=" + token + "&exp=" + exp.getEpochSecond();
        return new SignedUrl(url, exp);
    }

    /** Constant-time validation; expired or tampered tokens are rejected. */
    public boolean validate(java.util.UUID mediaId, String token, String exp) {
        if (token == null || exp == null) return false;
        try {
            long epoch = Long.parseLong(exp);
            if (Instant.ofEpochSecond(epoch).isBefore(Instant.now())) return false;
            String expected = mac(mediaId.toString() + ":" + epoch);
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String mac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw ApiException.of("SERVER-001", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "signing failure");
        }
    }
}
