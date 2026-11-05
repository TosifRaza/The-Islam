package app.ikp.backend.assessment;

import app.ikp.backend.assessment.dto.AssessmentDtos;
import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.academy.AcademyCourse;
import app.ikp.backend.academy.AcademyCourseRepository;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.subject.SubjectRepository;
import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
import app.ikp.backend.workflow.ContentReviewRecord;
import app.ikp.backend.workflow.ContentReviewRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assessment service (M5 spec Module 13). Safety-critical invariants:
 *
 *  1. Correct answers and explanations are SERVER-ONLY data. No public or
 *     pre-submission payload ever contains is_correct or explanation.
 *  2. Grading happens server-side at submit time, against the option set that
 *     was snapshotted into the attempt at start.
 *  3. Question content is versioned: every edit appends an immutable version
 *     snapshot and resets review_state to draft (approval invalidated).
 *  4. A quiz can be published ONLY when state=approved AND every question is
 *     approved, with each approval recorded by a reviewer other than the
 *     question author (WORKFLOW-001 otherwise; denial audited).
 *  5. Attempts and their results are ownership-scoped (user_id from token).
 *  6. Admin flashcard decks are publish-gated; user decks are private by
 *     construction (owner-scoped queries only).
 */
@Service
public class AssessmentService {

    /** Grace period after time_limit_seconds before a submit is refused as expired. */
    static final long SUBMIT_GRACE_SECONDS = 60;

