package com.ikp.dev.app.ui.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;

/**
 * Onboarding pages (introduction + permission introduction screen, prompt §7).
 * Pure view content — no network, no fabrication.
 */
final class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.Holder> {

    private static final int[] TITLES = {
            R.string.onboarding_title_1,
            R.string.onboarding_title_2,
            R.string.onboarding_title_3,
            R.string.onboarding_permissions_title,
    };
    private static final int[] BODIES = {
            R.string.onboarding_body_1,
            R.string.onboarding_body_2,
            R.string.onboarding_body_3,
            R.string.onboarding_permissions_body,
    };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_page, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.title.setText(TITLES[position]);
        holder.body.setText(BODIES[position]);
    }

    @Override
    public int getItemCount() {
        return TITLES.length;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView body;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.onboarding_title);
            body = itemView.findViewById(R.id.onboarding_body);
        }
    }
}
