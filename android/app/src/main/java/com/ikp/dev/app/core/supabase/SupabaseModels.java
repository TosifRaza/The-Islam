package com.ikp.dev.app.core.supabase;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Data models for Supabase Auth and REST APIs.
 */
public final class SupabaseModels {

    private SupabaseModels() {}

    public static final class AuthRequest {
        @SerializedName("email")
        public final String email;

        @SerializedName("password")
        public final String password;

        @SerializedName("data")
        public final Map<String, Object> data;

        public AuthRequest(String email, String password) {
            this(email, password, null);
        }

        public AuthRequest(String email, String password, Map<String, Object> data) {
            this.email = email;
            this.password = password;
            this.data = data;
        }
    }

    public static final class AuthResponse {
        @SerializedName("access_token")
        public String accessToken;

        @SerializedName("token_type")
        public String tokenType;

        @SerializedName("expires_in")
        public long expiresIn;

        @SerializedName("refresh_token")
        public String refreshToken;

        @SerializedName("user")
        public User user;
    }

    public static final class User {
        @SerializedName("id")
        public String id;

        @SerializedName("email")
        public String email;

        @SerializedName("created_at")
        public String createdAt;

        @SerializedName("user_metadata")
        public Map<String, Object> userMetadata;
    }
}
