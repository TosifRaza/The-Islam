package app.ikp.backend.fatwa;

import app.ikp.backend.audit.AuditLogRepository;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import app.ikp.backend.fatwa.dto.FatwaRequest;
import app.ikp.backend.user.UserRepository;
import app.ikp.backend.workflow.ContentReviewRecord;
import app.ikp.backend.workflow.ContentReviewRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fatwa admin family per approved spec §3.17 — the workflow that makes Fatwa
 * publication non-bypassable. Role gates mirror the Permission Matrix:
 *   create/submit: fatwa.create (Super Admin, Admin, Scholar Reviewer)
 *   assign-reviewer: Super Admin/Admin; assignee must hold Scholar Reviewer
 *   decision: Scholar Reviewer ONLY (sole authority)
 *   publish/unpublish: fatwa.publish (state-gated, separation of duties)
 */
@RestController
@RequestMapping("/api/v1/admin/fatwas")
@Tag(name = "admin-fatwas")
public class FatwaAdminController {

    private final FatwaService fatwas;
    private final ContentReviewRecordRepository reviewRecords;
    private final AuditLogRepository auditRepo;
    private final UserRepository users;

    public FatwaAdminController(FatwaService fatwas, ContentReviewRecordRepository reviewRecords,
                                AuditLogRepository auditRepo, UserRepository users) {
        this.fatwas = fatwas;
        this.reviewRecords = reviewRecords;
        this.auditRepo = auditRepo;
        this.users = users;
    }

    @PostMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create draft Fatwa (spec §3.17)")
    @PreAuthorize("hasAuthority('fatwa.create')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody FatwaRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.status(201).body(Pages.envelope(toView(fatwas.create(request, actor))));
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Queue with state filter (spec §3.17)")
    @PreAuthorize("hasAnyAuthority('fatwa.create','fatwa.publish','fatwa.review.approve')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String state,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Fatwa.State st = parseState(state);
        List<Fatwa> all = fatwas.byState(st);
        int pageSize = Math.min(Math.max(1, size), 100);
        int current = Math.max(1, page);
        List<Map<String, Object>> items = all.stream()
                .skip((long) (current - 1) * pageSize)
                .limit(pageSize)
                .map(FatwaAdminController::toView)
                .toList();
        return ResponseEntity.ok(Pages.envelope(items, new Pages.PageMeta(current, pageSize, all.size(),
                (int) Math.ceil(all.size() / (double) pageSize))));
    }

    @GetMapping("/{fatwaId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Fatwa detail")
    @PreAuthorize("hasAnyAuthority('fatwa.create','fatwa.publish','fatwa.review.approve')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID fatwaId) {
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.byPublicId(fatwaId))));
    }

    @PatchMapping("/{fatwaId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Edit draft/in_review (edit after approval resets to draft)")
    @PreAuthorize("hasAuthority('fatwa.create')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID fatwaId,
                                                      @Valid @RequestBody FatwaRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.update(fatwaId, request, actor))));
    }

    @PostMapping("/{fatwaId}/submit-review")
    @Operation(summary = "draft → in_review")
    @PreAuthorize("hasAuthority('fatwa.create')")
    public ResponseEntity<Map<String, Object>> submitReview(@PathVariable UUID fatwaId,
                                                            @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.submitReview(fatwaId, actor))));
    }

    @PostMapping("/{fatwaId}/assign-reviewer")
    @Operation(summary = "Assign a Scholar Reviewer (assignee must hold the role)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Map<String, Object>> assignReviewer(@PathVariable UUID fatwaId,
                                                              @RequestBody Map<String, Object> body,
                                                              @AuthenticationPrincipal SecurityPrincipal actor) {
        UUID reviewerId = parseUuid(body, "reviewerUserId");
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.assignReviewer(fatwaId, reviewerId, actor))));
    }

    @PostMapping("/{fatwaId}/decision")
    @Operation(summary = "Record scholarly decision — Scholar Reviewer ONLY")
    @PreAuthorize("hasRole('SCHOLAR_REVIEWER')")
    public ResponseEntity<Map<String, Object>> decision(@PathVariable UUID fatwaId,
                                                        @RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal SecurityPrincipal actor) {
        String decision = String.valueOf(body.get("decision"));
        if (!List.of("approve", "reject").contains(decision)) {
            throw ApiException.validation("decision must be approve or reject");
        }
        String notes = body.get("notes") == null ? null : String.valueOf(body.get("notes"));
        if ("reject".equals(decision) && (notes == null || notes.isBlank())) {
            throw ApiException.validation("notes required on reject");
        }
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.decide(fatwaId, "approve".equals(decision), notes, actor))));
    }

    @PostMapping("/{fatwaId}/publish")
    @Operation(summary = "Publish — state-gated: approved + scholar approval + publisher ≠ reviewer")
    @PreAuthorize("hasAuthority('fatwa.publish')")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable UUID fatwaId,
                                                       @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.publish(fatwaId, actor))));
    }

    @PostMapping("/{fatwaId}/unpublish")
    @Operation(summary = "published → archived (audited)")
    @PreAuthorize("hasAuthority('fatwa.publish')")
    public ResponseEntity<Map<String, Object>> unpublish(@PathVariable UUID fatwaId,
                                                         @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(toView(fatwas.unpublish(fatwaId, actor))));
    }

    @GetMapping("/{fatwaId}/audit")
    @Operation(summary = "Full review/publication audit trail (spec §3.17)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Map<String, Object>> auditTrail(@PathVariable UUID fatwaId) {
        Fatwa f = fatwas.byPublicId(fatwaId);
        List<Map<String, Object>> trail = new java.util.ArrayList<>();
        for (ContentReviewRecord r : reviewRecords
                .findByEntityTypeAndEntityIdOrderByDecidedAtDesc("fatwa", f.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", String.valueOf(r.getDecidedAt()));
            row.put("action", "review." + r.getDecision().name().toLowerCase());
            row.put("actor", publicIdOf(r.getReviewerId()));
            row.put("notes", r.getNotes());
            trail.add(row);
        }
        for (app.ikp.backend.audit.AuditLog a : auditRepo
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("fatwa", f.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", String.valueOf(a.getCreatedAt()));
            row.put("action", a.getAction());
            row.put("actor", a.getActorId() == null ? null : publicIdOf(a.getActorId()));
            trail.add(row);
        }
        return ResponseEntity.ok(Pages.envelope(trail));
    }

    private Fatwa.State parseState(String state) {
        if (state == null || state.isBlank()) return null;
        try {
            return Fatwa.State.valueOf(state);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("unknown state: " + state);
        }
    }

    private static UUID parseUuid(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) throw ApiException.validation(key + " is required");
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(key + " must be a uuid");
        }
    }

    private String publicIdOf(Long internalUserId) {
        return users.findById(internalUserId).map(u -> u.getPublicId().toString()).orElse(null);
    }

    static Map<String, Object> toView(Fatwa f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fatwaId", f.getPublicId());
        m.put("question", f.getQuestion());
        m.put("category", f.getCategory());
        m.put("language", f.getLanguage());
        m.put("state", f.getState().name());
        m.put("version", f.getVersion());
        m.put("createdAt", String.valueOf(f.getCreatedAt()));
        m.put("updatedAt", String.valueOf(f.getUpdatedAt()));
        m.put("publishedAt", f.getPublishedAt() == null ? null : String.valueOf(f.getPublishedAt()));
        if (f.getState() == Fatwa.State.published || f.getState() == Fatwa.State.archived) {
            m.put("answer", f.getAnswer());
        }
        return m;
    }
}
