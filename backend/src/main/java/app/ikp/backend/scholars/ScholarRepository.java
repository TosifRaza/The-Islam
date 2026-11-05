package app.ikp.backend.scholars;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScholarRepository extends JpaRepository<Scholar, Long> {

    Optional<Scholar> findByPublicId(UUID publicId);

    Page<Scholar> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<Scholar> findByVerificationStatusOrderByUpdatedAtDesc(Scholar.VerificationStatus status);

    Page<Scholar> findByVerificationStatusOrderByUpdatedAtDesc(Scholar.VerificationStatus status, Pageable pageable);

    Optional<Scholar> findByLinkedUserId(Long userId);
}