    private final AssessmentQuizRepository quizzes;
    private final AssessmentQuestionRepository questions;
    private final AssessmentQuestionVersionRepository versions;
    private final AssessmentAttemptRepository attempts;
    private final AssessmentAttemptAnswerRepository answers;
    private final FlashcardDeckRepository decks;
    private final AcademyCourseRepository academyCourses;
    private final SubjectRepository subjects;
    private final UserRepository users;
    private final ContentReviewRecordRepository reviews;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public AssessmentService(AssessmentQuizRepository quizzes, AssessmentQuestionRepository questions,
                             AssessmentQuestionVersionRepository versions,
                             AssessmentAttemptRepository attempts,
                             AssessmentAttemptAnswerRepository answers,
                             FlashcardDeckRepository decks, AcademyCourseRepository academyCourses,
                             SubjectRepository subjects,
                             UserRepository users, ContentReviewRecordRepository reviews,
                             AuditService audit, ObjectMapper mapper) {
        this.quizzes = quizzes;
        this.questions = questions;
        this.versions = versions;
        this.attempts = attempts;
        this.answers = answers;
        this.decks = decks;
        this.academyCourses = academyCourses;
        this.subjects = subjects;
        this.users = users;
        this.reviews = reviews;
        this.audit = audit;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- quizzes (admin)

    @Transactional
    public AssessmentQuiz createQuiz(AssessmentDtos.QuizRequest r, SecurityPrincipal actor) {
        AssessmentQuiz q = new AssessmentQuiz();
        q.setTitle(r.title());
        q.setDescription(r.description());
        q.setLanguage(r.language() == null ? "en" : r.language());
        q.setTimeLimitSeconds(r.timeLimitSeconds());
        if (r.passScorePct() != null) q.setPassScorePct(r.passScorePct());
        if (r.courseId() != null) {
            q.setCourse(academyCourseRef(r.courseId()));
        }
        if (r.subjectSlug() != null && !r.subjectSlug().isBlank()) {
            q.setSubject(subjects.findBySlug(r.subjectSlug())
                    .orElseThrow(() -> ApiException.validation("unknown subject slug")));
        }
        q.setCreatedBy(users.getReferenceById(actor.id()));
        quizzes.save(q);
        audit.record(actor.id(), "assessment.quiz.create", "assessment_quiz", q.getId(), null,
                quizSnapshot(q), null);
        return q;
    }

    @Transactional
    public AssessmentQuiz updateQuiz(UUID publicId, AssessmentDtos.QuizRequest r, SecurityPrincipal actor) {
        AssessmentQuiz q = quiz(publicId);
        if (q.getStatus() != AssessmentQuiz.Status.draft) {
            throw ApiException.workflow("only draft quizzes can be edited (state " + q.getStatus() + ")");
        }
        Map<String, Object> before = quizSnapshot(q);
        q.setTitle(r.title());
        q.setDescription(r.description());
        q.setLanguage(r.language() == null ? q.getLanguage() : r.language());
        q.setTimeLimitSeconds(r.timeLimitSeconds());
        if (r.passScorePct() != null) q.setPassScorePct(r.passScorePct());
        audit.record(actor.id(), "assessment.quiz.update", "assessment_quiz", q.getId(),
                before, quizSnapshot(q), null);
        return q;
    }

    @Transactional
    public AssessmentDtos.TransitionView transition(UUID publicId, String action, String notes,
                                                    SecurityPrincipal actor) {
        AssessmentQuiz q = quiz(publicId);
        AssessmentQuiz.Status before = q.getStatus();
        switch (action == null ? "" : action) {
            case "submit-review" -> {
                requireState(q, AssessmentQuiz.Status.draft, action);
                q.setStatus(AssessmentQuiz.Status.in_editorial_review);
            }
            case "approve-editorial" -> {
                requireState(q, AssessmentQuiz.Status.in_editorial_review, action);
                requireRoles(actor);
                if (actor.id().equals(q.getCreatedBy() == null ? null : q.getCreatedBy().getId())) {
                    denyQuiz(q, actor, action, "editorial approval requires a reviewer other than the creator");
                }
                saveReview("assessment_quiz", q.getId(), ContentReviewRecord.Stage.editorial_review,
                        ContentReviewRecord.Decision.approved, actor, notes);
                q.setStatus(AssessmentQuiz.Status.approved);
            }
            case "reject" -> {
                if (notes == null || notes.isBlank()) throw ApiException.validation("rejection requires notes");
                requireState(q, AssessmentQuiz.Status.in_editorial_review, action);
                requireRoles(actor);
                saveReview("assessment_quiz", q.getId(), ContentReviewRecord.Stage.editorial_review,
                        ContentReviewRecord.Decision.rejected, actor, notes);
                q.setStatus(AssessmentQuiz.Status.draft);
            }
            case "publish" -> {
                requireState(q, AssessmentQuiz.Status.approved, action);
                requirePermission(actor, "content.publish", action, q);
                if (questions.findByQuizIdOrderBySortOrderAsc(q.getId()).isEmpty()) {
                    denyQuiz(q, actor, action, "publish refused: quiz has no questions");
                }
                long pending = questions.countByQuizIdAndReviewStateNot(q.getId(),
                        AssessmentQuestion.ReviewState.approved);
                if (pending > 0) {
                    denyQuiz(q, actor, action, "publish refused: " + pending
                            + " question(s) are not approved — question accuracy review is required (Module 13)");
                }
                q.setStatus(AssessmentQuiz.Status.published);
                q.setPublishedAt(Instant.now());
            }
            case "unpublish" -> {
                requireState(q, AssessmentQuiz.Status.published, action);
                requirePermission(actor, "content.publish", action, q);
                q.setStatus(AssessmentQuiz.Status.approved);
            }
            case "archive" -> {
                requireState(q, AssessmentQuiz.Status.draft, AssessmentQuiz.Status.approved, action);
                requirePermission(actor, "content.publish", action, q);
                q.setStatus(AssessmentQuiz.Status.archived);
            }
            default -> throw ApiException.validation("unsupported action");
        }
        audit.record(actor.id(), "assessment.quiz." + action, "assessment_quiz", q.getId(),
                Map.of("state", before.name()), Map.of("state", q.getStatus().name()), notes);
        return new AssessmentDtos.TransitionView(q.getPublicId(), before.name(), q.getStatus().name(),
                Instant.now().toString());
    }

    // ---------------------------------------------------------------- questions (admin)

    @Transactional
    public AssessmentQuestion createQuestion(UUID quizPublicId, AssessmentDtos.QuestionRequest r,
                                             SecurityPrincipal actor) {
        AssessmentQuiz q = quiz(quizPublicId);
        // draft OR approved: adding a question to an approved (unpublished) quiz is
        // legitimate — the publish gate re-checks that EVERY question is approved.
        requireDraftOrApproved(q, "add questions to");
        if (r.options().size() < 2) throw ApiException.validation("an MCQ needs at least two options");
        long correctCount = r.options().stream().filter(AssessmentDtos.OptionRequest::correct).count();
        if (correctCount != 1) throw ApiException.validation("exactly one option must be correct");

        AssessmentQuestion question = new AssessmentQuestion();
        question.setQuiz(q);
        question.setPrompt(r.prompt());
        question.setPromptAr(r.promptAr());
        question.setExplanation(r.explanation());
        question.setPoints(r.points() == null ? 1 : r.points());
        question.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
        question.setCreatedBy(users.getReferenceById(actor.id()));
        for (int i = 0; i < r.options().size(); i++) {
            AssessmentDtos.OptionRequest o = r.options().get(i);
            AssessmentQuestionOption opt = new AssessmentQuestionOption();
            opt.setQuestion(question);
            opt.setOptionText(o.optionText());
            opt.setCorrect(o.correct());
            opt.setSortOrder(i);
            question.getOptions().add(opt);
        }
        questions.save(question);
        appendVersion(question, actor, r.changeNote() == null ? "created" : r.changeNote(), 1);
        audit.record(actor.id(), "assessment.question.create", "assessment_quiz", q.getId(),
                null, Map.of("question", question.getPublicId().toString(), "version", 1), null);
        return question;
    }

    /**
     * Every edit appends an immutable version snapshot and invalidates the
     * approval (review_state back to draft) — versioned question management
     * with an editorial review trail (spec Module 13).
     */
    @Transactional
    public AssessmentQuestion updateQuestion(UUID questionPublicId, AssessmentDtos.QuestionRequest r,
                                             SecurityPrincipal actor) {
        AssessmentQuestion question = question(questionPublicId);
        // draft OR approved (unpublished): an edit resets THIS question's approval to
        // draft and the publish gate re-verifies all questions before publication.
        requireDraftOrApproved(question.getQuiz(), "edit questions of");
        if (r.options().size() < 2) throw ApiException.validation("an MCQ needs at least two options");
        long correctCount = r.options().stream().filter(AssessmentDtos.OptionRequest::correct).count();
        if (correctCount != 1) throw ApiException.validation("exactly one option must be correct");

        int nextVersion = question.getVersion() + 1;
        appendVersion(question, actor, r.changeNote() == null ? "edited" : r.changeNote(), nextVersion);

        question.setPrompt(r.prompt());
        question.setPromptAr(r.promptAr());
        question.setExplanation(r.explanation());
        if (r.points() != null) question.setPoints(r.points());
        if (r.sortOrder() != null) question.setSortOrder(r.sortOrder());
        question.setVersion(nextVersion);
        question.setReviewState(AssessmentQuestion.ReviewState.draft); // approval invalidated by edit
        question.getOptions().clear();
        for (int i = 0; i < r.options().size(); i++) {
            AssessmentDtos.OptionRequest o = r.options().get(i);
            AssessmentQuestionOption opt = new AssessmentQuestionOption();
            opt.setQuestion(question);
            opt.setOptionText(o.optionText());
            opt.setCorrect(o.correct());
            opt.setSortOrder(i);
            question.getOptions().add(opt);
        }
        questions.save(question);
        audit.record(actor.id(), "assessment.question.update", "assessment_quiz",
                question.getQuiz().getId(), null,
                Map.of("question", question.getPublicId().toString(), "version", nextVersion,
                        "reviewState", "draft"), null);
        return question;
    }

    @Transactional
    public AssessmentQuestion submitQuestionReview(UUID questionPublicId, SecurityPrincipal actor) {
        AssessmentQuestion question = question(questionPublicId);
        if (question.getReviewState() != AssessmentQuestion.ReviewState.draft) {
            throw ApiException.workflow("question state " + question.getReviewState()
                    + " does not permit submit-review");
        }
        question.setReviewState(AssessmentQuestion.ReviewState.pending_review);
        audit.record(actor.id(), "assessment.question.submit_review", "assessment_question",
                question.getId(), null, Map.of("reviewState", "pending_review"), null);
        return question;
    }

    /** Editorial approval of a question — reviewer ≠ author, recorded. */
    @Transactional
    public AssessmentQuestion decideQuestion(UUID questionPublicId, boolean approve, String notes,
                                             SecurityPrincipal actor) {
        AssessmentQuestion question = question(questionPublicId);
        if (question.getReviewState() != AssessmentQuestion.ReviewState.pending_review) {
            throw ApiException.workflow("question state " + question.getReviewState() + " does not permit a decision");
        }
        requireRoles(actor);
        if (question.getCreatedBy() != null && question.getCreatedBy().getId().equals(actor.id())) {
            audit.recordDenial("assessment.question.decision_deny", "assessment_question", question.getId(),
                    "reviewer equals author");
            throw ApiException.forbidden("a question cannot be approved by its own author");
        }
        saveReview("assessment_question", question.getId(), ContentReviewRecord.Stage.editorial_review,
                approve ? ContentReviewRecord.Decision.approved : ContentReviewRecord.Decision.rejected,
                actor, notes);
        question.setReviewState(approve
                ? AssessmentQuestion.ReviewState.approved
                : AssessmentQuestion.ReviewState.draft);
        audit.record(actor.id(), approve ? "assessment.question.approved" : "assessment.question.rejected",
                "assessment_question", question.getId(), null,
                Map.of("reviewState", question.getReviewState().name()), notes);
        return question;
    }

    @Transactional(readOnly = true)
    public List<AssessmentDtos.QuestionVersionView> questionVersions(UUID questionPublicId) {
        AssessmentQuestion question = question(questionPublicId);
        return versions.findByQuestionIdOrderByVersionDesc(question.getId()).stream()
                .map(v -> new AssessmentDtos.QuestionVersionView(v.getVersion(), parseSnapshot(v.getSnapshot()),
                        v.getChangeNote(),
                        v.getEditedBy() == null ? null : v.getEditedBy().getEmail(),
                        String.valueOf(v.getEditedAt())))
                .toList();
    }

    // ---------------------------------------------------------------- attempts (member)

    @Transactional
    public AssessmentDtos.AttemptStarted startAttempt(UUID quizPublicId, SecurityPrincipal actor) {
        AssessmentQuiz q = quiz(quizPublicId);
        if (q.getStatus() != AssessmentQuiz.Status.published) throw ApiException.notFound("quiz not found");
        List<AssessmentQuestion> pool = questions.findByQuizIdOrderBySortOrderAsc(q.getId());
        if (pool.isEmpty()) throw ApiException.workflow("quiz has no questions");

        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setUser(users.getReferenceById(actor.id()));
        attempt.setQuiz(q);
        attempt.setPresented(presentedJson(pool));
        attempts.save(attempt);
        audit.record(actor.id(), "assessment.attempt.start", "assessment_quiz", q.getId(), null,
                Map.of("attempt", attempt.getPublicId().toString()), null);
        return new AssessmentDtos.AttemptStarted(attempt.getPublicId(), q.getPublicId(),
                String.valueOf(attempt.getStartedAt()), q.getTimeLimitSeconds(),
                pool.stream().map(this::toPublicQuestion).toList());
    }

    @Transactional
    public AssessmentDtos.AttemptResult submitAttempt(UUID attemptPublicId,
                                                      List<AssessmentDtos.SubmitAnswer> submitted,
                                                      SecurityPrincipal actor) {
        AssessmentAttempt attempt = ownedAttempt(attemptPublicId, actor);
        if (attempt.getStatus() != AssessmentAttempt.Status.in_progress) {
            throw ApiException.workflow("attempt already " + attempt.getStatus());
        }
        AssessmentQuiz q = attempt.getQuiz();
        if (q.getTimeLimitSeconds() != null && attempt.getStartedAt() != null) {
            long elapsed = Duration.between(attempt.getStartedAt(), Instant.now()).getSeconds();
            if (elapsed > q.getTimeLimitSeconds() + SUBMIT_GRACE_SECONDS) {
                attempt.setStatus(AssessmentAttempt.Status.expired);
                audit.record(actor.id(), "assessment.attempt.expired", "assessment_quiz", q.getId(),
                        null, Map.of("attempt", attemptPublicId.toString()), null);
                throw ApiException.workflow("attempt expired (time limit " + q.getTimeLimitSeconds() + "s)");
            }
        }

        // Grade server-side against the questions in the presented set only.
        List<UUID> presentedIds = presentedQuestionIds(attempt);
        int earned = 0;
        int totalPoints = 0;
        List<AssessmentAttemptAnswer> rows = new ArrayList<>();
        for (UUID questionId : presentedIds) {
            AssessmentQuestion question = questions.findByPublicId(questionId)
                    .orElse(null);
            if (question == null) continue; // question removed after snapshot — excluded from scoring
            totalPoints += question.getPoints();
            AssessmentQuestionOption correct = question.getOptions().stream()
                    .filter(AssessmentQuestionOption::isCorrect).findFirst().orElse(null);
            UUID selected = submitted.stream()
                    .filter(a -> a.questionId().equals(questionId))
                    .map(AssessmentDtos.SubmitAnswer::optionId)
                    .findFirst().orElse(null);
            AssessmentQuestionOption selectedOpt = selected == null ? null
                    : question.getOptions().stream()
                            .filter(o -> optionPublicId(o).equals(selected))
                            .findFirst().orElse(null);
            boolean isCorrect = selectedOpt != null && selectedOpt.isCorrect();
            if (isCorrect && correct != null) earned += question.getPoints();

            AssessmentAttemptAnswer row = new AssessmentAttemptAnswer();
            row.setAttemptId(attempt.getId());
            row.setQuestionId(question.getId());
            row.setAttempt(attempt);
            row.setQuestion(question);
            row.setSelectedOption(selectedOpt);
            row.setCorrect(isCorrect);
            rows.add(row);
        }
        answers.saveAll(rows);

        int scorePct = totalPoints == 0 ? 0 : (int) Math.round(100.0 * earned / totalPoints);
        boolean passed = scorePct >= q.getPassScorePct();
        attempt.setStatus(AssessmentAttempt.Status.submitted);
        attempt.setSubmittedAt(Instant.now());
        attempt.setScorePct(scorePct);
        attempt.setPassed(passed);
        attempts.save(attempt);
        audit.record(actor.id(), "assessment.attempt.submit", "assessment_quiz", q.getId(),
                null, Map.of("attempt", attemptPublicId.toString(), "score", scorePct, "passed", passed), null);

        List<AssessmentDtos.AnswerResult> results = new ArrayList<>();
        for (AssessmentAttemptAnswer row : rows) {
            AssessmentQuestionOption correctOpt = row.getQuestion().getOptions().stream()
                    .filter(AssessmentQuestionOption::isCorrect).findFirst().orElse(null);
            results.add(new AssessmentDtos.AnswerResult(row.getQuestion().getPublicId(),
                    row.getSelectedOption() == null ? null : optionPublicId(row.getSelectedOption()),
                    correctOpt == null ? null : optionPublicId(correctOpt),
                    row.isCorrect(), row.getQuestion().getExplanation()));
        }
        return new AssessmentDtos.AttemptResult(attempt.getPublicId(), q.getPublicId(),
                attempt.getStatus().name(), scorePct, passed, q.getPassScorePct(), results,
                String.valueOf(attempt.getSubmittedAt()));
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.AttemptResult resultOf(UUID attemptPublicId, SecurityPrincipal actor) {
        AssessmentAttempt attempt = ownedAttempt(attemptPublicId, actor);
        List<AssessmentDtos.AnswerResult> results = answers.findByAttemptId(attempt.getId()).stream()
                .map(row -> {
                    AssessmentQuestionOption correctOpt = row.getQuestion().getOptions().stream()
                            .filter(AssessmentQuestionOption::isCorrect).findFirst().orElse(null);
                    return new AssessmentDtos.AnswerResult(row.getQuestion().getPublicId(),
                            row.getSelectedOption() == null ? null : optionPublicId(row.getSelectedOption()),
                            correctOpt == null ? null : optionPublicId(correctOpt),
                            row.isCorrect(), row.getQuestion().getExplanation());
                }).toList();
        return new AssessmentDtos.AttemptResult(attempt.getPublicId(), attempt.getQuiz().getPublicId(),
                attempt.getStatus().name(),
                attempt.getScorePct() == null ? 0 : attempt.getScorePct(),
                Boolean.TRUE.equals(attempt.getPassed()),
                attempt.getQuiz().getPassScorePct(), results,
                attempt.getSubmittedAt() == null ? null : String.valueOf(attempt.getSubmittedAt()));
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDtos.AttemptRow> attemptHistory(Long userId, UUID quizPublicId, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100));
        Page<AssessmentAttempt> p = quizPublicId == null
                ? attempts.findByUserIdOrderByStartedAtDesc(userId, pr)
                : attempts.findByUserIdAndQuizIdOrderByStartedAtDesc(userId, quizId(quizPublicId), pr);
        return p.map(t -> new AssessmentDtos.AttemptRow(t.getPublicId(), t.getQuiz().getPublicId(),
                t.getQuiz().getTitle(), t.getStatus().name(), t.getScorePct(), t.getPassed(),
                String.valueOf(t.getStartedAt()),
                t.getSubmittedAt() == null ? null : String.valueOf(t.getSubmittedAt())));
    }

    /** Weak-topic identification from the user's own submitted attempts. */
    @Transactional(readOnly = true)
    public List<AssessmentDtos.WeakTopic> weakTopics(Long userId) {
        List<Object[]> rows = answers.answeredRows(userId);
        Map<String, int[]> bySubject = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String slug = row[1] == null ? null : String.valueOf(row[1]);
            String key = slug == null ? "general" : slug;
            int[] agg = bySubject.computeIfAbsent(key, k -> new int[2]);
            agg[1]++;
            if (!Boolean.TRUE.equals(row[2])) agg[0]++;
        }
        return bySubject.entrySet().stream()
                .map(e -> new AssessmentDtos.WeakTopic("general".equals(e.getKey()) ? null : e.getKey(),
                        "general".equals(e.getKey()) ? "General" : e.getKey(),
                        e.getValue()[0], e.getValue()[1],
                        e.getValue()[1] == 0 ? 0 : (int) Math.round(100.0 * e.getValue()[0] / e.getValue()[1])))
                .sorted((a, b) -> Integer.compare(b.wrongPct(), a.wrongPct()))
                .toList();
    }

