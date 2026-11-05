package app.ikp.backend.academy;

import app.ikp.backend.academy.dto.AcademyDtos;
import app.ikp.backend.assessment.AssessmentQuizRepository;
import app.ikp.backend.assessment.FlashcardDeckRepository;
import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.storage.MediaAssetRepository;
import app.ikp.backend.subject.Subject;
import app.ikp.backend.subject.SubjectRepository;
import app.ikp.backend.user.UserRepository;
import app.ikp.backend.workflow.ContentReviewRecord;
import app.ikp.backend.workflow.ContentReviewRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Academy service (M5 spec Module 10). Owns the course state machine
 * (mirrors the approved §3.19.5 semantics used by books):
 *
 *   draft --submit-review--> in_editorial_review
 *   in_editorial_review --approve-editorial--> approved   (reviewer ≠ creator)
 *   in_editorial_review --reject--> draft                 (notes required)
 *   approved --publish--> published                      (content.publish)
 *   published --unpublish--> approved
 *   draft|approved --archive--> archived
 *
 * Structure editing (chapters/lessons) is draft-only. Every transition is
 * audited; denials are audited in their own transaction (WORKFLOW-001, nothing
 * changed). Instructor fields are display-only recorded text — the platform
 * never generates qualifications (spec: do not fabricate instructor
 * credentials; certificates stay out of scope, OD-11).
 */
@Service
public class AcademyService {

    private final AcademyCourseRepository courses;
    private final AcademyChapterRepository chapters;
    private final AcademyLessonRepository lessons;
    private final AcademyEnrolmentRepository enrolments;
    private final AcademyLessonProgressRepository progress;
    private final SubjectRepository subjects;
    private final UserRepository users;
    private final MediaAssetRepository media;
    private final AssessmentQuizRepository quizRepo;
    private final FlashcardDeckRepository deckRepo;
    private final ContentReviewRecordRepository reviews;
    private final AuditService audit;

