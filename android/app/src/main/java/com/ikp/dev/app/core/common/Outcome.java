package com.ikp.dev.app.core.common;

import androidx.annotation.Nullable;

/**
 * Generic synchronous operation result: exactly one of value/error is set.
 * Repositories return this instead of throwing so ViewModel code paths stay
 * deterministic and JVM-testable.
 */
public final class Outcome<T> {

    @Nullable
    public final T value;
    @Nullable
    public final com.ikp.dev.app.core.error.ApiError error;

    private Outcome(T value, com.ikp.dev.app.core.error.ApiError error) {
        this.value = value;
        this.error = error;
    }

    public static <T> Outcome<T> ok(T value) {
        return new Outcome<>(value, null);
    }

    public static <T> Outcome<T> fail(com.ikp.dev.app.core.error.ApiError error) {
        return new Outcome<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
