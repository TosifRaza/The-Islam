package app.ikp.backend.academy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AcademyEnrolmentRepository extends JpaRepository<AcademyEnrolment, AcademyEnrolment.PK> {

    Optional<AcademyEnrolment> findByUserIdAndCourseId(Long userId, Long courseId);

    List<AcademyEnrolment> findByUserIdOrderByEnrolledAtDesc(Long userId);

    @Query("SELECT COUNT(e) FROM AcademyEnrolment e WHERE e.course.id = :courseId")
    long countByCourseId(Long courseId);
}