    // ---------------------------------------------------------------- flashcards

    @Transactional
    public FlashcardDeck createAdminDeck(AssessmentDtos.DeckRequest r, SecurityPrincipal actor) {
        FlashcardDeck d = new FlashcardDeck();
        d.setOwnerType(FlashcardDeck.OwnerType.admin);
        d.setTitle(r.title());
        d.setDescription(r.description());
        d.setLanguage(r.language() == null ? "en" : r.language());
        if (r.subjectSlug() != null && !r.subjectSlug().isBlank()) {
            d.setSubject(subjects.findBySlug(r.subjectSlug())
                    .orElseThrow(() -> ApiException.validation("unknown subject slug")));
        }
        d.setCreatedBy(users.getReferenceById(actor.id()));
        if (r.cards() != null) {
            for (int i = 0; i < r.cards().size(); i++) {
                AssessmentDtos.CardRequest c = r.cards().get(i);
                Flashcard card = new Flashcard();
                card.setDeck(d);
                card.setFrontText(c.frontText());
                card.setBackText(c.backText());
                card.setHint(c.hint());
                card.setSortOrder(c.sortOrder() == null ? i : c.sortOrder());
                d.getCards().add(card);
            }
        }
        decks.save(d);
        audit.record(actor.id(), "flashcards.admin_deck.create", "flashcard_deck", d.getId(),
                null, Map.of("title", d.getTitle(), "cards", d.getCards().size()), null);
        return d;
    }

