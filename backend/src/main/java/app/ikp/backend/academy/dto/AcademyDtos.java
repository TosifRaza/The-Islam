package app.ikp.backend.academy.dto;

import app.ikp.backend.academy.AcademyCourse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M5 Academy contracts (records; envelope {"data", "meta"} per approved conventions). */
public final class AcademyDtos {

    private AcademyDtos() { }

    public record CourseRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 20000) String description,
            @Pattern(regexp = "ar|en|ur|hi") String language,
            @Pattern(regexp = "beginner|intermediate|advanced") String level,
            @NotBlank String subjectSlug,
            @Size(max = 200) String instructorDisplayName,
            @Size(max = 500) String instructorCredentialsNote,
            @Min(0) @Max(1000) Integer estimatedHours) { }

    public record ChapterRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 2000) String description,
            @Min(0) @Max(10000) Integer sortOrder) { }

    public record LessonRequest(
            @NotBlank @Size(max = 300) String title,
            @NotBlank @Pattern(regexp = "text|video|pdf|quiz|flashcards") String lessonType,
            @Size(max = 50000) String bodyText,
            @Size(max = 500) String videoUrl,
            UUID mediaId,
            UUID quizId,
            UUID flashcardDeckId,
            @Min(0) @Max(600) Integer durationMinutes,
            @Min(0) @Max(10000) Integer sortOrder) { }

    public record SubjectRef(String slug, String name) { }

    public record CourseAdminRow(UUID courseId, String title, String language, String level,
                                 String status, SubjectRef subject, Integer estimatedHours,
                                 String instructorDisplayName, int chapterCount, int lessonCount,
                                 long enrolmentCount, String updatedAt) { }

    public record ChapterAdminView(UUID chapterId, String title, String description,
                                   int sortOrder, List<LessonAdminView> lessons) { }

    public record LessonAdminView(UUID lessonId, String title, String lessonType, int sortOrder,
                                  Integer durationMinutes, UUID mediaId, UUID quizId, UUID flashcardDeckId,
                                  String videoUrl) { }

    public record CourseAdminDetail(UUID courseId, String title, String description, String language,
                                    String level, String status, SubjectRef subject,
                                    String instructorDisplayName, String instructorCredentialsNote,
                                    Integer estimatedHours, List<ChapterAdminView> chapters) { }

    public record CourseRow(UUID courseId, String title, String description, String language,
                            String level, SubjectRef subject, Integer estimatedHours,
                            String instructorDisplayName, int lessonCount, String publishedAt) { }

    public record LessonPublicView(UUID lessonId, String title, String lessonType, int sortOrder,
                                   Integer durationMinutes, UUID quizId, UUID flashcardDeckId,
                                   String bodyText, String videoUrl, String mediaPath) { }

    public record ChapterPublicView(UUID chapterId, String title, String description,
                                    List<LessonPublicView> lessons) { }

    public record CourseDetail(UUID courseId, String title, String description, String language,
                               String level, SubjectRef subject, Integer estimatedHours,
                               String instructorDisplayName, String instructorCredentialsNote,
                               List<ChapterPublicView> chapters) { }

    public record EnrolView(UUID courseId, String status, String enrolledAt) { }

    public record ProgressView(UUID courseId, int totalLessons, int completedLessons,
                               int percent, String status) { }

    public record ContinueCourse(UUID courseId, String title, SubjectRef subject, int percent,
                                 UUID nextLessonId, String nextLessonTitle) { }

    public record TransitionView(UUID courseId, String before, String after, String changedAt) { }

    public static Map<String, Object> auditSnapshot(AcademyCourse c) {
        return Map.of("title", c.getTitle(), "status", c.getStatus().name(),
                "subject", c.getSubject().getSlug(), "level", c.getLevel().name());
    }
}
