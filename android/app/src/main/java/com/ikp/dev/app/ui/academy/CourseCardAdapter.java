package com.ikp.dev.app.ui.academy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CourseCardAdapter extends RecyclerView.Adapter<CourseCardAdapter.ViewHolder> {

    public interface OnCourseClickListener {
        void onCourseClick(@NonNull AcademyDtos.CourseRowDto course);
        void onBookmarkClick(@NonNull AcademyDtos.CourseRowDto course);
    }

    private final List<AcademyDtos.CourseRowDto> items = new ArrayList<>();
    private final OnCourseClickListener listener;

    public CourseCardAdapter(@Nullable OnCourseClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AcademyDtos.CourseRowDto> newItems) {
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
                .inflate(R.layout.item_course_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AcademyDtos.CourseRowDto c = items.get(position);
        Context ctx = holder.itemView.getContext();

        // Subject pill
        String subjectName = c.subject != null && c.subject.name != null && !c.subject.name.isEmpty()
                ? c.subject.name : "Islamic Studies";
        holder.subjectView.setText(subjectName);

        // Level badge
        String level = c.level != null && !c.level.isEmpty()
                ? capitalize(c.level) : "General";
        holder.levelView.setText(level);

        // Title and description
        holder.titleView.setText(c.title != null ? c.title : "");
        holder.descView.setText(c.description != null ? c.description : "");

        // Instructor
        String instructor = c.instructorDisplayName != null && !c.instructorDisplayName.isEmpty()
                ? c.instructorDisplayName : "Verified Scholar";
        holder.instructorView.setText(instructor);

        // Meta (lessons + hours)
        int lessons = c.lessonCount != null ? c.lessonCount : 0;
        int hours = c.estimatedHours != null ? c.estimatedHours : 0;
        String meta;
        if (hours > 0 && lessons > 0) {
            meta = String.format(Locale.US, "%d Lessons • %d hrs", lessons, hours);
        } else if (lessons > 0) {
            meta = String.format(Locale.US, "%d Lessons", lessons);
        } else if (hours > 0) {
            meta = String.format(Locale.US, "%d hrs", hours);
        } else {
            meta = "Self-paced";
        }
        holder.metaView.setText(meta);

        // Clicks
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCourseClick(c);
            }
        });

        holder.bookmarkBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(c);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase(Locale.ROOT);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView subjectView;
        final TextView levelView;
        final ImageView bookmarkBtn;
        final TextView titleView;
        final TextView descView;
        final TextView instructorView;
        final TextView metaView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            subjectView = itemView.findViewById(R.id.tv_course_subject);
            levelView = itemView.findViewById(R.id.tv_course_level);
            bookmarkBtn = itemView.findViewById(R.id.btn_course_bookmark);
            titleView = itemView.findViewById(R.id.tv_course_title);
            descView = itemView.findViewById(R.id.tv_course_desc);
            instructorView = itemView.findViewById(R.id.tv_course_instructor);
            metaView = itemView.findViewById(R.id.tv_course_meta);
        }
    }
}
