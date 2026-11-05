package app.ikp.backend.research;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ResearchArticleRepository extends JpaRepository<ResearchArticle, Long>, JpaSpecificationExecutor<ResearchArticle> {

    Optional<ResearchArticle> findByPublicId(UUID publicId);

    Page<ResearchArticle> findByStatusOrderByUpdatedAtDesc(ResearchArticle.Status status, Pageable pageable);

    Page<ResearchArticle> findByStatusOrderByPublishedAtDesc(ResearchArticle.Status status, Pageable pageable);

    Page<ResearchArticle> findByTopicSlugAndStatusOrderByPublishedAtDesc(String topicSlug,
                                                                         ResearchArticle.Status status,
                                                                         Pageable pageable);

    long countByStatus(ResearchArticle.Status status);
}
