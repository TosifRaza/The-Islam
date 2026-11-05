package com.ikp.dev.app.core.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Abstraction over credential storage so the rest of the app (and JVM tests)
 * never touch android.security directly. Implementations MUST be backed by
 * Android Keystore encryption in production code.
 *
 * SECURITY RULES (prompt §8): never store passwords; never log token values;
 * clear() must remove everything.
 */
public interface TokenStore {

    @Nullable
    String accessToken();

    @Nullable
    String refreshToken();

    @Nullable
    String userId();

    @Nullable
    String displayName();

    @Nullable
    String email();

    @Nullable
    String rolesJson();

    @Nullable
    String deviceName();

    void save(@NonNull String accessToken, @NonNull String refreshToken,
              @NonNull String userId, @NonNull String displayName,
              @NonNull String email, @Nullable String rolesJson,
              @Nullable String deviceName);

    /** Persist a rotated token pair (single-use refresh). */
    void updateTokens(@NonNull String accessToken, @NonNull String refreshToken);

    /** Remove every stored credential. */
    void clear();

    boolean hasSession();
}
