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
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;

/**
 * Honest "capability not yet offered by the backend" screen used for
 * registration and password reset (M3 API integration report: no
 * /auth/register and no /auth/password endpoints exist). Shows a clear,
 * truthful explanation — no fake forms that pretend to work.
 */
public final class UnavailableFragment extends Fragment {

    private static final String ARG_MODE = "mode";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_unavailable, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView title = view.findViewById(R.id.unavailable_title);
        TextView body = view.findViewById(R.id.unavailable_body);
        Button back = view.findViewById(R.id.btn_back_login);

        Bundle args = getArguments();
        String mode = args == null ? "register" : args.getString(ARG_MODE, "register");
        if ("forgot".equals(mode)) {
            title.setText(R.string.forgot_unavailable_title);
            body.setText(R.string.forgot_unavailable_body);
        } else {
            title.setText(R.string.register_unavailable_title);
            body.setText(R.string.register_unavailable_body);
        }

        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
    }
}
