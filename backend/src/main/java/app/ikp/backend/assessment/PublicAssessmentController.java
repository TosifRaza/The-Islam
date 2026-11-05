package app.ikp.backend.assessment;

import app.ikp.backend.assessment.dto.AssessmentDtos;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public + member assessment surface. INVARIANT: the pre-submission payloads
 * (catalogue, quiz detail, attempt start) NEVER contain is_correct flags or
 * explanations — grading is server-side and results are only visible to the
 * attempt owner after submission.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "assessments")
public class PublicAssessmentController {

    private final AssessmentService service;

    public PublicAssessmentController(AssessmentService service) { this.service = service; }

    // ----- published quizzes -----

    @GetMapping("/assessments/quizzes")
    @Operation(summary = "Published quiz catalogue")
    public ResponseEntity<Map<String, Object>> quizzes(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Page<AssessmentQuiz> result = service.publicQuizzes(page, size);
        return ResponseEntity.ok(Pages.envelope(result, service::toPublicQuizRow));
    }

    @GetMapping("/assessments/quizzes/{quizId}")
    @Operation(summary = "Published quiz detail — no answers, no explanations")
    public ResponseEntity<Map<String, Object>> quiz(@PathVariable UUID quizId) {
        AssessmentQuiz q = service.quiz(quizId);
        if (q.getStatus() != AssessmentQuiz.Status.published) throw ApiException.notFound("quiz not found");
        return ResponseEntity.ok(Pages.envelope(service.toPublicDetail(q)));
    }

    @PostMapping("/assessments/quizzes/{quizId}/attempts")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Start an attempt (presented set frozen server-side)")
    public ResponseEntity<Map<String, Object>> start(@PathVariable UUID quizId,
                                                     @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.status(201).body(Pages.envelope(service.startAttempt(quizId, actor)));
    }

    @PostMapping("/assessments/attempts/{attemptId}/submit")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Submit answers — graded server-side; returns result with explanations")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable UUID attemptId,
                                                      @RequestBody AssessmentDtos.SubmitRequest request,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(
                service.submitAttempt(attemptId, request.answers(), actor)));
    }

    @GetMapping("/assessments/attempts/{attemptId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Own attempt result (with explanations after submission)")
    public ResponseEntity<Map<String, Object>> result(@PathVariable UUID attemptId,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.resultOf(attemptId, actor)));
    }

    @GetMapping("/assessments/attempts")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Own attempt history (optionally per quiz)")
    public ResponseEntity<Map<String, Object>> history(@RequestParam(required = false) UUID quizId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @AuthenticationPrincipal SecurityPrincipal actor) {
        Page<AssessmentDtos.AttemptRow> result = service.attemptHistory(actor.id(), quizId, page, size);
        return ResponseEntity.ok(Pages.envelope(result, r -> r));
    }

    @GetMapping("/assessments/weak-topics")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Weak-topic identification from own submitted attempts")
    public ResponseEntity<Map<String, Object>> weakTopics(@AuthenticationPrincipal SecurityPrincipal actor) {
        List<AssessmentDtos.WeakTopic> data = service.weakTopics(actor.id());
        return ResponseEntity.ok(Pages.envelope(data));
    }

    // ----- flashcards -----

    @GetMapping("/flashcards/decks")
    @Operation(summary = "Published admin flashcard decks")
    public ResponseEntity<Map<String, Object>> decks(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        Page<FlashcardDeck> result = service.publicDecks(page, size);
        return ResponseEntity.ok(Pages.envelope(result, service::toDeckRow));
    }

    @GetMapping("/flashcards/decks/{deckId}")
    @Operation(summary = "Deck detail with cards (admin decks: published/approved only; guests welcome)")
    public ResponseEntity<Map<String, Object>> deck(@PathVariable UUID deckId,
                                                    @AuthenticationPrincipal SecurityPrincipal actor) {
        // Guest = userId -1: personal decks are structurally invisible (owner match
        // can never succeed); published admin decks are publicly readable.
        long userId = actor == null ? -1L : actor.id();
        return ResponseEntity.ok(Pages.envelope(service.toDeckDetail(
                service.deckForUser(deckId, userId, false))));
    }

    @GetMapping("/flashcards/my/decks")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Own personal decks (private by construction)")
    public ResponseEntity<Map<String, Object>> myDecks(@AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.myDecks(actor.id()).stream()
                .map(service::toDeckRow).toList()));
    }

    @PostMapping("/flashcards/my/decks")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Create a personal flashcard deck (private)")
    public ResponseEntity<Map<String, Object>> createMyDeck(@RequestBody AssessmentDtos.DeckRequest request,
                                                            @AuthenticationPrincipal SecurityPrincipal actor) {
        FlashcardDeck d = service.createUserDeck(request, actor);
        return ResponseEntity.status(201).body(Pages.envelope(service.toDeckDetail(d)));
    }

    @PatchMapping("/flashcards/my/decks/{deckId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Edit own personal deck metadata (owner-scoped)")
    public ResponseEntity<Map<String, Object>> patchMyDeck(@PathVariable UUID deckId,
                                                           @RequestBody AssessmentDtos.DeckPatch patch,
                                                           @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok(Pages.envelope(service.toDeckDetail(
                service.updateMyDeck(deckId, patch, actor))));
    }

    @DeleteMapping("/flashcards/my/decks/{deckId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Delete own personal deck (owner-scoped; 404 for anyone else)")
    public ResponseEntity<Map<String, Object>> deleteMyDeck(@PathVariable UUID deckId,
                                                            @AuthenticationPrincipal SecurityPrincipal actor) {
        service.deleteMyDeck(deckId, actor);
        return ResponseEntity.ok(Pages.envelope(Map.of("deleted", true, "deckId", deckId.toString())));
    }
}
