package app.ikp.backend.academy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademyCourseRepository extends JpaRepository<AcademyCourse, Long> {

    Optional<AcademyCourse> findByPublicId(UUID publicId);

    Page<AcademyCourse> findByStatusOrderByUpdatedAtDesc(AcademyCourse.Status status, Pageable pageable);

    Page<AcademyCourse> findByStatusInOrderByUpdatedAtDesc(List<AcademyCourse.Status> statuses, Pageable pageable);
}
