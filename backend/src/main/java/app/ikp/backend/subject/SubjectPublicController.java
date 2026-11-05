package app.ikp.backend.subject;

import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public subject/category endpoints (approved spec §3.4.1–3.4.2). Stable
 * CAT-* slugs only; position 06 is never returned publicly (OD-03) — enforced
 * by query, not by client filtering.
 */
@RestController
@RequestMapping("/api/v1/subjects")
@Tag(name = "subjects")
public class SubjectPublicController {

    private final SubjectService subjects;
    private final SubjectMapper mapper;

    public SubjectPublicController(SubjectService subjects, SubjectMapper mapper) {
        this.subjects = subjects;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Published categories in display order (spec §3.4.1)")
    public ResponseEntity<Map<String, Object>> list() {
        List<?> all = subjects.publishedForPublic().stream().map(mapper::toView).toList();
        return ResponseEntity.ok(Pages.envelope(all, new Pages.PageMeta(1, all.size(), all.size(), 1)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Category detail (spec §3.4.2; hidden slug → 404)")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(Pages.envelope(mapper.toView(subjects.bySlugOr404(slug))));
    }
}
