package app.ikp.backend.user;

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

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Auth-critical loader: roles must be inside the query because the token
     * filter resolves fresh authorities OUTSIDE any persistence session
     * (open-in-view is disabled by design) — lazy loading there crashes with
     * LazyInitializationException and the request would die as a masked 401.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.roles
            WHERE u.id = :id
            """)
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    long countByStatus(String status);

    /**
     * Admin list filter. Built as a dynamic Specification instead of a
     * nullable-parameter @Query: PostgreSQL cannot infer types for null-bound
     * JDBC parameters in `( ? is null or col = ? )` predicates (bytea
     * inference), while Specifications emit only the predicates that apply.
     */
    static Specification<User> filter(String q, String status) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                preds.add(cb.like(cb.lower(root.get("email")), "%" + q.trim().toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                preds.add(cb.equal(root.get("status"), status));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
