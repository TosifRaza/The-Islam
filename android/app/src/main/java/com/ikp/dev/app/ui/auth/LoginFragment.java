package com.ikp.dev.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.MessageKeys;

/**
 * Login screen — integrated with the REAL backend (POST /api/v1/auth/login).
 * Device name is optional and sent through to the backend session registry.
 * Passwords are never stored; tokens land in the Keystore-backed store.
 */
public final class LoginFragment extends Fragment {

    private AuthViewModel viewModel;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etDevice;
    private Button btnLogin;
    private TextView loginError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        tilEmail = view.findViewById(R.id.til_email);
        tilPassword = view.findViewById(R.id.til_password);
        etEmail = view.findViewById(R.id.et_email);
        etPassword = view.findViewById(R.id.et_password);
        etDevice = view.findViewById(R.id.et_device);
        btnLogin = view.findViewById(R.id.btn_login);
        loginError = view.findViewById(R.id.login_error);

        btnLogin.setOnClickListener(v -> submit());

        View btnGuest = view.findViewById(R.id.btn_guest);
        if (btnGuest != null) {
            btnGuest.setOnClickListener(v -> {
                ServiceLocator.sessionManager().onGuest();
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_home);
            });
        }

        view.findViewById(R.id.btn_forgot).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_forgot));
        view.findViewById(R.id.btn_go_register).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_register));

        viewModel.loginState().observe(getViewLifecycleOwner(), this::render);
    }

    private void submit() {
        tilEmail.setError(null);
        tilPassword.setError(null);
        loginError.setVisibility(View.GONE);

        String email = etEmail.getText() == null ? "" : etEmail.getText().toString();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
        String device = etDevice.getText() == null ? "" : etDevice.getText().toString();

        if (!AuthViewModel.isValidEmail(email)) {
            tilEmail.setError(getString(R.string.auth_invalid_email));
            return;
        }
        if (!AuthViewModel.isValidPassword(password)) {
            tilPassword.setError(getString(R.string.auth_invalid_password));
            return;
        }
        viewModel.login(email, password, device);
    }

    private void render(UiState<com.ikp.dev.app.domain.model.Models.User> state) {
        if (state == null) {
            return;
        }
        switch (state.kind) {
            case LOADING:
                btnLogin.setEnabled(false);
                btnLogin.setText(R.string.login_loading);
                loginError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                if (state.data != null) {
                    viewModel.refreshProfile();
                    NavHostFragment.findNavController(this).navigate(R.id.action_login_to_home);
                } else {
                    // Initial idle state (success with null payload)
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.login_button);
                }
                break;
            case ERROR:
                btnLogin.setEnabled(true);
                btnLogin.setText(R.string.login_button);
                if (state.error != null && state.error.rawDetail != null && !state.error.rawDetail.isEmpty()) {
                    loginError.setText(state.error.rawDetail);
                } else {
                    int resId = MessageKeys.resIdForKey(
                            state.error == null ? "error_unknown" : state.error.messageKey);
                    loginError.setText(resId);
                }
                loginError.setVisibility(View.VISIBLE);
                break;
            default:
                btnLogin.setEnabled(true);
                btnLogin.setText(R.string.login_button);
        }
    }
}
