package app.ikp.backend.fatwa;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import app.ikp.backend.scholars.Scholar;
import app.ikp.backend.scholars.ScholarRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public Fatwa directory (spec Module 14). OD-08 honoured structurally: the
 * ONLY rows reachable here are state='published' Fatwas — i.e. ones that
 * passed the scholar approval gate (FatwaService). Draft/review/approved
 * states are invisible to this controller; there is no AI-generated
 * auto-publication path anywhere in the backend.
 */
@RestController
@RequestMapping("/api/v1/fatwas")
@Tag(name = "fatwas")
public class FatwaPublicController {

    private final FatwaRepository fatwas;
    private final ScholarRepository scholars;
    private final JdbcTemplate jdbc;

    public FatwaPublicController(FatwaRepository fatwas, ScholarRepository scholars, JdbcTemplate jdbc) {
        this.fatwas = fatwas;
        this.scholars = scholars;
        this.jdbc = jdbc;
    }

    @GetMapping
    @Operation(summary = "Published Fatwa directory (search + category browse)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<Fatwa> result;
        if (q != null && !q.isBlank()) {
            result = fatwas.findByStateAndQuestionContainingIgnoreCase(Fatwa.State.published, q.trim(), pr);
        } else if (category != null && !category.isBlank()) {
            result = fatwas.findByStateAndCategory(Fatwa.State.published, category, pr);
        } else {
            result = fatwas.findByState(Fatwa.State.published, pr);
        }
        return ResponseEntity.ok(Pages.envelope(result, this::row));
    }

    @GetMapping("/{fatwaId}")
    @Operation(summary = "Published Fatwa with source references, reviewing scholar and dates")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID fatwaId) {
        Fatwa f = fatwas.findByPublicId(fatwaId).orElseThrow(() -> ApiException.notFound("fatwa not found"));
        if (f.getState() != Fatwa.State.published) throw ApiException.notFound("fatwa not found");
        Map<String, Object> body = row(f);
        body.put("answer", f.getAnswer());
        body.put("sourceRefs", f.getSourceRefs());
        // Scholar information + review date (Module 14): resolved from the recorded
        // scholarly approval audit trail — never claimed without a record.
        Map<String, Object> review = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.reviewer_id AS reviewer_id, r.decided_at AS decided_at, r.notes AS notes
                FROM content_review_records r
                WHERE r.entity_type = 'fatwa' AND r.entity_id = ?
                  AND r.stage = 'scholarly_review' AND r.decision = 'approved'
                ORDER BY r.decided_at DESC LIMIT 1
                """, f.getId());
        if (!rows.isEmpty()) {
            Map<String, Object> r0 = rows.get(0);
            review.put("reviewDate", String.valueOf(r0.get("decided_at")));
            review.put("reviewNotes", r0.get("notes"));
            Long reviewerId = r0.get("reviewer_id") == null ? null : ((Number) r0.get("reviewer_id")).longValue();
            Scholar scholar = reviewerId == null ? null
                    : scholars.findByLinkedUserId(reviewerId).orElse(null);
            if (scholar != null) {
                review.put("scholar", Map.of(
                        "scholarId", scholar.getPublicId().toString(),
                        "name", scholar.getName(),
                        "verificationStatus", scholar.getVerificationStatus().name()));
            }
        }
        body.put("review", review);
        return ResponseEntity.ok(Pages.envelope(body));
    }

    private Map<String, Object> row(Fatwa f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fatwaId", f.getPublicId().toString());
        m.put("question", f.getQuestion());
        m.put("category", f.getCategory());
        m.put("language", f.getLanguage());
        m.put("publicationDate", f.getPublishedAt() == null ? null : String.valueOf(f.getPublishedAt()));
        return m;
    }
}