    public AcademyService(AcademyCourseRepository courses, AcademyChapterRepository chapters,
                          AcademyLessonRepository lessons, AcademyEnrolmentRepository enrolments,
                          AcademyLessonProgressRepository progress, SubjectRepository subjects,
                          UserRepository users, MediaAssetRepository media,
                          AssessmentQuizRepository quizRepo, FlashcardDeckRepository deckRepo,
                          ContentReviewRecordRepository reviews, AuditService audit) {
        this.courses = courses;
        this.chapters = chapters;
        this.lessons = lessons;
        this.enrolments = enrolments;
        this.progress = progress;
        this.subjects = subjects;
        this.users = users;
        this.media = media;
        this.quizRepo = quizRepo;
        this.deckRepo = deckRepo;
        this.reviews = reviews;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- admin

    @Transactional
    public AcademyCourse createCourse(AcademyDtos.CourseRequest r, SecurityPrincipal actor) {
        Subject subject = subjects.findBySlug(r.subjectSlug())
                .orElseThrow(() -> ApiException.validation("unknown subject slug: " + r.subjectSlug()));
        AcademyCourse c = new AcademyCourse();
        apply(c, r, subject);
        c.setCreatedBy(users.getReferenceById(actor.id()));
        courses.save(c);
        audit.record(actor.id(), "academy.course.create", "academy_course", c.getId(), null,
                AcademyDtos.auditSnapshot(c), null);
        return c;
    }

    @Transactional
    public AcademyCourse updateCourse(UUID publicId, AcademyDtos.CourseRequest r, SecurityPrincipal actor) {
        AcademyCourse c = course(publicId);
        if (c.getStatus() != AcademyCourse.Status.draft) {
            throw ApiException.workflow("only draft courses can be edited (state " + c.getStatus() + ")");
        }
        Subject subject = subjects.findBySlug(r.subjectSlug())
                .orElseThrow(() -> ApiException.validation("unknown subject slug: " + r.subjectSlug()));
        Map<String, Object> before = AcademyDtos.auditSnapshot(c);
        apply(c, r, subject);
        audit.record(actor.id(), "academy.course.update", "academy_course", c.getId(),
                before, AcademyDtos.auditSnapshot(c), null);
        return c;
    }

    @Transactional
    public AcademyChapter addChapter(UUID coursePublicId, AcademyDtos.ChapterRequest r, SecurityPrincipal actor) {
        AcademyCourse c = course(coursePublicId);
        requireDraft(c, "add chapters to");
        AcademyChapter ch = new AcademyChapter();
        ch.setCourse(c);
        ch.setTitle(r.title());
        ch.setDescription(r.description());
        ch.setSortOrder(r.sortOrder() == null ? nextChapterOrder(c.getId()) : r.sortOrder());
        chapters.save(ch);
        audit.record(actor.id(), "academy.chapter.create", "academy_course", c.getId(),
                null, Map.of("chapter", ch.getTitle(), "sortOrder", ch.getSortOrder()), null);
        return ch;
    }

    @Transactional
    public AcademyChapter updateChapter(UUID chapterPublicId, AcademyDtos.ChapterRequest r, SecurityPrincipal actor) {
        AcademyChapter ch = chapter(chapterPublicId);
        requireDraft(ch.getCourse(), "edit chapters of");
        ch.setTitle(r.title());
        ch.setDescription(r.description());
        if (r.sortOrder() != null) ch.setSortOrder(r.sortOrder());
        audit.record(actor.id(), "academy.chapter.update", "academy_course", ch.getCourse().getId(),
                null, Map.of("chapter", ch.getTitle()), null);
        return ch;
    }

    @Transactional
    public AcademyLesson addLesson(UUID chapterPublicId, AcademyDtos.LessonRequest r, SecurityPrincipal actor) {
        AcademyChapter ch = chapter(chapterPublicId);
        requireDraft(ch.getCourse(), "add lessons to");
        AcademyLesson l = new AcademyLesson();
        l.setChapter(ch);
        applyLesson(l, r);
        lessons.save(l);
        audit.record(actor.id(), "academy.lesson.create", "academy_course", ch.getCourse().getId(),
                null, Map.of("lesson", l.getTitle(), "type", l.getLessonType().name()), null);
        return l;
    }

    @Transactional
    public AcademyLesson updateLesson(UUID lessonPublicId, AcademyDtos.LessonRequest r, SecurityPrincipal actor) {
        AcademyLesson l = lesson(lessonPublicId);
        requireDraft(l.getChapter().getCourse(), "edit lessons of");
        applyLesson(l, r);
        audit.record(actor.id(), "academy.lesson.update", "academy_course", l.getChapter().getCourse().getId(),
                null, Map.of("lesson", l.getTitle()), null);
        return l;
    }

    /** Workflow transition on a course (see class comment for the machine). */
    @Transactional
    public AcademyDtos.TransitionView transition(UUID coursePublicId, String action, String notes,
                                                 SecurityPrincipal actor) {
        AcademyCourse c = course(coursePublicId);
        AcademyCourse.Status before = c.getStatus();
        switch (action == null ? "" : action) {
            case "submit-review" -> {
                requireState(c, AcademyCourse.Status.draft, action);
                c.setStatus(AcademyCourse.Status.in_editorial_review);
            }
            case "approve-editorial" -> {
                requireState(c, AcademyCourse.Status.in_editorial_review, action);
                requireRoles(actor, "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_CONTENT_MANAGER");
                if (actor.id().equals(c.getCreatedBy() == null ? null : c.getCreatedBy().getId())) {
                    denyCourse(c, actor, action, "editorial approval requires a reviewer other than the creator");
                }
                saveReview("academy_course", c.getId(), ContentReviewRecord.Stage.editorial_review,
                        ContentReviewRecord.Decision.approved, actor, notes);
                c.setStatus(AcademyCourse.Status.approved);
            }
            case "reject" -> {
                if (notes == null || notes.isBlank()) throw ApiException.validation("rejection requires notes");
                requireState(c, AcademyCourse.Status.in_editorial_review, action);
                requireRoles(actor, "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_CONTENT_MANAGER");
                saveReview("academy_course", c.getId(), ContentReviewRecord.Stage.editorial_review,
                        ContentReviewRecord.Decision.rejected, actor, notes);
                c.setStatus(AcademyCourse.Status.draft);
            }
            case "publish" -> {
                requireState(c, AcademyCourse.Status.approved, action);
                requirePermission(actor, "content.publish", action, c);
                // Permission ≠ publication: attached pdf material must be scan-clean.
                boolean allClean = lessons.lessonsOfCourseInOrder(c.getId()).stream()
                        .filter(l -> l.getMediaAsset() != null)
                        .allMatch(l -> Boolean.TRUE.equals(l.getMediaAsset().isClean()));
                if (!allClean) {
                    denyCourse(c, actor, action, "publish refused: attached pdf material is not scan-clean");
                }
                c.setStatus(AcademyCourse.Status.published);
                c.setPublishedAt(Instant.now());
            }
            case "unpublish" -> {
                requireState(c, AcademyCourse.Status.published, action);
                requirePermission(actor, "content.publish", action, c);
                c.setStatus(AcademyCourse.Status.approved);
            }
            case "archive" -> {
                requireState(c, AcademyCourse.Status.draft, AcademyCourse.Status.approved, action);
                requirePermission(actor, "content.publish", action, c);
                c.setStatus(AcademyCourse.Status.archived);
                c.setArchivedAt(Instant.now());
            }
            default -> throw ApiException.validation("unsupported action");
        }
        audit.record(actor.id(), "academy.course." + action, "academy_course", c.getId(),
                Map.of("state", before.name()), Map.of("state", c.getStatus().name()), notes);
        return new AcademyDtos.TransitionView(c.getPublicId(), before.name(), c.getStatus().name(),
                Instant.now().toString());
    }

    // ---------------------------------------------------------------- user

    @Transactional
    public AcademyDtos.EnrolView enrol(UUID coursePublicId, Long userId) {
        AcademyCourse c = course(coursePublicId);
        if (c.getStatus() != AcademyCourse.Status.published) {
            throw ApiException.notFound("course not found");
        }
        AcademyEnrolment e = enrolments.findByUserIdAndCourseId(userId, c.getId()).orElse(null);
        if (e == null) {
            e = enrolments.save(new AcademyEnrolment(userId, c.getId()));
        }
        return new AcademyDtos.EnrolView(c.getPublicId(), e.getStatus().name(), String.valueOf(e.getEnrolledAt()));
    }

    @Transactional
    public AcademyDtos.ProgressView completeLesson(UUID lessonPublicId, Long userId) {
        AcademyLesson l = lesson(lessonPublicId);
        AcademyCourse c = l.getChapter().getCourse();
        if (c.getStatus() != AcademyCourse.Status.published) throw ApiException.notFound("lesson not found");
        AcademyEnrolment enrolment = enrolments.findByUserIdAndCourseId(userId, c.getId()).orElse(null);
        if (enrolment == null) {
            // Auto-enrol on first completion — keeps progress coherent for the user.
            enrolment = enrolments.save(new AcademyEnrolment(userId, c.getId()));
        }
        progress.save(new AcademyLessonProgress(userId, l.getId()));

        long total = lessons.lessonsOfCourseInOrder(c.getId()).size();
        int done = progress.completedInCourse(userId, c.getId()).size();
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
        if (percent >= 100 && enrolment.getStatus() == AcademyEnrolment.Status.active) {
            enrolment.setStatus(AcademyEnrolment.Status.completed);
            enrolment.setCompletedAt(Instant.now());
        }
        return new AcademyDtos.ProgressView(c.getPublicId(), (int) total, done, percent, enrolment.getStatus().name());
    }

    @Transactional(readOnly = true)
    public AcademyDtos.ProgressView progressOf(UUID coursePublicId, Long userId) {
        AcademyCourse c = course(coursePublicId);
        long total = lessons.lessonsOfCourseInOrder(c.getId()).size();
        int done = progress.completedInCourse(userId, c.getId()).size();
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
        String status = enrolments.findByUserIdAndCourseId(userId, c.getId())
                .map(e -> e.getStatus().name()).orElse("not_enrolled");
        return new AcademyDtos.ProgressView(c.getPublicId(), (int) total, done, percent, status);
    }

    @Transactional(readOnly = true)
    public List<AcademyDtos.ContinueCourse> continueLearning(Long userId, int limit) {
        List<AcademyEnrolment> active = enrolments.findByUserIdOrderByEnrolledAtDesc(userId).stream()
                .filter(e -> e.getStatus() == AcademyEnrolment.Status.active)
                .limit(Math.min(Math.max(limit, 1), 10))
                .toList();
        return active.stream().map(e -> {
            AcademyCourse c = e.getCourse();
            List<AcademyLesson> ordered = lessons.lessonsOfCourseInOrder(c.getId());
            List<Long> doneIds = progress.completedInCourse(userId, c.getId()).stream()
                    .map(AcademyLessonProgress::getLessonId).toList();
            AcademyLesson next = ordered.stream().filter(l -> !doneIds.contains(l.getId())).findFirst().orElse(null);
            int percent = ordered.isEmpty() ? 0 : (int) Math.round(100.0 * doneIds.size() / ordered.size());
            return new AcademyDtos.ContinueCourse(c.getPublicId(), c.getTitle(),
                    new AcademyDtos.SubjectRef(c.getSubject().getSlug(), c.getSubject().getName()),
                    percent, next == null ? null : next.getPublicId(), next == null ? null : next.getTitle());
        }).toList();
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public AcademyCourse course(UUID publicId) {
        return courses.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("course not found"));
    }

    @Transactional(readOnly = true)
    public AcademyChapter chapter(UUID publicId) {
        return chapters.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("chapter not found"));
    }

