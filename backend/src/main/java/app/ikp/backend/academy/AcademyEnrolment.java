package app.ikp.backend.academy;

import app.ikp.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** A user's enrolment in a course. Ownership is server-side: user_id is part of the key. */
@Entity
@Table(name = "academy_enrolments")
@IdClass(AcademyEnrolment.PK.class)
public class AcademyEnrolment {

    public static final class PK implements Serializable {
        public Long userId;
        public Long courseId;

        public PK() { }
        public PK(Long userId, Long courseId) { this.userId = userId; this.courseId = courseId; }

        @Override public boolean equals(Object o) {
            return o instanceof PK other && Objects.equals(userId, other.userId) && Objects.equals(courseId, other.courseId);
        }
        @Override public int hashCode() { return Objects.hash(userId, courseId); }
    }

    public enum Status { active, completed }

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.active;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private AcademyCourse course;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AcademyEnrolment() { }
    public AcademyEnrolment(Long userId, Long courseId) {
        this.userId = userId;
        this.courseId = courseId;
        this.enrolledAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public Long getCourseId() { return courseId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getUser() { return user; }
    public AcademyCourse getCourse() { return course; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
