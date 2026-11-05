package app.ikp.backend.scholars;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
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

import java.util.Map;
import java.util.UUID;

/** M5 scholar directory admin (spec Module 14) — scholars.manage. */
@RestController
@RequestMapping("/api/v1/admin/scholars")
@Tag(name = "admin-scholars")
public class AdminScholarController {

    private final ScholarService service;

    public AdminScholarController(ScholarService service) { this.service = service; }

    public record ScholarRequest(@NotBlank @Size(max = 200) String name,
                                 @Size(max = 200) String nameAr,
                                 String biography,
                                 @Size(max = 1000) String qualifications,
                                 @Size(max = 500) String areasOfExpertise,
                                 UUID photoMediaId,
                                 UUID linkedUserPublicId) { }

    public record VerifyRequest(String status) { }

    @PostMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a scholar profile (unverified)")
    @PreAuthorize("hasAuthority('scholars.manage')")
    public ResponseEntity<Map<String, Object>> create(@jakarta.validation.Valid @RequestBody ScholarRequest r,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Scholar s = service.create(r.name(), r.nameAr(), r.biography(), r.qualifications(),
                r.areasOfExpertise(), r.photoMediaId(), r.linkedUserPublicId(), actor);
        return ResponseEntity.status(201).body(Pages.envelope(service.view(s, true)));
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Scholar management list")
    @PreAuthorize("hasAuthority('scholars.manage')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<Scholar> result = service.adminList(page, size);
        return ResponseEntity.ok(Pages.envelope(result, s -> service.view(s, true)));
    }

    @GetMapping("/{scholarId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Scholar detail (admin view)")
    @PreAuthorize("hasAuthority('scholars.manage')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID scholarId) {
        return ResponseEntity.ok(Pages.envelope(service.view(service.scholar(scholarId), true)));
    }

    @PutMapping("/{scholarId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update scholar profile")
    @PreAuthorize("hasAuthority('scholars.manage')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID scholarId,
                                                      @jakarta.validation.Valid @RequestBody ScholarRequest r,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Scholar s = service.update(scholarId, r.name(), r.nameAr(), r.biography(), r.qualifications(),
                r.areasOfExpertise(), r.photoMediaId(), actor);
        return ResponseEntity.ok(Pages.envelope(service.view(s, true)));
    }

    @PostMapping("/{scholarId}/verify")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Record credential verification (verified|revoked) — audited")
    @PreAuthorize("hasAuthority('scholars.manage')")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable UUID scholarId,
                                                      @jakarta.validation.Valid @RequestBody VerifyRequest r,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Scholar s = service.verify(scholarId, r.status(), actor);
        return ResponseEntity.ok(Pages.envelope(service.view(s, true)));
    }
}
