package app.ikp.backend.fatwa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FatwaRepository extends JpaRepository<Fatwa, Long> {

    Optional<Fatwa> findByPublicId(UUID publicId);

    List<Fatwa> findByState(Fatwa.State state);

    Page<Fatwa> findByState(Fatwa.State state, Pageable pageable);

    Page<Fatwa> findByStateAndQuestionContainingIgnoreCase(Fatwa.State state, String question, Pageable pageable);

    Page<Fatwa> findByStateAndCategory(Fatwa.State state, String category, Pageable pageable);
}
