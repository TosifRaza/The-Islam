package app.ikp.backend.storage;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long>, JpaSpecificationExecutor<MediaAsset> {

    Optional<MediaAsset> findByPublicId(UUID publicId);

    /**
     * Dynamic filter Specification — nullable-parameter JPQL breaks on
     * PostgreSQL for null-bound strings (bytea inference), Specifications
     * emit only the predicates that apply.
     */
    static Specification<MediaAsset> filter(String purpose, String scanStatus) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (purpose != null && !purpose.isBlank()) preds.add(cb.equal(root.get("purpose"), purpose));
            if (scanStatus != null && !scanStatus.isBlank()) preds.add(cb.equal(root.get("scanStatus"), scanStatus));
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    @Query("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM MediaAsset m WHERE m.uploadedBy = :userId")
    long quotaUsedBy(@Param("userId") Long userId);
}
