package app.ikp.backend.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchArticleCitationRepository extends JpaRepository<ResearchArticleCitation, Long> {

    List<ResearchArticleCitation> findByArticleIdOrderBySortOrderAsc(Long articleId);
}
