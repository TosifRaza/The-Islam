package app.ikp.backend.subject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findBySlug(String slug);

    List<Subject> findAllByOrderBySortOrderAsc();

    List<Subject> findByStatusNotOrderBySortOrderAsc(String excludedStatus);

    boolean existsByStatus(String status);

}
