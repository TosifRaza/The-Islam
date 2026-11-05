package app.ikp.backend.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthorRepository extends JpaRepository<Author, Long> {
    Optional<Author> findByPublicId(UUID publicId);
}
