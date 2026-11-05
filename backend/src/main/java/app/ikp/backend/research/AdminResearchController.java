package app.ikp.backend.research;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M5 Research admin CMS (spec Module 11) — content.edit (research_editor scope). */
@RestController
@RequestMapping("/api/v1/admin/research")
@Tag(name = "admin-research")
public class AdminResearchController {

    private final ResearchService research;

    public AdminResearchController(ResearchService research) { this.research = research; }

    public record CitationBody(String citationKind, Integer surahId, Integer ayahNumber, Long hadithId,
                               java.util.UUID bookPublicId, java.util.UUID articlePublicId,
                               String externalRef, String quote, String note, Integer sortOrder) { }

    public record ArticleRequest(
            @NotBlank @Size(max = 60) String topicSlug,
            @NotBlank @Size(max = 400) String title,
            @Size(max = 400) String titleAr,
            @Size(max = 1000) String summary,
            @NotBlank String body,
            @Size(max = 8) String language,
            @Size(max = 200) String authorDisplayName,
            @NotBlank String sourceTier,
            @Valid List<CitationBody> citations) { }

    private static ResearchService.CitationInput toInput(CitationBody b) {
        return new ResearchService.CitationInput(b.citationKind(), b.surahId(), b.ayahNumber(), b.hadithId(),
                b.bookPublicId(), b.articlePublicId(), b.externalRef(), b.quote(), b.note(), b.sortOrder());
    }

    @PostMapping("/articles")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a research article (draft) with verified citations")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ArticleRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        ResearchArticle a = research.createArticle(request.topicSlug(), request.title(), request.titleAr(),
                request.summary(), request.body(), request.language(), request.authorDisplayName(),
                ResearchArticle.SourceTier.valueOf(request.sourceTier()),
                request.citations() == null ? List.of()
                        : request.citations().stream().map(AdminResearchController::toInput).toList(),
                actor);
        return ResponseEntity.status(201).body(Pages.envelope(research.articleView(a, true)));
    }

    @GetMapping("/articles")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Article management list")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<ResearchArticle> result = research.adminList(status, page, size);
        return ResponseEntity.ok(Pages.envelope(result, research::articleRow));
    }

    @GetMapping("/articles/{articleId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Article detail with citations")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID articleId) {
        return ResponseEntity.ok(Pages.envelope(research.articleView(research.article(articleId), true)));
    }

    @PutMapping("/articles/{articleId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update a draft article (citations re-verified)")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID articleId,
                                                      @Valid @RequestBody ArticleRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        ResearchArticle a = research.updateArticle(articleId, request.topicSlug(), request.title(),
                request.titleAr(), request.summary(), request.body(), request.language(),
                request.authorDisplayName(), ResearchArticle.SourceTier.valueOf(request.sourceTier()),
                request.citations() == null ? List.of()
                        : request.citations().stream().map(AdminResearchController::toInput).toList(),
                actor);
        return ResponseEntity.ok(Pages.envelope(research.articleView(a, true)));
    }

    @PostMapping("/articles/{articleId}/{action}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Workflow: submit-review | approve-editorial | approve | reject | publish | unpublish | archive")
    @PreAuthorize("hasAuthority('content.edit') or hasRole('SCHOLAR_REVIEWER') or hasAuthority('content.publish')")
    public ResponseEntity<Map<String, Object>> transition(@PathVariable UUID articleId,
                                                          @PathVariable String action,
                                                          @RequestBody(required = false) Map<String, String> body,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        String notes = body == null ? null : body.get("notes");
        return ResponseEntity.ok(Pages.envelope(research.transition(articleId, action, notes, actor)));
    }

    @GetMapping("/topics")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Research topic taxonomy (fixed by spec)")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> topics() {
        return ResponseEntity.ok(Pages.envelope(research.activeTopics().stream()
                .map(t -> Map.of("slug", t.getSlug(), "name", t.getName(),
                        "nameAr", t.getNameAr() == null ? "" : t.getNameAr()))
                .toList()));
    }
}
