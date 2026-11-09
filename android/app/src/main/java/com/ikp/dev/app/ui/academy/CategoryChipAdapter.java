package com.ikp.dev.app.ui.academy;

import android.graphics.Color;
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

public final class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ViewHolder> {

    public static final class ChipItem {
        @Nullable public final String slug;
        @NonNull public final String name;

        public ChipItem(@Nullable String slug, @NonNull String name) {
            this.slug = slug;
            this.name = name;
        }
    }

    public interface OnCategorySelectedListener {
        void onCategorySelected(@Nullable String slug, @NonNull String name);
    }

    private final List<ChipItem> items = new ArrayList<>();
    private int selectedPosition = 0;
    private final OnCategorySelectedListener listener;

    public CategoryChipAdapter(@Nullable OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ChipItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        selectedPosition = 0;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChipItem item = items.get(position);
        holder.nameView.setText(item.name);
        boolean isSelected = (position == selectedPosition);

        if (holder.iconView != null) {
            String lower = item.name != null ? item.name.toLowerCase() : "";
            if (position == 0 || "all".equals(lower)) {
                holder.iconView.setVisibility(View.GONE);
            } else {
                holder.iconView.setVisibility(View.VISIBLE);
                if (lower.contains("quran") || lower.contains("tafsir")) {
                    holder.iconView.setImageResource(R.drawable.ic_cat_quran);
                } else if (lower.contains("hadith")) {
                    holder.iconView.setImageResource(R.drawable.ic_cat_hadith);
                } else {
                    holder.iconView.setImageResource(R.drawable.ic_cat_quran);
                }
            }
        }

        if (isSelected) {
            holder.container.setBackgroundResource(R.drawable.bg_category_chip_selected);
            holder.nameView.setTextColor(Color.WHITE);
        } else {
            holder.container.setBackgroundResource(R.drawable.bg_category_chip_default);
            holder.nameView.setTextColor(0xFF12291F);
        }

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (prev != selectedPosition) {
                notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
            }
            if (listener != null) {
                listener.onCategorySelected(item.slug, item.name);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final View container;
        final android.widget.ImageView iconView;
        final TextView nameView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.chip_container);
            iconView = itemView.findViewById(R.id.iv_category_icon);
            nameView = itemView.findViewById(R.id.tv_category_name);
        }
    }
}