    @Transactional(readOnly = true)
    public AcademyLesson lesson(UUID publicId) {
        return lessons.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("lesson not found"));
    }

    @Transactional(readOnly = true)
    public Page<AcademyCourse> adminList(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (status != null && !status.isBlank()) {
            return courses.findByStatusOrderByUpdatedAtDesc(parseStatus(status), pr);
        }
        return courses.findAll(pr);
    }

    @Transactional(readOnly = true)
    public Page<AcademyCourse> publicBrowse(int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        return courses.findByStatusOrderByUpdatedAtDesc(AcademyCourse.Status.published, pr);
    }

    @Transactional(readOnly = true)
    public List<AcademyChapter> chaptersOf(UUID coursePublicId) {
        return chapters.findByCourseIdOrderBySortOrderAsc(course(coursePublicId).getId());
    }

    @Transactional(readOnly = true)
    public long enrolmentCount(Long courseId) {
        return enrolments.countByCourseId(courseId);
    }

    // ---------------------------------------------------------------- mapping

    public AcademyDtos.CourseAdminRow toAdminRow(AcademyCourse c) {
        int chapterCount = chapters.findByCourseIdOrderBySortOrderAsc(c.getId()).size();
        int lessonCount = lessons.lessonsOfCourseInOrder(c.getId()).size();
        return new AcademyDtos.CourseAdminRow(c.getPublicId(), c.getTitle(), c.getLanguage(),
                c.getLevel().name(), c.getStatus().name(),
                new AcademyDtos.SubjectRef(c.getSubject().getSlug(), c.getSubject().getName()),
                c.getEstimatedHours(), c.getInstructorDisplayName(), chapterCount, lessonCount,
                enrolmentCount(c.getId()), String.valueOf(c.getUpdatedAt()));
    }

    public AcademyDtos.CourseAdminDetail toAdminDetail(AcademyCourse c) {
        List<AcademyDtos.ChapterAdminView> chs = chapters.findByCourseIdOrderBySortOrderAsc(c.getId())
                .stream().map(ch -> new AcademyDtos.ChapterAdminView(ch.getPublicId(), ch.getTitle(),
                        ch.getDescription(), ch.getSortOrder(),
                        lessons.lessonsOfChapter(ch.getId()).stream()
                                .map(this::toAdminLesson).toList()))
                .toList();
        return new AcademyDtos.CourseAdminDetail(c.getPublicId(), c.getTitle(), c.getDescription(),
                c.getLanguage(), c.getLevel().name(), c.getStatus().name(),
                new AcademyDtos.SubjectRef(c.getSubject().getSlug(), c.getSubject().getName()),
                c.getInstructorDisplayName(), c.getInstructorCredentialsNote(), c.getEstimatedHours(), chs);
    }

    public AcademyDtos.LessonAdminView toAdminLesson(AcademyLesson l) {
        return new AcademyDtos.LessonAdminView(l.getPublicId(), l.getTitle(), l.getLessonType().name(),
                l.getSortOrder(), l.getDurationMinutes(),
                l.getMediaAsset() == null ? null : l.getMediaAsset().getPublicId(),
                l.getQuiz() == null ? null : l.getQuiz().getPublicId(),
                l.getFlashcardDeck() == null ? null : l.getFlashcardDeck().getPublicId(),
                l.getVideoUrl());
    }

    public AcademyDtos.CourseRow toPublicRow(AcademyCourse c) {
        int lessonCount = lessons.lessonsOfCourseInOrder(c.getId()).size();
        return new AcademyDtos.CourseRow(c.getPublicId(), c.getTitle(), c.getDescription(), c.getLanguage(),
                c.getLevel().name(),
                new AcademyDtos.SubjectRef(c.getSubject().getSlug(), c.getSubject().getName()),
                c.getEstimatedHours(), c.getInstructorDisplayName(), lessonCount,
                String.valueOf(c.getPublishedAt()));
    }

    public AcademyDtos.CourseDetail toPublicDetail(AcademyCourse c) {
        List<AcademyDtos.ChapterPublicView> chs = chapters.findByCourseIdOrderBySortOrderAsc(c.getId())
                .stream().map(ch -> new AcademyDtos.ChapterPublicView(ch.getPublicId(), ch.getTitle(),
                        ch.getDescription(), lessons.lessonsOfChapter(ch.getId()).stream()
                                .map(this::toPublicLesson).toList()))
                .toList();
        return new AcademyDtos.CourseDetail(c.getPublicId(), c.getTitle(), c.getDescription(),
                c.getLanguage(), c.getLevel().name(),
                new AcademyDtos.SubjectRef(c.getSubject().getSlug(), c.getSubject().getName()),
                c.getEstimatedHours(), c.getInstructorDisplayName(), c.getInstructorCredentialsNote(), chs);
    }

    public AcademyDtos.LessonPublicView toPublicLesson(AcademyLesson l) {
        return new AcademyDtos.LessonPublicView(l.getPublicId(), l.getTitle(), l.getLessonType().name(),
                l.getSortOrder(), l.getDurationMinutes(),
                l.getQuiz() == null ? null : l.getQuiz().getPublicId(),
                l.getFlashcardDeck() == null ? null : l.getFlashcardDeck().getPublicId(),
                l.getBodyText(), l.getVideoUrl(),
                l.getMediaAsset() == null ? null : "/api/v1/files/" + l.getMediaAsset().getPublicId());
    }

    // ---------------------------------------------------------------- helpers

    private void apply(AcademyCourse c, AcademyDtos.CourseRequest r, Subject subject) {
        c.setSubject(subject);
        c.setTitle(r.title());
        c.setDescription(r.description());
        c.setLanguage(r.language() == null ? "en" : r.language());
        c.setLevel(r.level() == null ? AcademyCourse.Level.beginner : AcademyCourse.Level.valueOf(r.level()));
        c.setInstructorDisplayName(r.instructorDisplayName());
        c.setInstructorCredentialsNote(r.instructorCredentialsNote());
        c.setEstimatedHours(r.estimatedHours());
    }

    private void applyLesson(AcademyLesson l, AcademyDtos.LessonRequest r) {
        AcademyLesson.LessonType type = AcademyLesson.LessonType.valueOf(r.lessonType());
        l.setTitle(r.title());
        l.setLessonType(type);
        l.setBodyText(r.bodyText());
        l.setVideoUrl(r.videoUrl());
        l.setDurationMinutes(r.durationMinutes());
        l.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
        l.setQuiz(r.quizId() == null ? null : quizRepo.findByPublicId(r.quizId())
                .orElseThrow(() -> ApiException.validation("quiz not found")));
        l.setFlashcardDeck(r.flashcardDeckId() == null ? null : deckRepo.findByPublicId(r.flashcardDeckId())
                .orElseThrow(() -> ApiException.validation("flashcard deck not found")));
        if (r.mediaId() != null) {
            var asset = media.findByPublicId(r.mediaId())
                    .orElseThrow(() -> ApiException.validation("media asset not found"));
            if (type == AcademyLesson.LessonType.pdf && !asset.isClean()) {
                throw ApiException.of("UPLOAD-002", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "pdf material must be scan-clean before it can be attached (UPLOAD-002)");
            }
            l.setMediaAsset(asset);
        } else {
            l.setMediaAsset(null);
        }
        if (type == AcademyLesson.LessonType.quiz && l.getQuiz() == null) {
            throw ApiException.validation("quiz lessons require quizId");
        }
        if (type == AcademyLesson.LessonType.flashcards && l.getFlashcardDeck() == null) {
            throw ApiException.validation("flashcards lessons require flashcardDeckId");
        }
    }

    private int nextChapterOrder(Long courseId) {
        return chapters.findByCourseIdOrderBySortOrderAsc(courseId).size();
    }

    private void requireDraft(AcademyCourse c, String verb) {
        if (c.getStatus() != AcademyCourse.Status.draft) {
            throw ApiException.workflow("course state " + c.getStatus() + " does not permit to " + verb
                    + " (draft only)");
        }
    }

    private void requireState(AcademyCourse c, AcademyCourse.Status expected, String action) {
        if (c.getStatus() != expected) {
            denyCourse(c, null, action, "state " + c.getStatus() + " does not permit " + action
                    + " (requires " + expected + ")");
        }
    }

    private void requireState(AcademyCourse c, AcademyCourse.Status e1, AcademyCourse.Status e2, String action) {
        if (c.getStatus() != e1 && c.getStatus() != e2) {
            denyCourse(c, null, action, "state " + c.getStatus() + " does not permit " + action);
        }
    }

    private void requireRoles(SecurityPrincipal actor, String... roles) {
        List<String> wanted = List.of(roles);
        boolean ok = actor.authorities().stream().anyMatch(a -> wanted.contains(a.getAuthority()));
        if (!ok) {
            throw ApiException.forbidden("editorial decisions require Super Admin, Admin or Content Manager");
        }
    }

    private void requirePermission(SecurityPrincipal actor, String permission, String action, AcademyCourse c) {
        boolean has = actor.authorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
        if (!has) {
            denyCourse(c, actor, action, "actor lacks permission " + permission);
        }
    }

    private void denyCourse(AcademyCourse c, SecurityPrincipal actor, String action, String detail) {
        audit.recordDenial("academy.workflow.deny", "academy_course", c.getId(), action + ": " + detail);
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

    private AcademyCourse.Status parseStatus(String status) {
        try {
            return AcademyCourse.Status.valueOf(status.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("unknown status: " + status);
        }
    }
}
