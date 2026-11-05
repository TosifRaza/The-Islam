package com.ikp.dev.app;

/**
 * Build metadata surfaced for support screens. Pure Java (no android.* imports)
 * so it is unit-testable on the JVM without the Android SDK.
 */
public final class VersionInfo {

    private final String versionName;
    private final int versionCode;
    private final String milestone;

    public VersionInfo(String versionName, int versionCode, String milestone) {
        if (versionName == null || versionName.isBlank()) {
            throw new IllegalArgumentException("versionName must not be empty");
        }
        if (versionCode <= 0) {
            throw new IllegalArgumentException("versionCode must be positive");
        }
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.milestone = milestone;
    }

    public String versionName() {
        return versionName;
    }

    public int versionCode() {
        return versionCode;
    }

    public String milestone() {
        return milestone;
    }

    /** Human-readable support string, e.g. "0.1.0-m1 (1) — M1 foundation". */
    public String display() {
        return versionName + " (" + versionCode + ") — " + milestone;
    }
}
