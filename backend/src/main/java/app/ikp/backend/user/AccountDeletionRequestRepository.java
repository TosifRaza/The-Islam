package app.ikp.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {
    List<AccountDeletionRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
