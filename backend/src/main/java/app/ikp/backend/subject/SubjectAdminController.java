package app.ikp.backend.subject;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.audit.AuditService;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import app.ikp.backend.subject.dto.SubjectView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin subject management (approved spec §3.4.3–3.4.9). Auth per Permission
 * Matrix: Super Admin Full, Admin Edit, Content Manager Full, Academy Manager
 * Full (academy.manage). All writes audited.
 */
@RestController
@RequestMapping("/api/v1/admin/subjects")
@Tag(name = "admin-subjects")
public class SubjectAdminController {

    private final SubjectService subjects;
    private final SubjectMapper mapper;
    private final AuditService audit;

    public SubjectAdminController(SubjectService subjects, SubjectMapper mapper, AuditService audit) {
        this.subjects = subjects;
        this.mapper = mapper;
        this.audit = audit;
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "All subjects incl. hidden/reserved (spec §3.4.9)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status) {
        List<SubjectView> all = subjects.allForAdmin().stream().map(mapper::toView)
                .filter(v -> status == null || status.isBlank() || v.status().equals(status))
                .toList();
        return ResponseEntity.ok(Pages.envelope(all,
                new Pages.PageMeta(1, all.size(), all.size(), 1)));
    }

    @PatchMapping("/{slug}")
    @Operation(summary = "Update name/description/image/status (spec §3.4.4)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String slug,
                                                      @RequestBody Map<String, Object> patch,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Subject before = subjects.bySlugOr404Admin(slug);
        Subject after = subjects.update(slug, patch);
        audit.record(actor.id(), "subject.update", "subject", after.getId(), snapshot(before), snapshot(after), null);
        return ResponseEntity.ok(Pages.envelope(mapper.toView(after)));
    }

    @PostMapping("/reorder")
    @Operation(summary = "Set complete display order 1..15 (spec §3.4.5)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> reorder(@RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal SecurityPrincipal actor) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> order = (List<Map<String, Object>>) body.get("order");
        int updated = subjects.reorder(order);
        audit.record(actor.id(), "subject.reorder", "subject", null, null, Map.of("updated", updated), null);
        return ResponseEntity.ok(Pages.envelope(Map.of("updated", updated)));
    }

    @PostMapping("/{slug}/publish")
    @Operation(summary = "Publish subject (spec §3.4.6)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable String slug,
                                                       @AuthenticationPrincipal SecurityPrincipal actor) {
        Subject s = subjects.publish(slug);
        audit.record(actor.id(), "subject.publish", "subject", s.getId(), null, Map.of("status", "active"), null);
        return ResponseEntity.ok(Pages.envelope(mapper.toView(s)));
    }

    @PostMapping("/{slug}/unpublish")
    @Operation(summary = "Unpublish subject (spec §3.4.7)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> unpublish(@PathVariable String slug,
                                                         @AuthenticationPrincipal SecurityPrincipal actor) {
        Subject s = subjects.unpublish(slug);
        audit.record(actor.id(), "subject.unpublish", "subject", s.getId(), null, Map.of("status", "archived"), null);
        return ResponseEntity.ok(Pages.envelope(mapper.toView(s)));
    }

    /** Post + delete are refused by design — the category set is the fixed 15-position client reference. */
    @PostMapping
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> create() {
        throw ApiException.workflow("the approved category set is the fixed 15-position client reference; "
                + "new categories require an owner decision and a forward migration");
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String slug) {
        throw ApiException.workflow("client-reference categories are not deletable; use unpublish for visibility");
    }

    private static Map<String, Object> snapshot(Subject s) {
        return Map.of("name", s.getName(), "description", String.valueOf(s.getDescription()),
                "status", s.getStatus(), "image", String.valueOf(s.getImage()),
                "position", s.getSortOrder());
    }
}