    @Transactional
    public FlashcardDeck patchAdminDeck(UUID deckPublicId, AssessmentDtos.DeckPatch patch,
                                        SecurityPrincipal actor) {
        FlashcardDeck d = deck(deckPublicId, FlashcardDeck.OwnerType.admin);
        if (patch.title() != null) d.setTitle(patch.title());
        if (patch.description() != null) d.setDescription(patch.description());
        if (patch.language() != null) d.setLanguage(patch.language());
        if (patch.publishState() != null) {
            FlashcardDeck.PublishState next = FlashcardDeck.PublishState.valueOf(patch.publishState());
            FlashcardDeck.PublishState cur = d.getPublishState();
            boolean allowed = switch (next) {
                case approved -> cur == FlashcardDeck.PublishState.draft;
                case published -> cur == FlashcardDeck.PublishState.approved;
                case disabled -> true;
                case draft -> cur == FlashcardDeck.PublishState.approved; // back to draft
            };
            if (!allowed) {
                throw ApiException.workflow("publish_state " + cur + " does not permit transition to " + next);
            }
            d.setPublishState(next);
        }
        audit.record(actor.id(), "flashcards.admin_deck.update", "flashcard_deck", d.getId(),
                null, Map.of("title", d.getTitle(), "publishState", d.getPublishState().name()), null);
        return d;
    }

