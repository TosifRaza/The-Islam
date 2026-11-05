package app.ikp.backend.book;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByPublicId(UUID publicId);

    List<Book> findByStatus(Book.Status status);

    /**
     * Admin/public list filter as a dynamic Specification. A nullable-parameter
     * JPQL query breaks on PostgreSQL when Hibernate binds nulls as bytea
     * ("could not determine data type of parameter" / "lower(bytea)"), so
     * predicates are emitted only for the filters actually supplied.
     */
    static Specification<Book> filter(String q, String subject, String language,
                                      Book.Status status, String format, UUID author) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                preds.add(cb.like(cb.lower(root.get("title")), "%" + q.trim().toLowerCase() + "%"));
            }
            if (subject != null && !subject.isBlank()) {
                preds.add(cb.equal(root.get("subject").get("slug"), subject));
            }
            if (language != null && !language.isBlank()) {
                preds.add(cb.equal(root.get("language"), language));
            }
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (format != null && !format.isBlank()) {
                preds.add(cb.equal(root.join("files").get("format"), format));
                query.distinct(true);
            }
            if (author != null) {
                preds.add(cb.equal(root.join("authors").get("author").get("publicId"), author));
                query.distinct(true);
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
