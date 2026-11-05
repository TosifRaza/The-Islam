package app.ikp.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssessmentAttemptAnswerRepository extends JpaRepository<AssessmentAttemptAnswer, AssessmentAttemptAnswer.PK> {

    List<AssessmentAttemptAnswer> findByAttemptId(Long attemptId);

    /**
     * Flat rows (question id, subject slug or null, correct flag) across the
     * user's submitted attempts. Weak-topic aggregation happens in the service
     * — a nullable-parameter JPQL query breaks on PostgreSQL when Hibernate
     * binds nulls as bytea (same pitfall documented on BookRepository).
     */
    @Query("""
            SELECT a.question.id, q.subject.slug, a.correct
            FROM AssessmentAttemptAnswer a
            JOIN a.attempt t
            JOIN AssessmentQuiz q ON q.id = t.quiz.id
            WHERE t.user.id = :userId
              AND t.status = app.ikp.backend.assessment.AssessmentAttempt.Status.submitted
            """)
    List<Object[]> answeredRows(Long userId);
}