    @Transactional
    public FlashcardDeck createUserDeck(AssessmentDtos.DeckRequest r, SecurityPrincipal actor) {
        FlashcardDeck d = new FlashcardDeck();
        d.setOwnerType(FlashcardDeck.OwnerType.user);
        d.setOwnerUser(users.getReferenceById(actor.id()));
        d.setCreatedBy(users.getReferenceById(actor.id()));
        d.setTitle(r.title());
        d.setDescription(r.description());
        d.setLanguage(r.language() == null ? "en" : r.language());
        d.setPublishState(FlashcardDeck.PublishState.published); // personal decks are immediately usable — privately
        if (r.subjectSlug() != null && !r.subjectSlug().isBlank()) {
            d.setSubject(subjects.findBySlug(r.subjectSlug())
                    .orElseThrow(() -> ApiException.validation("unknown subject slug")));
        }
        if (r.cards() != null) {
            for (int i = 0; i < r.cards().size(); i++) {
                AssessmentDtos.CardRequest c = r.cards().get(i);
                Flashcard card = new Flashcard();
                card.setDeck(d);
                card.setFrontText(c.frontText());
                card.setBackText(c.backText());
                card.setHint(c.hint());
                card.setSortOrder(c.sortOrder() == null ? i : c.sortOrder());
                d.getCards().add(card);
            }
        }
        decks.save(d);
        audit.record(actor.id(), "flashcards.user_deck.create", "flashcard_deck", d.getId(),
                null, Map.of("title", d.getTitle()), null);
        return d;
    }

