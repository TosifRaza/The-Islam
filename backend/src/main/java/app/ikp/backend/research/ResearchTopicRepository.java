package app.ikp.backend.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResearchTopicRepository extends JpaRepository<ResearchTopic, Long> {

    List<ResearchTopic> findByStatusOrderBySortOrderAsc(String status);

    Optional<ResearchTopic> findBySlug(String slug);
}
