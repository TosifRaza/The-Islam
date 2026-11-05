package app.ikp.backend.academy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademyChapterRepository extends JpaRepository<AcademyChapter, Long> {

    Optional<AcademyChapter> findByPublicId(UUID publicId);

    List<AcademyChapter> findByCourseIdOrderBySortOrderAsc(Long courseId);
}
