package app.ikp.backend.academy;

import app.ikp.backend.academy.dto.AcademyDtos;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public + member Academy surface (spec Module 10 user features). Only
 * published courses are visible — every read path filters on the server.
 * Enrolment/progress endpoints are ownership-scoped (user_id from the token,
 * never from the client).
 */
@RestController
@RequestMapping("/api/v1/academy")
@Tag(name = "academy")
public class PublicAcademyController {

    private final AcademyService academy;

    public PublicAcademyController(AcademyService academy) { this.academy = academy; }

    @GetMapping("/courses")
    @Operation(summary = "Browse published courses")
    public ResponseEntity<Map<String, Object>> courses(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Page<AcademyCourse> result = academy.publicBrowse(page, size);
        return ResponseEntity.ok(Pages.envelope(result, academy::toPublicRow));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "Published course detail with chapters and lessons")
    public ResponseEntity<Map<String, Object>> course(@PathVariable UUID courseId) {
        AcademyCourse c = academy.course(courseId);
        if (c.getStatus() != AcademyCourse.Status.published) {
            throw app.ikp.backend.common.ApiException.notFound("course not found");
        }
        return ResponseEntity.ok(Pages.envelope(academy.toPublicDetail(c)));
    }

    @PostMapping("/courses/{courseId}/enrol")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Enrol in a published course (idempotent)")
    public ResponseEntity<Map<String, Object>> enrol(@PathVariable UUID courseId,
                                                     @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.status(201).body(Pages.envelope(academy.enrol(courseId, actor.id())));
    }

    @PostMapping("/lessons/{lessonId}/complete")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Mark a lesson complete (auto-enrols; returns course progress)")
    public ResponseEntity<Map<String, Object>> complete(@PathVariable UUID lessonId,
                                                        @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(academy.completeLesson(lessonId, actor.id())));
    }

    @GetMapping("/courses/{courseId}/progress")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Own progress in a course")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable UUID courseId,
                                                        @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(academy.progressOf(courseId, actor.id())));
    }

    @GetMapping("/continue")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Continue-learning rail (active enrolments with next lesson)")
    public ResponseEntity<Map<String, Object>> continueLearning(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        List<AcademyDtos.ContinueCourse> data = academy.continueLearning(actor.id(), limit);
        return ResponseEntity.ok(Pages.envelope(data));
    }
}
