package com.ikp.dev.app.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.core.error.ApiError;

/**
 * Reusable loading/empty/error state frame (prompt §19). Every significant
 * screen embeds one; modes are driven from {@code UiState}. The error mode
 * never shows raw server text — only the mapped, user-facing message
 * (plus the stable code, which is safe and useful for support).
 */
public final class StateView extends FrameLayout {

    private android.view.View loading;
    private LinearLayout emptyGroup;
    private TextView emptyTitle;
    private TextView emptyBody;
    private Button emptyAction;
    private LinearLayout errorGroup;
    private TextView errorTitle;
    private TextView errorBody;
    private TextView errorCode;
    private Button errorRetry;

    @Nullable
    private Runnable onRetry;
    @Nullable
    private Runnable onEmptyAction;

    public StateView(@NonNull Context context) {
        super(context);
        init();
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_state, this, true);
        loading = findViewById(R.id.state_loading);
        emptyGroup = findViewById(R.id.state_empty);
        emptyTitle = findViewById(R.id.state_empty_title);
        emptyBody = findViewById(R.id.state_empty_body);
        emptyAction = findViewById(R.id.state_empty_action);
        errorGroup = findViewById(R.id.state_error);
        errorTitle = findViewById(R.id.state_error_title);
        errorBody = findViewById(R.id.state_error_body);
        errorCode = findViewById(R.id.state_error_code);
        errorRetry = findViewById(R.id.state_error_retry);
        errorRetry.setOnClickListener(v -> {
            if (onRetry != null) {
                onRetry.run();
            }
        });
        emptyAction.setOnClickListener(v -> {
            if (onEmptyAction != null) {
                onEmptyAction.run();
            }
        });
        showOnly(R.id.state_loading);
    }

    public void setOnRetry(@Nullable Runnable onRetry) {
        this.onRetry = onRetry;
    }

    public void setOnEmptyAction(@Nullable Runnable action, @Nullable CharSequence label) {
        this.onEmptyAction = action;
        if (label != null) {
            emptyAction.setText(label);
            emptyAction.setVisibility(VISIBLE);
        } else {
            emptyAction.setVisibility(GONE);
        }
    }

    public void renderLoading() {
        showOnly(R.id.state_loading);
    }

    public void renderEmpty() {
        emptyTitle.setText(R.string.state_empty_title);
        emptyBody.setText(R.string.state_empty_body);
        showOnly(R.id.state_empty);
    }

    public void renderEmpty(CharSequence title, CharSequence body) {
        emptyTitle.setText(title);
        emptyBody.setText(body);
        showOnly(R.id.state_empty);
    }

    public void renderError(@Nullable ApiError error) {
        errorTitle.setText(R.string.state_error_title);
        int resId = error == null
                ? R.string.error_unknown
                : com.ikp.dev.app.ui.common.MessageKeys.resIdForKey(error.messageKey);
        errorBody.setText(resId);
        if (error != null && error.code != null && !error.code.startsWith("NET-")) {
            errorCode.setText(getContext().getString(R.string.error_code_suffix, error.code));
            errorCode.setVisibility(VISIBLE);
        } else {
            errorCode.setVisibility(GONE);
        }
        showOnly(R.id.state_error);
    }

    /** Generic driver for {@link UiState}. */
    public <T> void render(UiState<T> state) {
        if (state == null) {
            return;
        }
        switch (state.kind) {
            case LOADING:
                renderLoading();
                break;
            case EMPTY:
                renderEmpty();
                break;
            case ERROR:
                renderError(state.error);
                break;
            case SUCCESS:
            default:
                setVisibility(GONE);
                return;
        }
        setVisibility(VISIBLE);
    }

    private void showOnly(int childId) {
        setVisibility(VISIBLE);
        loading.setVisibility(childId == R.id.state_loading ? VISIBLE : GONE);
        emptyGroup.setVisibility(childId == R.id.state_empty ? VISIBLE : GONE);
        errorGroup.setVisibility(childId == R.id.state_error ? VISIBLE : GONE);
    }
}
