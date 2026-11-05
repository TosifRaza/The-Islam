package app.ikp.backend.book;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Author management (matrix: books metadata — content.edit). */
@RestController
@RequestMapping("/api/v1/admin/authors")
@Tag(name = "admin-authors")
public class AdminAuthorController {

    public record AuthorRequest(@NotBlank @Size(max = 200) String name,
                                @Size(max = 200) String nameAr,
                                @Size(max = 2000) String bio) {
    }

    private final AuthorRepository authors;
    private final AuditService audit;

    public AdminAuthorController(AuthorRepository authors, AuditService audit) {
        this.authors = authors;
        this.audit = audit;
    }

    public record AuthorView(UUID authorId, String name, String nameAr, String bio) {
    }

    @PostMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create author")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AuthorRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Author a = new Author();
        a.setName(request.name());
        a.setNameAr(request.nameAr());
        a.setBio(request.bio());
        authors.save(a);
        audit.record(actor.id(), "author.create", "author", a.getId(), null,
                Map.of("name", a.getName()), null);
        return ResponseEntity.status(201).body(Pages.envelope(toView(a)));
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "List authors")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> list() {
        List<AuthorView> all = authors.findAll().stream()
                .sorted(java.util.Comparator.comparing(Author::getName))
                .map(AdminAuthorController::toView).toList();
        return ResponseEntity.ok(Pages.envelope(all, new Pages.PageMeta(1, all.size(), all.size(), 1)));
    }

    @PatchMapping("/{authorId}")
    @Operation(summary = "Update author")
    @PreAuthorize("hasAuthority('content.edit')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID authorId,
                                                      @Valid @RequestBody AuthorRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        Author a = authors.findByPublicId(authorId).orElseThrow(() -> ApiException.notFound("author not found"));
        a.setName(request.name());
        a.setNameAr(request.nameAr());
        a.setBio(request.bio());
        audit.record(actor.id(), "author.update", "author", a.getId(), null,
                Map.of("name", a.getName()), null);
        return ResponseEntity.ok(Pages.envelope(toView(a)));
    }

    static AuthorView toView(Author a) {
        return new AuthorView(a.getPublicId(), a.getName(), a.getNameAr(), a.getBio());
    }
}
