package com.ikp.dev.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Wire DTOs for the auth endpoints (docs/05 §3.1). Records are deserialised
 * by Gson reflectively; field names match the backend JSON exactly.
 */
public final class Dtos {

    private Dtos() {
    }

    /** POST /auth/login body. */
    public static final class LoginRequestDto {
        public final String email;
        public final String password;
        @SerializedName(value = "deviceName", alternate = "device_name")
        public final String deviceName;

        public LoginRequestDto(String email, String password, String deviceName) {
            this.email = email;
            this.password = password;
            this.deviceName = deviceName;
        }
    }

    /** POST /auth/refresh + /auth/logout body. */
    public static final class RefreshRequestDto {
        public final String refreshToken;

        public RefreshRequestDto(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    /** Token pair response (access 15 min; refresh 30 d rotating). */
    public static final class TokenResponseDto {
        @SerializedName(value = "accessToken", alternate = "access_token")
        public String accessToken;
        @SerializedName(value = "refreshToken", alternate = "refresh_token")
        public String refreshToken;
        @SerializedName(value = "expiresIn", alternate = "expires_in")
        public Long expiresIn;
        public UserInfoDto user;
    }

    public static final class UserInfoDto {
        @SerializedName(value = "userId", alternate = "user_id")
        public String userId;
        @SerializedName(value = "displayName", alternate = "display_name")
        public String displayName;
        public List<String> roles;
    }

    /** GET /auth/me response. */
    public static final class MeResponseDto {
        @SerializedName(value = "userId", alternate = "user_id")
        public String userId;
        public String email;
        @SerializedName(value = "displayName", alternate = "display_name")
        public String displayName;
        public String language;
        public String status;
        public List<String> roles;
        public List<String> permissions;
        @SerializedName(value = "createdAt", alternate = "created_at")
        public String createdAt;
    }

    /** GET /auth/sessions entry. */
    public static final class SessionDto {
        @SerializedName(value = "sessionId", alternate = "session_id")
        public Long sessionId;
        @SerializedName(value = "deviceName", alternate = "device_name")
        public String deviceName;
        @SerializedName(value = "createdAt", alternate = "created_at")
        public String createdAt;
        @SerializedName(value = "expiresAt", alternate = "expires_at")
        public String expiresAt;
        public Boolean current;
    }
}