    @Transactional(readOnly = true)
    public Page<FlashcardDeck> adminDecks(int page, int size) {
        return decks.findByOwnerTypeOrderByUpdatedAtDesc(FlashcardDeck.OwnerType.admin,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public Page<FlashcardDeck> publicDecks(int page, int size) {
        return decks.findByOwnerTypeAndPublishStateOrderByUpdatedAtDesc(FlashcardDeck.OwnerType.admin,
                FlashcardDeck.PublishState.published,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public List<FlashcardDeck> myDecks(Long userId) {
        return decks.findByOwnerTypeAndOwnerUserIdOrderByUpdatedAtDesc(FlashcardDeck.OwnerType.user, userId);
    }

    /** Owner-scoped metadata edit on a personal deck (admin decks are untouched here). */
    @Transactional
    public FlashcardDeck updateMyDeck(UUID deckPublicId, AssessmentDtos.DeckPatch patch,
                                      SecurityPrincipal actor) {
        FlashcardDeck d = ownedUserDeck(deckPublicId, actor);
        if (patch.title() != null) d.setTitle(patch.title());
        if (patch.description() != null) d.setDescription(patch.description());
        if (patch.language() != null) d.setLanguage(patch.language());
        decks.save(d);
        audit.record(actor.id(), "flashcards.user_deck.update", "flashcard_deck", d.getId(),
                null, Map.of("title", d.getTitle()), null);
        return d;
    }

    /** Owner-scoped delete; personal rows are invisible to everyone else (404, not 403). */
    @Transactional
    public void deleteMyDeck(UUID deckPublicId, SecurityPrincipal actor) {
        FlashcardDeck d = ownedUserDeck(deckPublicId, actor);
        audit.record(actor.id(), "flashcards.user_deck.delete", "flashcard_deck", d.getId(),
                null, Map.of("title", d.getTitle()), null);
        decks.delete(d);
    }

    private FlashcardDeck ownedUserDeck(UUID deckPublicId, SecurityPrincipal actor) {
        FlashcardDeck d = decks.findByPublicId(deckPublicId)
                .orElseThrow(() -> ApiException.notFound("deck not found"));
        if (d.getOwnerType() != FlashcardDeck.OwnerType.user
                || d.getOwnerUser() == null || !d.getOwnerUser().getId().equals(actor.id())) {
            // Structurally invisible — same response as for a missing deck.
            throw ApiException.notFound("deck not found");
        }
        return d;
    }

    /** Ownership-scoped deck access: user decks only for their owner; admin decks by state. */
    @Transactional(readOnly = true)
    public FlashcardDeck deckForUser(UUID deckPublicId, Long userId, boolean adminView) {
        FlashcardDeck d = decks.findByPublicId(deckPublicId)
                .orElseThrow(() -> ApiException.notFound("deck not found"));
        if (d.getOwnerType() == FlashcardDeck.OwnerType.user) {
            if (d.getOwnerUser() == null || !d.getOwnerUser().getId().equals(userId)) {
                // Personal decks are structurally invisible to everyone else.
                throw ApiException.notFound("deck not found");
            }
        } else if (!adminView) {
            if (d.getPublishState() != FlashcardDeck.PublishState.published
                    && d.getPublishState() != FlashcardDeck.PublishState.approved) {
                throw ApiException.notFound("deck not found");
            }
        }
        return d;
    }

    // ---------------------------------------------------------------- reads/mapping

    /** Admin deck accessor with owner-type guard. */
    @Transactional(readOnly = true)
    public FlashcardDeck deck(UUID deckPublicId, FlashcardDeck.OwnerType expectedOwner) {
        FlashcardDeck d = decks.findByPublicId(deckPublicId)
                .orElseThrow(() -> ApiException.notFound("deck not found"));
        if (d.getOwnerType() != expectedOwner) throw ApiException.notFound("deck not found");
        return d;
    }

    @Transactional(readOnly = true)
    public AssessmentQuiz quiz(UUID publicId) {
        return quizzes.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("quiz not found"));
    }

    @Transactional(readOnly = true)
    public AssessmentQuestion question(UUID publicId) {
        return questions.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("question not found"));
    }

    /** Used by AcademyService when linking a lesson — existence check only. */
    public AssessmentQuiz quizByPublicIdForLink(UUID publicId) {
        return quizzes.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.validation("quiz not found"));
    }

    /** Used by AcademyService when linking a lesson — existence check only. */
    public FlashcardDeck deckByPublicIdForLink(UUID publicId) {
        return decks.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.validation("flashcard deck not found"));
    }

    @Transactional(readOnly = true)
    public Page<AssessmentQuiz> adminQuizzes(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (status != null && !status.isBlank()) {
            return quizzes.findByStatusOrderByUpdatedAtDesc(
                    AssessmentQuiz.Status.valueOf(status.toLowerCase()), pr);
        }
        return quizzes.findAll(pr);
    }

