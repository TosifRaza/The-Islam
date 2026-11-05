package com.ikp.dev.app.util;

import androidx.annotation.Nullable;

import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.ui.common.TwoLineAdapter;

import java.util.ArrayList;
import java.util.List;

/** Pure mapping from quiz/deck DTOs to shared two-line rows (JVM-testable). */
public final class QuizRows {

    private QuizRows() { }

    public static List<TwoLineAdapter.Item> catalogue(@Nullable List<AcademyDtos.QuizRowDto> quizzes) {
        List<TwoLineAdapter.Item> rows = new ArrayList<>();
        if (quizzes == null) return rows;
        for (AcademyDtos.QuizRowDto q : quizzes) {
            StringBuilder sub = new StringBuilder();
            if (q.questionCount != null) sub.append(q.questionCount).append(" questions");
            if (q.timeLimitSeconds != null) {
                sub.append(sub.length() == 0 ? "" : " · ").append(q.timeLimitSeconds).append("s limit");
            }
            rows.add(new TwoLineAdapter.ItemImpl(q.title, sub.toString(), null));
        }
        return rows;
    }

    public static List<TwoLineAdapter.Item> decks(@Nullable List<AcademyDtos.DeckRowDto> decks) {
        List<TwoLineAdapter.Item> rows = new ArrayList<>();
        if (decks == null) return rows;
        for (AcademyDtos.DeckRowDto d : decks) {
            rows.add(new TwoLineAdapter.ItemImpl(d.title,
                    (d.cardCount == null ? 0 : d.cardCount) + " cards", d.ownerType));
        }
        return rows;
    }
}
