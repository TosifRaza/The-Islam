package app.ikp.backend.auth.dto;

import java.util.List;

/** Token pair per approved spec §3.1.3 (access 15 min; refresh 30 d, rotating). */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn,
                            UserInfo user) {

    public record UserInfo(java.util.UUID userId, String displayName, List<String> roles) {
    }
}
