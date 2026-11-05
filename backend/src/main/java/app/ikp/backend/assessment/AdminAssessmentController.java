package app.ikp.backend.assessment;

import app.ikp.backend.assessment.dto.AssessmentDtos;
import app.ikp.backend.auth.SecurityPrincipal;
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

/** M5 Assessment admin CMS (spec Module 13 admin features + assessment management). */
@RestController
@RequestMapping("/api/v1/admin/assessments")
@Tag(name = "admin-assessments")
public class AdminAssessmentController {

    private final AssessmentService service;

    public AdminAssessmentController(AssessmentService service) { this.service = service; }

    // ----- quizzes -----

    @PostMapping("/quizzes")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a quiz (draft)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AssessmentDtos.QuizRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        AssessmentQuiz q = service.createQuiz(request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(service.toAdminDetail(q)));
    }

    @GetMapping("/quizzes")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Quiz management list (with approval counts)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<AssessmentQuiz> result = service.adminQuizzes(status, page, size);
        return ResponseEntity.ok(Pages.envelope(result, service::toAdminRow));
    }

    @GetMapping("/quizzes/{quizId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Quiz detail incl. questions (admin view: review states, correct flags)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID quizId) {
        return ResponseEntity.ok(Pages.envelope(service.toAdminDetail(service.quiz(quizId))));
    }

    @PatchMapping("/quizzes/{quizId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update quiz (draft only)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID quizId,
                                                      @Valid @RequestBody AssessmentDtos.QuizRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(
                service.toAdminDetail(service.updateQuiz(quizId, request, actor))));
    }

    @PostMapping("/quizzes/{quizId}/{action}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Workflow: submit-review | approve-editorial | reject | publish | unpublish | archive")
    @PreAuthorize("hasAuthority('assessment.manage') or hasAuthority('content.publish')")
    public ResponseEntity<Map<String, Object>> transition(@PathVariable UUID quizId,
                                                          @PathVariable String action,
                                                          @RequestBody(required = false) Map<String, String> body,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        String notes = body == null ? null : body.get("notes");
        return ResponseEntity.ok(Pages.envelope(service.transition(quizId, action, notes, actor)));
    }

    // ----- versioned question bank -----

    @PostMapping("/quizzes/{quizId}/questions")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a versioned MCQ question (draft)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> createQuestion(@PathVariable UUID quizId,
                                                              @Valid @RequestBody AssessmentDtos.QuestionRequest request,
                                                              @AuthenticationPrincipal SecurityPrincipal actor) {
        AssessmentQuestion q = service.createQuestion(quizId, request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(
                service.toAdminDetail(q.getQuiz())));
    }

    @PatchMapping("/questions/{questionId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Edit a question — bumps version, invalidates approval")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> updateQuestion(@PathVariable UUID questionId,
                                                              @Valid @RequestBody AssessmentDtos.QuestionRequest request,
                                                              @AuthenticationPrincipal SecurityPrincipal actor) {
        AssessmentQuestion q = service.updateQuestion(questionId, request, actor);
        return ResponseEntity.ok(Pages.envelope(Map.of(
                "questionId", q.getPublicId(), "version", q.getVersion(),
                "reviewState", q.getReviewState().name())));
    }

    @PostMapping("/questions/{questionId}/submit-review")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Submit a question for accuracy review")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> submitReview(@PathVariable UUID questionId,
                                                            @AuthenticationPrincipal SecurityPrincipal actor) {
        AssessmentQuestion q = service.submitQuestionReview(questionId, actor);
        return ResponseEntity.ok(Pages.envelope(Map.of(
                "questionId", q.getPublicId(), "reviewState", q.getReviewState().name())));
    }

    @PostMapping("/questions/{questionId}/decision")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Editorial decision on a question (reviewer ≠ author; recorded)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER')")
    public ResponseEntity<Map<String, Object>> decide(@PathVariable UUID questionId,
                                                      @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        boolean approve = !Boolean.FALSE.equals(body.get("approve"));
        String notes = body.get("notes") == null ? null : String.valueOf(body.get("notes"));
        AssessmentQuestion q = service.decideQuestion(questionId, approve, notes, actor);
        return ResponseEntity.ok(Pages.envelope(Map.of(
                "questionId", q.getPublicId(), "reviewState", q.getReviewState().name())));
    }

    @GetMapping("/questions/{questionId}/versions")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Immutable version history of a question")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> versions(@PathVariable UUID questionId) {
        return ResponseEntity.ok(Pages.envelope(service.questionVersions(questionId)));
    }

    // ----- admin flashcard decks -----

    @PostMapping("/flashcards/decks")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create an admin flashcard deck (draft)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> createDeck(@Valid @RequestBody AssessmentDtos.DeckRequest request,
                                                          @AuthenticationPrincipal SecurityPrincipal actor) {
        FlashcardDeck d = service.createAdminDeck(request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(service.toDeckDetail(d)));
    }

    @GetMapping("/flashcards/decks")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Admin deck list")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> listDecks(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        Page<FlashcardDeck> result = service.adminDecks(page, size);
        return ResponseEntity.ok(Pages.envelope(result, service::toDeckRow));
    }

    @PatchMapping("/flashcards/decks/{deckId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update admin deck / state transition (draft→approved→published, disabled anytime)")
    @PreAuthorize("hasAuthority('assessment.manage')")
    public ResponseEntity<Map<String, Object>> patchDeck(@PathVariable UUID deckId,
                                                         @RequestBody AssessmentDtos.DeckPatch patch,
                                                         @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.toDeckDetail(service.patchAdminDeck(deckId, patch, actor))));
    }
}
