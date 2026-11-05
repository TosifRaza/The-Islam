package app.ikp.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Long> {

    Optional<AssessmentQuestion> findByPublicId(UUID publicId);

    List<AssessmentQuestion> findByQuizIdOrderBySortOrderAsc(Long quizId);

    long countByQuizIdAndReviewStateNot(Long quizId, AssessmentQuestion.ReviewState state);
}
