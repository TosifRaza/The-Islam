package app.ikp.backend.academy;

import app.ikp.backend.academy.dto.AcademyDtos;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** M5 Academy admin CMS (spec Module 10 admin features) — academy.manage. */
@RestController
@RequestMapping("/api/v1/admin/academy")
@Tag(name = "admin-academy")
public class AdminAcademyController {

    private final AcademyService academy;

    public AdminAcademyController(AcademyService academy) { this.academy = academy; }

    @PostMapping("/courses")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a course (draft)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AcademyDtos.CourseRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        AcademyCourse c = academy.createCourse(request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(academy.toAdminDetail(c)));
    }

    @GetMapping("/courses")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Course management list")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<AcademyCourse> result = academy.adminList(status, page, size);
        return ResponseEntity.ok(Pages.envelope(result, academy::toAdminRow));
    }

    @GetMapping("/courses/{courseId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Course detail incl. chapters and lessons")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID courseId) {
        return ResponseEntity.ok(Pages.envelope(academy.toAdminDetail(academy.course(courseId))));
    }

    @PatchMapping("/courses/{courseId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update course metadata (draft only)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID courseId,
                                                      @Valid @RequestBody AcademyDtos.CourseRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(
                academy.toAdminDetail(academy.updateCourse(courseId, request, actor))));
    }

    @PostMapping("/courses/{courseId}/chapters")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Add a chapter (draft only)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> addChapter(@PathVariable UUID courseId,
                                                          @Valid @RequestBody AcademyDtos.ChapterRequest request,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        AcademyChapter ch = academy.addChapter(courseId, request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(Map.of(
                "chapterId", ch.getPublicId(), "title", ch.getTitle(), "sortOrder", ch.getSortOrder())));
    }

    @PatchMapping("/chapters/{chapterId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update a chapter (draft only)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> updateChapter(@PathVariable UUID chapterId,
                                                             @Valid @RequestBody AcademyDtos.ChapterRequest request,
                                                             @AuthenticationPrincipal SecurityPrincipal actor) {
        AcademyChapter ch = academy.updateChapter(chapterId, request, actor);
        return ResponseEntity.ok(Pages.envelope(Map.of(
                "chapterId", ch.getPublicId(), "title", ch.getTitle(), "sortOrder", ch.getSortOrder())));
    }

    @PostMapping("/chapters/{chapterId}/lessons")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Add a lesson (draft only) — text|video|pdf|quiz|flashcards")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> addLesson(@PathVariable UUID chapterId,
                                                         @Valid @RequestBody AcademyDtos.LessonRequest request,
                                                         @AuthenticationPrincipal SecurityPrincipal actor) {
        AcademyLesson l = academy.addLesson(chapterId, request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(academy.toAdminLesson(l)));
    }

    @PatchMapping("/lessons/{lessonId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update a lesson (draft only)")
    @PreAuthorize("hasAuthority('academy.manage')")
    public ResponseEntity<Map<String, Object>> updateLesson(@PathVariable UUID lessonId,
                                                            @Valid @RequestBody AcademyDtos.LessonRequest request,
                                                            @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(academy.toAdminLesson(
                academy.updateLesson(lessonId, request, actor))));
    }

    @PostMapping("/courses/{courseId}/{action}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Workflow: submit-review | approve-editorial | reject | publish | unpublish | archive")
    @PreAuthorize("hasAuthority('academy.manage') or hasAuthority('content.publish')")
    public ResponseEntity<Map<String, Object>> transition(@PathVariable UUID courseId,
                                                          @PathVariable String action,
                                                          @RequestBody(required = false) Map<String, String> body,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        String notes = body == null ? null : body.get("notes");
        return ResponseEntity.ok(Pages.envelope(academy.transition(courseId, action, notes, actor)));
    }
}
