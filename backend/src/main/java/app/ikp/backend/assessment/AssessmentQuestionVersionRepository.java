package app.ikp.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentQuestionVersionRepository extends JpaRepository<AssessmentQuestionVersion, Long> {

    List<AssessmentQuestionVersion> findByQuestionIdOrderByVersionDesc(Long questionId);
}
