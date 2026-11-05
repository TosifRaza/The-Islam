package app.ikp.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

    List<RefreshToken> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Session revocation: mark every active family row revoked (immediate effect). */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    void revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
