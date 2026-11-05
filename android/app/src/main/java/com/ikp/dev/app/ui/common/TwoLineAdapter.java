package com.ikp.dev.app.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared two-line row adapter for M5 lists (courses, quizzes, decks, articles,
 * scholars, fatwas, attempt history, weak topics). One reusable item layout
 * keeps the M5 screen set small and consistent; screens map their DTOs to
 * {@link Item} (pure mapping — unit-testable without Android).
 */
public final class TwoLineAdapter extends RecyclerView.Adapter<TwoLineAdapter.VH> {

    public interface Item {
        @NonNull
        String title();

        @Nullable
        String subtitle();

        /** Optional short pill text on the row (type / level / tier / score). */
        @Nullable
        String badge();
    }

    public interface OnItemClick {
        void on(Item item, int position);
    }

    public static final class ItemImpl implements Item {
        private final String title;
        private final String subtitle;
        private final String badge;

        public ItemImpl(@NonNull String title, @Nullable String subtitle, @Nullable String badge) {
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
        }

        @NonNull
        @Override public String title() { return title; }

        @Nullable
        @Override public String subtitle() { return subtitle; }

        @Nullable
        @Override public String badge() { return badge; }
    }

    public static final class VH extends RecyclerView.ViewHolder {
        final TextView badge;
        final TextView title;
        final TextView subtitle;

        VH(View v) {
            super(v);
            badge = v.findViewById(R.id.row_badge);
            title = v.findViewById(R.id.row_title);
            subtitle = v.findViewById(R.id.row_subtitle);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final OnItemClick click;

    public TwoLineAdapter(@Nullable OnItemClick click) {
        this.click = click;
    }

    public void submit(List<? extends Item> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    public List<Item> items() {
        return items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_two_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item item = items.get(position);
        h.title.setText(item.title());
        String sub = item.subtitle();
        if (sub == null || sub.isEmpty()) {
            h.subtitle.setVisibility(View.GONE);
        } else {
            h.subtitle.setVisibility(View.VISIBLE);
            h.subtitle.setText(sub);
        }
        String b = item.badge();
        if (b == null || b.isEmpty()) {
            h.badge.setVisibility(View.GONE);
        } else {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(b);
        }
        h.itemView.setOnClickListener(v -> {
            if (click != null) click.on(item, h.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
