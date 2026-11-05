package app.ikp.backend.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentQuizRepository extends JpaRepository<AssessmentQuiz, Long> {

    Optional<AssessmentQuiz> findByPublicId(UUID publicId);

    Page<AssessmentQuiz> findByStatusOrderByUpdatedAtDesc(AssessmentQuiz.Status status, Pageable pageable);

    List<AssessmentQuiz> findByStatusAndCourseIdOrderByCreatedAtAsc(AssessmentQuiz.Status status, Long courseId);

    List<AssessmentQuiz> findByStatusAndSubjectSlugOrderByCreatedAtAsc(AssessmentQuiz.Status status, String subjectSlug);

    long countByStatus(AssessmentQuiz.Status status);
}
