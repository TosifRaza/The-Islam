package app.ikp.backend.assessment;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5 Assessment acceptance — the safety-critical suite. Verifies:
 *  (1) correct answers/explanations NEVER appear in pre-submission payloads,
 *  (2) grading is server-side and only the owner sees results,
 *  (3) question versioning + review-before-publish gate (WORKFLOW-001),
 *  (4) admin vs user flashcard deck separation with strict privacy.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M5AssessmentSecurityIntegrationTest extends IntegrationTestBase {

    private static String quizId;
    private static String questionApprovedId;
    private static String attemptId;
    private static String correctOptionPublicRef; // not directly exposed — inferred from grading

    @Test
    @Order(1)
    void quizWithUnapprovedQuestionsCannotBePublished() {
        var author = createAccount("academy_manager", uniqueEmail("qz1"));
        String authorToken = login(author);
        var reviewer = createAccount("content_manager", uniqueEmail("qz2"));
        String reviewToken = login(reviewer);
        var publisher = createAccount("super_admin", uniqueEmail("qz3"));
        String publishToken = login(publisher);

        quizId = readJson(post("/api/v1/admin/assessments/quizzes", authorToken, Map.of(
                "title", "Aqeedah Basics " + UUID.randomUUID().toString().substring(0, 5),
                "subjectSlug", "cat_aqeedah", "passScorePct", 50)).getBody())
                .path("data").path("quizId").asText();

        // one question, created but NEVER submitted for review
        ResponseEntity<String> q = post("/api/v1/admin/assessments/quizzes/" + quizId + "/questions", authorToken,
                Map.of("prompt", "How many pillars of Iman are mentioned in the Hadith of Jibril?",
                        "explanation", "The Hadith of Jibril mentions six pillars of Iman.",
                        "options", List.of(
                                Map.of("optionText", "Five", "correct", false),
                                Map.of("optionText", "Six", "correct", true))));
        assertThat(q.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        questionApprovedId = readJson(q.getBody()).path("data").path("quizId").asText(); // detail refetch below

        post("/api/v1/admin/assessments/quizzes/" + quizId + "/submit-review", authorToken, null);
        post("/api/v1/admin/assessments/quizzes/" + quizId + "/approve-editorial", reviewToken, null);
        ResponseEntity<String> publish = post("/api/v1/admin/assessments/quizzes/" + quizId + "/publish",
                publishToken, null);
        assertThat(publish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(publish.getBody()).path("detail").asText()).contains("not approved");
    }

    @Test
    @Order(2)
    void questionApprovalByOtherThanAuthorThenPublishSucceeds() {
        var author = createAccount("academy_manager", uniqueEmail("qz4"));
        String authorToken = login(author);
        var reviewer = createAccount("admin", uniqueEmail("qz5"));
        String reviewToken = login(reviewer);
        var publisher = createAccount("super_admin", uniqueEmail("qz6"));
        String publishToken = login(publisher);

        // fetch the quiz to get the question id
        ResponseEntity<String> detail = get("/api/v1/admin/assessments/quizzes/" + quizId, authorToken);
        String qid = readJson(detail.getBody()).path("data").path("questions").get(0)
                .path("questionId").asText();
        post("/api/v1/admin/assessments/questions/" + qid + "/submit-review", authorToken, null);
        assertThat(post("/api/v1/admin/assessments/questions/" + qid + "/decision", reviewToken,
                Map.of("approve", true, "notes", "verified against source")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // editing after approval invalidates the approval (versioned questions)
        ResponseEntity<String> edit = patch("/api/v1/admin/assessments/questions/" + qid, authorToken, Map.of(
                "prompt", "Edited prompt — how many pillars of Iman in the Hadith of Jibril?",
                "explanation", "Six, per the Hadith of Jibril.",
                "changeNote", "clarify wording",
                "options", List.of(
                        Map.of("optionText", "Five", "correct", false),
                        Map.of("optionText", "Six", "correct", true))));
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(edit.getBody()).path("data").path("version").asInt()).isEqualTo(2);
        assertThat(readJson(edit.getBody()).path("data").path("reviewState").asText()).isEqualTo("draft");

        // versions are append-only
        ResponseEntity<String> versions = get("/api/v1/admin/assessments/questions/" + qid + "/versions",
                authorToken);
        assertThat(readJson(versions.getBody()).path("data").size()).isEqualTo(2);

        // re-approve then publish
        post("/api/v1/admin/assessments/questions/" + qid + "/submit-review", authorToken, null);
        post("/api/v1/admin/assessments/questions/" + qid + "/decision", reviewToken,
                Map.of("approve", true, "notes", "re-verified"));
        ResponseEntity<String> publish = post("/api/v1/admin/assessments/quizzes/" + quizId + "/publish",
                publishToken, null);
        assertThat(publish.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(3)
    void publicQuizPayloadNeverCarriesAnswersOrExplanations() {
        ResponseEntity<String> detail = get("/api/v1/assessments/quizzes/" + quizId, null);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = readJson(detail.getBody()).toString();
        assertThat(body).doesNotContain("isCorrect").doesNotContain("correct")
                .doesNotContain("explanation");
        var questions = readJson(detail.getBody()).path("data").path("questions");
        assertThat(questions.size()).isEqualTo(1);
        assertThat(questions.get(0).path("prompt").asText()).contains("pillars of Iman");
    }

    @Test
    @Order(4)
    void attemptStartGradesServerSideAndOwnerSeesExplanationsOnlyAfterSubmit() {
        var reader = createAccount("registered_user", uniqueEmail("at1"));
        String token = login(reader);
        var other = createAccount("registered_user", uniqueEmail("at2"));
        String otherToken = login(other);

        ResponseEntity<String> start = post("/api/v1/assessments/quizzes/" + quizId + "/attempts", token, null);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var started = readJson(start.getBody()).path("data");
        attemptId = started.path("attemptId").asText();
        // start payload has no answers either
        assertThat(started.toString()).doesNotContain("explanation").doesNotContain("isCorrect");
        String qid = started.path("questions").get(0).path("questionId").asText();

        // submit the WRONG answer on purpose → graded server-side
        // first submit with a fabricated option id (wrong), then verify score 0
        ResponseEntity<String> submit = post("/api/v1/assessments/attempts/" + attemptId + "/submit", token,
                Map.of("answers", List.of(Map.of("questionId", qid,
                        "optionId", "11111111-1111-1111-1111-111111111111"))));
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);
        var result = readJson(submit.getBody()).path("data");
        assertThat(result.path("scorePct").asInt()).isEqualTo(0);
        assertThat(result.path("passed").asBoolean()).isFalse();
        // explanation IS present after submission
        assertThat(result.path("answers").get(0).path("explanation").asText()).contains("Six");

        // owner can re-read the graded result
        ResponseEntity<String> again = get("/api/v1/assessments/attempts/" + attemptId, token);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);

        // another user CANNOT read this attempt (ownership is server-side)
        ResponseEntity<String> leak = get("/api/v1/assessments/attempts/" + attemptId, otherToken);
        assertThat(leak.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(4)
    void attemptPayloadIsAnswerableAndCorrectOptionGradesFullScore() {
        // Regression (found by the M5 live-stack harness): the pre-submission
        // payload must carry the OPTIONS (id + text only) — without them the
        // member cannot answer, and submitting a real option id crashed the
        // grading path (500). Correctness data stays server-side.
        var reader = createAccount("registered_user", uniqueEmail("at3"));
        String token = login(reader);

        ResponseEntity<String> start = post("/api/v1/assessments/quizzes/" + quizId + "/attempts", token, null);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var started = readJson(start.getBody()).path("data");
        String attemptId2 = started.path("attemptId").asText();
        var q0 = started.path("questions").get(0);
        var options = q0.path("options");
        assertThat(options.size()).isEqualTo(2);
        // option rows expose only id + text — never the correct flag
        assertThat(options.get(0).has("correct")).isFalse();
        assertThat(options.get(0).has("explanation")).isFalse();

        // answer with the REAL option id taken from the payload ("Six" is
        // correct per the seeded question) → full score
        String correctOption = options.get(1).path("optionText").asText().equals("Six")
                ? options.get(1).path("optionId").asText()
                : options.get(0).path("optionId").asText();
        ResponseEntity<String> submit = post("/api/v1/assessments/attempts/" + attemptId2 + "/submit", token,
                Map.of("answers", List.of(Map.of(
                        "questionId", q0.path("questionId").asText(),
                        "optionId", correctOption))));
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);
        var result = readJson(submit.getBody()).path("data");
        assertThat(result.path("scorePct").asInt()).isEqualTo(100);
        assertThat(result.path("passed").asBoolean()).isTrue();
        assertThat(result.path("answers").get(0).path("correct").asBoolean()).isTrue();
    }

    @Test
    @Order(5)
    void adminAndUserFlashcardDecksAreStrictlySeparated() {
        var admin = createAccount("academy_manager", uniqueEmail("fc1"));
        String adminToken = login(admin);
        var user = createAccount("registered_user", uniqueEmail("fc2"));
        String userToken = login(user);
        var stranger = createAccount("registered_user", uniqueEmail("fc3"));
        String strangerToken = login(stranger);

        // admin deck starts draft → invisible publicly
        String adminDeck = readJson(post("/api/v1/admin/assessments/flashcards/decks", adminToken, Map.of(
                "title", "Arabic roots deck", "subjectSlug", "cat_arabic_academy",
                "cards", List.of(
                        Map.of("frontText", "كتب", "backText", "to write (root k-t-b)"),
                        Map.of("frontText", "قرأ", "backText", "to read (root q-r-2)"))))
                .getBody()).path("data").path("deckId").asText();
        assertThat(get("/api/v1/flashcards/decks/" + adminDeck, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // publish path draft → approved → published
        assertThat(patch("/api/v1/admin/assessments/flashcards/decks/" + adminDeck, adminToken,
                Map.of("publishState", "published")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        patch("/api/v1/admin/assessments/flashcards/decks/" + adminDeck, adminToken, Map.of("publishState", "approved"));
        assertThat(patch("/api/v1/admin/assessments/flashcards/decks/" + adminDeck, adminToken,
                Map.of("publishState", "published")).getStatusCode()).isEqualTo(HttpStatus.OK);

        // now public
        assertThat(get("/api/v1/flashcards/decks/" + adminDeck, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // user deck: private, invisible to strangers
        String userDeck = readJson(post("/api/v1/flashcards/my/decks", userToken, Map.of(
                "title", "My private revision deck",
                "cards", List.of(Map.of("frontText", "front", "backText", "back"))))
                .getBody()).path("data").path("deckId").asText();
        assertThat(get("/api/v1/flashcards/decks/" + userDeck, strangerToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/flashcards/decks/" + userDeck, userToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        // stranger never sees it in the owner's list either — they see only their own
        ResponseEntity<String> mine = get("/api/v1/flashcards/my/decks", strangerToken);
        assertThat(readJson(mine.getBody()).toString()).doesNotContain("My private revision deck");
    }

    @Test
    @Order(6)
    void weakTopicsReflectWrongAnswers() {
        var reader = createAccount("registered_user", uniqueEmail("wt1"));
        String token = login(reader);
        // attempt the quiz wrong again for a second data point
        ResponseEntity<String> start = post("/api/v1/assessments/quizzes/" + quizId + "/attempts", token, null);
        String qid = readJson(start.getBody()).path("data").path("questions").get(0).path("questionId").asText();
        String att = readJson(start.getBody()).path("data").path("attemptId").asText();
        post("/api/v1/assessments/attempts/" + att + "/submit", token, Map.of("answers",
                List.of(Map.of("questionId", qid, "optionId", "22222222-2222-2222-2222-222222222222"))));

        ResponseEntity<String> weak = get("/api/v1/assessments/weak-topics", token);
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = readJson(weak.getBody()).path("data");
        int wrongTotal = 0;
        for (var t : data) wrongTotal += t.path("wrong").asInt();
        assertThat(wrongTotal).isGreaterThanOrEqualTo(1); // this user's own submitted attempt was wrong
    }

    @Test
    @Order(7)
    void attemptHistoryIsPerUser() {
        var reader = createAccount("registered_user", uniqueEmail("ah1"));
        String token = login(reader);
        ResponseEntity<String> res = get("/api/v1/assessments/attempts", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(res.getBody()).path("data").size()).isEqualTo(0); // this user has none
    }

    @Test
    @Order(8)
    void personalDeckPatchAndDeleteAreOwnerScoped() {
        var owner = createAccount("registered_user", uniqueEmail("fc9"));
        String ownerToken = login(owner);
        var stranger = createAccount("registered_user", uniqueEmail("fc10"));
        String strangerToken = login(stranger);

        String deck = readJson(post("/api/v1/flashcards/my/decks", ownerToken, Map.of(
                "title", "Deck to edit",
                "cards", List.of(Map.of("frontText", "f", "backText", "b"))))
                .getBody()).path("data").path("deckId").asText();

        // stranger's patch/delete are structurally invisible (404, not 403)
        assertThat(patch("/api/v1/flashcards/my/decks/" + deck, strangerToken,
                Map.of("title", "hijacked")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/v1/flashcards/my/decks/" + deck, strangerToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // owner can patch and then delete
        assertThat(patch("/api/v1/flashcards/my/decks/" + deck, ownerToken,
                Map.of("title", "Deck renamed")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(get("/api/v1/flashcards/my/decks", ownerToken).getBody())
                .toString()).contains("Deck renamed");
        assertThat(delete("/api/v1/flashcards/my/decks/" + deck, ownerToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/flashcards/my/decks", ownerToken).getBody()).doesNotContain(deck);

        // an ADMIN deck can never be touched through the personal-deck endpoints
        var admin = createAccount("academy_manager", uniqueEmail("fc11"));
        String adminToken = login(admin);
        String adminDeck = readJson(post("/api/v1/admin/assessments/flashcards/decks", adminToken,
                Map.of("title", "Admin deck", "cards",
                        List.of(Map.of("frontText", "f", "backText", "b")))).getBody())
                .path("data").path("deckId").asText();
        assertThat(patch("/api/v1/flashcards/my/decks/" + adminDeck, ownerToken,
                Map.of("title", "nope")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/v1/flashcards/my/decks/" + adminDeck, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
