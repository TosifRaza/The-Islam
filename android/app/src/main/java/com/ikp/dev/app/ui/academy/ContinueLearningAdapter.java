package com.ikp.dev.app.ui.academy;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ContinueLearningAdapter extends RecyclerView.Adapter<ContinueLearningAdapter.ViewHolder> {

    public interface OnResumeClickListener {
        void onResumeClick(@NonNull AcademyDtos.ContinueDto item);
    }

    private final List<AcademyDtos.ContinueDto> items = new ArrayList<>();
    private final OnResumeClickListener listener;

    public ContinueLearningAdapter(@Nullable OnResumeClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AcademyDtos.ContinueDto> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_continue_learning, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AcademyDtos.ContinueDto c = items.get(position);
        holder.courseTitleView.setText(c.title != null ? c.title : "");

        if (c.nextLessonTitle != null && !c.nextLessonTitle.isEmpty()) {
            holder.lessonTitleView.setVisibility(View.VISIBLE);
            holder.lessonTitleView.setText("Next: " + c.nextLessonTitle);
        } else {
            holder.lessonTitleView.setVisibility(View.GONE);
        }

        int pct = c.percent != null ? c.percent : 0;
        holder.progressBar.setProgress(pct);
        holder.progressPctView.setText(String.format(Locale.US, "%d%%", pct));

        View.OnClickListener click = v -> {
            if (listener != null) {
                listener.onResumeClick(c);
            }
        };

        holder.itemView.setOnClickListener(click);
        holder.resumeBtn.setOnClickListener(click);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView courseTitleView;
        final TextView lessonTitleView;
        final ProgressBar progressBar;
        final TextView progressPctView;
        final ImageButton resumeBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            courseTitleView = itemView.findViewById(R.id.tv_cont_course_title);
            lessonTitleView = itemView.findViewById(R.id.tv_cont_lesson_title);
            progressBar = itemView.findViewById(R.id.pb_cont_progress);
            progressPctView = itemView.findViewById(R.id.tv_cont_progress_pct);
            resumeBtn = itemView.findViewById(R.id.btn_cont_resume);
        }
    }
}
