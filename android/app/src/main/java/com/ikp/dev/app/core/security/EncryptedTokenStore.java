package com.ikp.dev.app.core.security;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Android Keystore-backed credential storage (prompt §8). Uses
 * EncryptedSharedPreferences with an AES256-GCM master key held in the
 * Android Keystore — values at rest are encrypted and integrity-protected.
 *
 * If the Keystore-backed file cannot be opened (corruption, key invalidation
 * after backup restore), the file is deleted and recreated empty: failing
 * closed to a signed-out state instead of crashing.
 *
 * This class NEVER logs token or credential values.
 */
public final class EncryptedTokenStore implements TokenStore {

    private static final String FILE = "ikp_secure_prefs";
    private static final String K_ACCESS = "access_token";
    private static final String K_REFRESH = "refresh_token";
    private static final String K_USER_ID = "user_id";
    private static final String K_DISPLAY_NAME = "display_name";
    private static final String K_EMAIL = "email";
    private static final String K_ROLES = "roles_json";
    private static final String K_DEVICE = "device_name";

    private final SharedPreferences prefs;

    public EncryptedTokenStore(Context context) {
        this.prefs = openOrCreate(context);
    }

    private static SharedPreferences openOrCreate(Context context) {
        try {
            return create(context);
        } catch (GeneralSecurityException | IOException firstAttempt) {
            // Keystore can invalidate entries (e.g. after device restore).
            // Fail closed: drop the corrupted store, start signed out.
            try {
                context.deleteSharedPreferences(FILE);
            } catch (Exception ignored) {
                // best effort on older devices
            }
            try {
                return create(context);
            } catch (GeneralSecurityException | IOException fatal) {
                throw new IllegalStateException("secure token storage unavailable", fatal);
            }
        }
    }

    private static SharedPreferences create(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    @Override
    @Nullable
    public String accessToken() {
        return prefs.getString(K_ACCESS, null);
    }

    @Override
    @Nullable
    public String refreshToken() {
        return prefs.getString(K_REFRESH, null);
    }

    @Override
    @Nullable
    public String userId() {
        return prefs.getString(K_USER_ID, null);
    }

    @Override
    @Nullable
    public String displayName() {
        return prefs.getString(K_DISPLAY_NAME, null);
    }

    @Override
    @Nullable
    public String email() {
        return prefs.getString(K_EMAIL, null);
    }

    @Override
    @Nullable
    public String rolesJson() {
        return prefs.getString(K_ROLES, null);
    }

    @Override
    @Nullable
    public String deviceName() {
        return prefs.getString(K_DEVICE, null);
    }

    @Override
    public void save(String accessToken, String refreshToken, String userId,
                     String displayName, String email, String rolesJson, String deviceName) {
        prefs.edit()
                .putString(K_ACCESS, accessToken)
                .putString(K_REFRESH, refreshToken)
                .putString(K_USER_ID, userId)
                .putString(K_DISPLAY_NAME, displayName)
                .putString(K_EMAIL, email)
                .putString(K_ROLES, rolesJson)
                .putString(K_DEVICE, deviceName)
                .commit();
    }

    @Override
    public void updateTokens(String accessToken, String refreshToken) {
        prefs.edit()
                .putString(K_ACCESS, accessToken)
                .putString(K_REFRESH, refreshToken)
                .commit();
    }

    @Override
    public void clear() {
        prefs.edit().clear().commit();
    }

    @Override
    public boolean hasSession() {
        return prefs.contains(K_REFRESH);
    }
}
