package com.ikp.dev.app.ui.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;

import java.util.ArrayList;
import java.util.List;

public final class SuggestedQuestionAdapter extends RecyclerView.Adapter<SuggestedQuestionAdapter.ViewHolder> {

    public interface OnQuestionClickListener {
        void onQuestionClick(@NonNull AssistantModels.SuggestedQuestion question);
    }

    private final List<AssistantModels.SuggestedQuestion> items = new ArrayList<>();
    private final OnQuestionClickListener listener;

    public SuggestedQuestionAdapter(@NonNull OnQuestionClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AssistantModels.SuggestedQuestion> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assistant_suggestion_chip, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTopic;
        private final TextView tvQuestion;
        private final TextView tvPreview;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTopic = itemView.findViewById(R.id.tv_sugg_topic);
            tvQuestion = itemView.findViewById(R.id.tv_sugg_question);
            tvPreview = itemView.findViewById(R.id.tv_sugg_preview);
        }

        void bind(AssistantModels.SuggestedQuestion item) {
            tvTopic.setText(item.topic != null ? item.topic : "Research Query");
            tvQuestion.setText(item.question != null ? item.question : "");
            tvPreview.setText(item.preview != null ? item.preview : "");
            itemView.setOnClickListener(v -> listener.onQuestionClick(item));
        }
    }
}
