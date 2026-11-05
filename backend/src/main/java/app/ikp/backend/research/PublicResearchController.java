package app.ikp.backend.research;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Public research surface (spec Module 11). Only published articles; the
 * mandatory three-tier classification (primary/secondary/editorial) is always
 * rendered so readers can tell source classes apart.
 */
@RestController
@RequestMapping("/api/v1/research")
@Tag(name = "research")
public class PublicResearchController {

    private final ResearchService research;

    public PublicResearchController(ResearchService research) { this.research = research; }

    @GetMapping("/topics")
    @Operation(summary = "Research topics")
    public ResponseEntity<Map<String, Object>> topics() {
        return ResponseEntity.ok(Pages.envelope(research.activeTopics().stream()
                .map(t -> Map.of("slug", t.getSlug(), "name", t.getName(),
                        "nameAr", t.getNameAr() == null ? "" : t.getNameAr(),
                        "description", t.getDescription() == null ? "" : t.getDescription()))
                .toList()));
    }

    @GetMapping("/articles")
    @Operation(summary = "Published research articles (filter by topic)")
    public ResponseEntity<Map<String, Object>> articles(@RequestParam(required = false) String topic,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        Page<ResearchArticle> result = research.publicList(topic, null, page, size);
        return ResponseEntity.ok(Pages.envelope(result, research::articleRow));
    }

    @GetMapping("/articles/{articleId}")
    @Operation(summary = "Published article detail with verified citations and tier label")
    public ResponseEntity<Map<String, Object>> article(@PathVariable UUID articleId) {
        ResearchArticle a = research.article(articleId);
        if (a.getStatus() != ResearchArticle.Status.published) {
            throw ApiException.notFound("research article not found");
        }
        return ResponseEntity.ok(Pages.envelope(research.articleView(a, false)));
    }
}
