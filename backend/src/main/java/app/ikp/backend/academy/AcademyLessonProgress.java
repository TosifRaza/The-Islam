package app.ikp.backend.academy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Per-lesson completion marker for the enroled user. */
@Entity
@Table(name = "academy_lesson_progress")
@IdClass(AcademyLessonProgress.PK.class)
public class AcademyLessonProgress {

    public static final class PK implements Serializable {
        public Long userId;
        public Long lessonId;

        public PK() { }
        public PK(Long userId, Long lessonId) { this.userId = userId; this.lessonId = lessonId; }

        @Override public boolean equals(Object o) {
            return o instanceof PK other && Objects.equals(userId, other.userId) && Objects.equals(lessonId, other.lessonId);
        }
        @Override public int hashCode() { return Objects.hash(userId, lessonId); }
    }

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AcademyLessonProgress() { }
    public AcademyLessonProgress(Long userId, Long lessonId) {
        this.userId = userId;
        this.lessonId = lessonId;
        this.completedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public Long getLessonId() { return lessonId; }
    public Instant getCompletedAt() { return completedAt; }
}
