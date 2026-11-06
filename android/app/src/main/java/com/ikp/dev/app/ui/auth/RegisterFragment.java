package com.ikp.dev.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.MessageKeys;

/**
 * Registration screen — powered by Supabase GoTrue Auth.
 */
public final class RegisterFragment extends Fragment {

    private AuthViewModel viewModel;
    private TextInputLayout tilName;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Button btnRegister;
    private TextView registerError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        viewModel.resetRegisterState();

        tilName = view.findViewById(R.id.til_name);
        tilEmail = view.findViewById(R.id.til_email);
        tilPassword = view.findViewById(R.id.til_password);
        etName = view.findViewById(R.id.et_name);
        etEmail = view.findViewById(R.id.et_email);
        etPassword = view.findViewById(R.id.et_password);
        btnRegister = view.findViewById(R.id.btn_register);
        registerError = view.findViewById(R.id.register_error);

        btnRegister.setOnClickListener(v -> submit());
        view.findViewById(R.id.btn_go_login).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        viewModel.registerState().observe(getViewLifecycleOwner(), this::render);
    }

    private void submit() {
        tilEmail.setError(null);
        tilPassword.setError(null);
        registerError.setVisibility(View.GONE);

        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        if (!AuthViewModel.isValidEmail(email)) {
            tilEmail.setError(getString(R.string.auth_invalid_email));
            return;
        }
        if (!AuthViewModel.isValidPassword(password)) {
            tilPassword.setError(getString(R.string.auth_invalid_password));
            return;
        }
        viewModel.register(email, password, name);
    }

    private void render(UiState<Models.User> state) {
        if (state == null) {
            return;
        }
        switch (state.kind) {
            case LOADING:
                btnRegister.setEnabled(false);
                btnRegister.setText(R.string.register_loading);
                registerError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                btnRegister.setEnabled(true);
                btnRegister.setText(R.string.register_button);
                if (state.data != null) {
                    if (ServiceLocator.sessionManager().isSignedIn()) {
                        Toast.makeText(requireContext(), "Account created successfully!", Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(this).navigate(R.id.action_register_to_home);
                    } else {
                        Toast.makeText(requireContext(), R.string.account_created_notice, Toast.LENGTH_LONG).show();
                        NavHostFragment.findNavController(this).popBackStack();
                    }
                }
                break;
            case ERROR:
                btnRegister.setEnabled(true);
                btnRegister.setText(R.string.register_button);
                int resId = MessageKeys.resIdForKey(
                        state.error == null ? "error_unknown" : state.error.messageKey);
                registerError.setText(resId);
                registerError.setVisibility(View.VISIBLE);
                break;
            default:
                btnRegister.setEnabled(true);
                btnRegister.setText(R.string.register_button);
        }
    }
}