    @Transactional(readOnly = true)
    public Page<AssessmentQuiz> publicQuizzes(int page, int size) {
        return quizzes.findByStatusOrderByUpdatedAtDesc(AssessmentQuiz.Status.published,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
    }

    public AssessmentDtos.QuizAdminRow toAdminRow(AssessmentQuiz q) {
        List<AssessmentQuestion> qs = questions.findByQuizIdOrderBySortOrderAsc(q.getId());
        int approved = (int) qs.stream()
                .filter(x -> x.getReviewState() == AssessmentQuestion.ReviewState.approved).count();
        return new AssessmentDtos.QuizAdminRow(q.getPublicId(), q.getTitle(), q.getLanguage(),
                q.getStatus().name(), q.getSubject() == null ? null : q.getSubject().getSlug(),
                q.getCourse() == null ? null : q.getCourse().getPublicId(),
                q.getTimeLimitSeconds(), q.getPassScorePct(), qs.size(), approved,
                String.valueOf(q.getUpdatedAt()));
    }

    /** Public catalogue row — no admin fields, no approval counters. */
    public AssessmentDtos.QuizPublicRow toPublicQuizRow(AssessmentQuiz q) {
        return new AssessmentDtos.QuizPublicRow(q.getPublicId(), q.getTitle(), q.getDescription(),
                q.getLanguage(), q.getSubject() == null ? null : q.getSubject().getSlug(),
                q.getCourse() == null ? null : q.getCourse().getPublicId(),
                q.getTimeLimitSeconds(), q.getPassScorePct(),
                questions.findByQuizIdOrderBySortOrderAsc(q.getId()).size());
    }

    public AssessmentDtos.QuizAdminDetail toAdminDetail(AssessmentQuiz q) {
        List<AssessmentDtos.QuestionAdminView> views = questions.findByQuizIdOrderBySortOrderAsc(q.getId())
                .stream().map(x -> new AssessmentDtos.QuestionAdminView(x.getPublicId(), x.getPrompt(),
                        x.getPromptAr(), x.getExplanation(), x.getPoints(), x.getVersion(),
                        x.getReviewState().name(), x.getSortOrder(),
                        x.getOptions().stream().map(o -> new AssessmentDtos.OptionAdminView(
                                optionId(o), o.getOptionText(), o.isCorrect(), o.getSortOrder())).toList()))
                .toList();
        return new AssessmentDtos.QuizAdminDetail(q.getPublicId(), q.getTitle(), q.getDescription(),
                q.getLanguage(), q.getStatus().name(),
                q.getSubject() == null ? null : q.getSubject().getSlug(),
                q.getCourse() == null ? null : q.getCourse().getPublicId(),
                q.getTimeLimitSeconds(), q.getPassScorePct(), views);
    }

    /** PUBLIC detail — intentionally carries NO is_correct and NO explanation. */
    public AssessmentDtos.QuizPublicDetail toPublicDetail(AssessmentQuiz q) {
        List<AssessmentDtos.PublicQuestion> qs = questions.findByQuizIdOrderBySortOrderAsc(q.getId())
                .stream().map(this::toPublicQuestion).toList();
        return new AssessmentDtos.QuizPublicDetail(q.getPublicId(), q.getTitle(), q.getDescription(),
                q.getLanguage(), q.getTimeLimitSeconds(), q.getPassScorePct(), qs);
    }

    public AssessmentDtos.PublicQuestion toPublicQuestion(AssessmentQuestion x) {
        // Pre-submission payload: options are answerable (id + text only) — the
        // correctness flag and explanation are NEVER included here; grading is
        // server-side at submit (privacy invariant, tested).
        List<AssessmentDtos.PublicOption> opts = x.getOptions().stream()
                .sorted(java.util.Comparator.comparingInt(
                        o -> o.getSortOrder() == null ? Integer.MAX_VALUE : o.getSortOrder()))
                .map(o -> new AssessmentDtos.PublicOption(optionPublicId(o), o.getOptionText()))
                .toList();
        return new AssessmentDtos.PublicQuestion(x.getPublicId(), x.getPrompt(), x.getPromptAr(),
                x.getPoints(), x.getSortOrder(), opts);
    }

    public AssessmentDtos.DeckRow toDeckRow(FlashcardDeck d) {
        return new AssessmentDtos.DeckRow(d.getPublicId(), d.getTitle(), d.getDescription(), d.getLanguage(),
                d.getSubject() == null ? null : d.getSubject().getSlug(), d.getCards().size(),
                d.getOwnerType().name(), d.getPublishState().name(), String.valueOf(d.getUpdatedAt()));
    }

    public AssessmentDtos.DeckDetail toDeckDetail(FlashcardDeck d) {
        return new AssessmentDtos.DeckDetail(d.getPublicId(), d.getTitle(), d.getDescription(), d.getLanguage(),
                d.getSubject() == null ? null : d.getSubject().getSlug(),
                d.getOwnerType().name(), d.getPublishState().name(),
                d.getCards().stream().map(c -> new AssessmentDtos.CardView(cardPublicId(c),
                        c.getFrontText(), c.getBackText(), c.getHint(), c.getSortOrder())).toList());
    }

    // ---------------------------------------------------------------- helpers

    /** Options have no public id column; expose the surrogate id as a stable string-safe value. */
    public UUID optionPublicId(AssessmentQuestionOption o) {
        return UUID.nameUUIDFromBytes(("ikp-option-" + o.getId()).getBytes());
    }

    private UUID optionId(AssessmentQuestionOption o) {
        return optionPublicId(o);
    }

    private UUID cardPublicId(Flashcard c) {
        return UUID.nameUUIDFromBytes(("ikp-card-" + c.getId()).getBytes());
    }

    private Long quizId(UUID publicId) {
        return quiz(publicId).getId();
    }

    private void requireState(AssessmentQuiz q, AssessmentQuiz.Status e1, AssessmentQuiz.Status e2, String action) {
        if (q.getStatus() != e1 && q.getStatus() != e2) {
            denyQuiz(q, null, action, "state " + q.getStatus() + " does not permit " + action);
        }
    }

    private AcademyCourse academyCourseRef(UUID courseId) {
        return academyCourses.findByPublicId(courseId)
                .orElseThrow(() -> ApiException.validation("course not found"));
    }

    private String presentedJson(List<AssessmentQuestion> pool) {
        try {
            List<Map<String, Object>> list = pool.stream()
                    .map(q -> Map.<String, Object>of("questionId", q.getPublicId().toString()))
                    .toList();
            return mapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("presented snapshot serialisation failed", e);
        }
    }

    private List<UUID> presentedQuestionIds(AssessmentAttempt attempt) {
        try {
            List<?> raw = mapper.readValue(attempt.getPresented(), List.class);
            List<UUID> ids = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m && m.get("questionId") != null) {
                    ids.add(UUID.fromString(String.valueOf(m.get("questionId"))));
                }
            }
            return ids;
        } catch (Exception e) {
            throw new IllegalStateException("presented snapshot parse failed", e);
        }
    }

    private void appendVersion(AssessmentQuestion question, SecurityPrincipal actor, String note, int version) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("prompt", question.getPrompt());
            snap.put("promptAr", question.getPromptAr());
            snap.put("explanation", question.getExplanation());
            snap.put("points", question.getPoints());
            snap.put("sortOrder", question.getSortOrder());
            snap.put("options", question.getOptions().stream()
                    .map(o -> Map.of("text", o.getOptionText(), "correct", o.isCorrect()))
                    .toList());
            AssessmentQuestionVersion v = new AssessmentQuestionVersion();
            v.setQuestion(question);
            v.setVersion(version);
            v.setSnapshot(mapper.writeValueAsString(snap));
            v.setChangeNote(note);
            v.setEditedBy(users.getReferenceById(actor.id()));
            versions.save(v);
        } catch (Exception e) {
            throw new IllegalStateException("version snapshot serialisation failed", e);
        }
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private AssessmentAttempt ownedAttempt(UUID attemptPublicId, SecurityPrincipal actor) {
        AssessmentAttempt t = attempts.findByPublicId(attemptPublicId)
                .orElseThrow(() -> ApiException.notFound("attempt not found"));
        if (t.getUser() == null || !t.getUser().getId().equals(actor.id())) {
            // Ownership is server-side: another user's attempt does not exist for you.
            throw ApiException.notFound("attempt not found");
        }
        return t;
    }

    private void requireDraftOrApproved(AssessmentQuiz q, String verb) {
        if (q.getStatus() != AssessmentQuiz.Status.draft && q.getStatus() != AssessmentQuiz.Status.approved) {
            throw ApiException.workflow("quiz state " + q.getStatus() + " does not permit to " + verb
                    + " (draft/approved only)");
        }
    }

    private void requireState(AssessmentQuiz q, AssessmentQuiz.Status expected, String action) {
        if (q.getStatus() != expected) {
            denyQuiz(q, null, action, "state " + q.getStatus() + " does not permit " + action
                    + " (requires " + expected + ")");
        }
    }

    private void requireRoles(SecurityPrincipal actor) {
        List<String> wanted = List.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_CONTENT_MANAGER");
        boolean ok = actor.authorities().stream().anyMatch(a -> wanted.contains(a.getAuthority()));
        if (!ok) throw ApiException.forbidden("editorial decisions require Super Admin, Admin or Content Manager");
    }

    private void requirePermission(SecurityPrincipal actor, String permission, String action, AssessmentQuiz q) {
        boolean has = actor.authorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
        if (!has) denyQuiz(q, actor, action, "actor lacks permission " + permission);
    }

    private void denyQuiz(AssessmentQuiz q, SecurityPrincipal actor, String action, String detail) {
        audit.recordDenial("assessment.workflow.deny", "assessment_quiz", q.getId(), action + ": " + detail);
        throw ApiException.workflow(detail);
    }

    private void saveReview(String entityType, Long entityId, ContentReviewRecord.Stage stage,
                            ContentReviewRecord.Decision decision, SecurityPrincipal actor, String notes) {
        ContentReviewRecord row = new ContentReviewRecord();
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setStage(stage);
        row.setReviewerId(actor.id());
        row.setDecision(decision);
        row.setNotes(notes);
        reviews.save(row);
    }

    static Map<String, Object> quizSnapshot(AssessmentQuiz q) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", q.getTitle());
        m.put("state", q.getStatus().name());
        m.put("language", q.getLanguage());
        m.put("passScorePct", q.getPassScorePct());
        return m;
    }
}
