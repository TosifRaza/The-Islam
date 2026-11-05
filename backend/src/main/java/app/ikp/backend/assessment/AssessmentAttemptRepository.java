package app.ikp.backend.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    Optional<AssessmentAttempt> findByPublicId(UUID publicId);

    Page<AssessmentAttempt> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    Page<AssessmentAttempt> findByUserIdAndQuizIdOrderByStartedAtDesc(Long userId, Long quizId, Pageable pageable);
}
