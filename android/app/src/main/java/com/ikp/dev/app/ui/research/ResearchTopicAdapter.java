package com.ikp.dev.app.ui.research;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.util.ArrayList;
import java.util.List;

public final class ResearchTopicAdapter extends RecyclerView.Adapter<ResearchTopicAdapter.TopicViewHolder> {

    public interface OnTopicClickListener {
        void onTopicClick(@Nullable AcademyDtos.TopicDto topic);
    }

    private final List<AcademyDtos.TopicDto> items = new ArrayList<>();
    private final OnTopicClickListener listener;
    private String selectedSlug = null; // null indicates "All Topics"

    public ResearchTopicAdapter(OnTopicClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AcademyDtos.TopicDto> topics, @Nullable String selectedSlug) {
        this.items.clear();
        this.selectedSlug = selectedSlug;
        if (topics != null) {
            for (AcademyDtos.TopicDto t : topics) {
                if (t.slug != null && (t.slug.equalsIgnoreCase("all") || t.slug.isEmpty())) {
                    continue;
                }
                this.items.add(t);
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedSlug(@Nullable String slug) {
        this.selectedSlug = slug;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_research_topic_chip, parent, false);
        return new TopicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {
        if (position == 0) {
            holder.chip.setText("All Topics");
            boolean isSelected = (selectedSlug == null || selectedSlug.isEmpty() || selectedSlug.equalsIgnoreCase("all"));
            holder.bindSelected(isSelected);
            holder.itemView.setOnClickListener(v -> {
                selectedSlug = null;
                notifyDataSetChanged();
                if (listener != null) listener.onTopicClick(null);
            });
        } else {
            AcademyDtos.TopicDto topic = items.get(position - 1);
            holder.chip.setText(topic.name);
            boolean isSelected = topic.slug != null && topic.slug.equalsIgnoreCase(selectedSlug);
            holder.bindSelected(isSelected);
            holder.itemView.setOnClickListener(v -> {
                selectedSlug = topic.slug;
                notifyDataSetChanged();
                if (listener != null) listener.onTopicClick(topic);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.isEmpty() ? 0 : items.size() + 1; // +1 for "All Topics"
    }

    static final class TopicViewHolder extends RecyclerView.ViewHolder {
        final TextView chip;

        TopicViewHolder(@NonNull View itemView) {
            super(itemView);
            chip = itemView.findViewById(R.id.tv_topic_chip);
        }

        void bindSelected(boolean isSelected) {
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_pill_emerald);
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackgroundResource(R.drawable.bg_category_chip_default);
                chip.setTextColor(Color.parseColor("#3D4B44"));
            }
        }
    }
}
