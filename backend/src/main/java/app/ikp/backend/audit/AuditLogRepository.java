package app.ikp.backend.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /**
     * Admin audit filter as a dynamic Specification: nullable-parameter JPQL
     * breaks on PostgreSQL (`could not determine data type of parameter` for
     * null-bound instants), while Specifications emit only matching predicates.
     */
    static Specification<AuditLog> filter(Long actorId, String entityType, String action,
                                          Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (actorId != null) preds.add(cb.equal(root.get("actorId"), actorId));
            if (entityType != null && !entityType.isBlank()) preds.add(cb.equal(root.get("entityType"), entityType));
            if (action != null && !action.isBlank()) preds.add(cb.equal(root.get("action"), action));
            if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
}
