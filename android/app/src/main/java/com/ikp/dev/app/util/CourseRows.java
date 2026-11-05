package com.ikp.dev.app.util;

import androidx.annotation.Nullable;

import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.ui.common.TwoLineAdapter;

import java.util.ArrayList;
import java.util.List;

/** Pure mapping from course/continue DTOs to shared two-line rows (JVM-testable). */
public final class CourseRows {

    private CourseRows() { }

    /** Catalogue row: title + "subject · level · N lessons". */
    public static List<TwoLineAdapter.Item> catalogue(@Nullable List<AcademyDtos.CourseRowDto> courses) {
        List<TwoLineAdapter.Item> rows = new ArrayList<>();
        if (courses == null) return rows;
        for (AcademyDtos.CourseRowDto c : courses) {
            StringBuilder sub = new StringBuilder();
            if (c.subject != null && c.subject.name != null) sub.append(c.subject.name);
            if (c.level != null) sub.append(sub.length() == 0 ? "" : " · ").append(c.level);
            if (c.lessonCount != null) {
                sub.append(sub.length() == 0 ? "" : " · ").append(c.lessonCount).append(" lessons");
            }
            rows.add(new TwoLineAdapter.ItemImpl(c.title, sub.toString(), null));
        }
        return rows;
    }

    /** Continue-learning row, or null when the record is not renderable. */
    @Nullable
    public static TwoLineAdapter.Item continueRow(@Nullable AcademyDtos.ContinueDto c) {
        if (c == null || c.title == null || c.title.isEmpty()) return null;
        StringBuilder sub = new StringBuilder();
        if (c.percent != null) sub.append(c.percent).append("%");
        if (c.nextLessonTitle != null && !c.nextLessonTitle.isEmpty()) {
            sub.append(sub.length() == 0 ? "" : " · ").append(c.nextLessonTitle);
        }
        return new TwoLineAdapter.ItemImpl(c.title, sub.toString(), null);
    }
}
