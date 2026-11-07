package com.ikp.dev.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * M5 learning & research DTOs — exact backend contract (Modules 10, 11, 13, 14).
 * Safety invariant mirrored client-side: the pre-submission question payload
 * ({@link QuestionPublicDto}) carries NO correct-flag and NO explanation —
 * those exist only in {@link AnswerResultDto} after server-side grading.
 * Gson is configured with serializeNulls; field names match the API.
 */
public final class AcademyDtos {

    private AcademyDtos() { }

    // ----- academy -----

    public static final class SubjectRefDto {
        public String slug;
        public String name;
    }

    public static final class CourseRowDto {
        @SerializedName(value = "courseId", alternate = {"course_id", "id", "public_id"})
        public String courseId;
        public String title;
        public String description;
        public String language;
        public String level;
        public SubjectRefDto subject;
        @SerializedName(value = "estimatedHours", alternate = "estimated_hours")
        public Integer estimatedHours;
        @SerializedName(value = "instructorDisplayName", alternate = {"instructor_display_name", "instructor_name"})
        public String instructorDisplayName;
        @SerializedName(value = "lessonCount", alternate = "lesson_count")
        public Integer lessonCount;
        @SerializedName(value = "publishedAt", alternate = "published_at")
        public String publishedAt;
    }

    public static final class LessonPublicDto {
        public String lessonId;
        public String title;
        public String lessonType; // text | video | pdf | quiz | flashcards
        public Integer sortOrder;
        public Integer durationMinutes;
        public String quizId;
        public String flashcardDeckId;
        public String bodyText;
        public String videoUrl;
        @SerializedName(value = "mediaUrl", alternate = "media_url")
        public String mediaUrl;
    }

    public static final class ChapterPublicDto {
        public String chapterId;
        public String title;
        public String description;
        public List<LessonPublicDto> lessons;
    }

    public static final class CourseDetailDto {
        public String courseId;
        public String title;
        public String description;
        public String language;
        public String level;
        public SubjectRefDto subject;
        public Integer estimatedHours;
        public String instructorDisplayName;
        public String instructorCredentialsNote;
        public List<ChapterPublicDto> chapters;
    }

    public static final class EnrolDto {
        public String courseId;
        public String status;
        public String enrolledAt;
    }

    public static final class ProgressDto {
        public String courseId;
        public Integer totalLessons;
        public Integer completedLessons;
        public Integer percent;
        public String status; // not_enrolled | active | completed
    }

    public static final class ContinueDto {
        public String courseId;
        public String title;
        public SubjectRefDto subject;
        public Integer percent;
        public String nextLessonId;
        public String nextLessonTitle;
    }

    // ----- assessments -----

    public static final class QuizRowDto {
        public String quizId;
        public String title;
        public String description;
        public String language;
        public Integer questionCount;
        public String subjectSlug;
        public Integer timeLimitSeconds;
    }

    public static final class OptionPublicDto {
        public String optionId;
        public String optionText;
    }

    /** Pre-submission payload — deliberately has no correct flag / explanation. */
    public static final class QuestionPublicDto {
        public String questionId;
        public String prompt;
        public String promptAr;
        public Integer points;
        public Integer sortOrder;
        public List<OptionPublicDto> options;
    }

    public static final class AttemptStartedDto {
        public String attemptId;
        public String quizId;
        public String startedAt;
        public Integer timeLimitSeconds;
        public List<QuestionPublicDto> questions;
    }

    public static final class AnswerResultDto {
        public String questionId;
        public String selectedOptionId;
        public String correctOptionId;
        public Boolean correct;
        public String explanation;
    }

    public static final class AttemptResultDto {
        public String attemptId;
        public String quizId;
        public String status;
        public Integer scorePct;
        public Boolean passed;
        public Integer passScorePct;
        public List<AnswerResultDto> answers;
        public String submittedAt;
    }

    public static final class AttemptRowDto {
        public String attemptId;
        public String quizId;
        public String quizTitle;
        public String status;
        public Integer scorePct;
        public Boolean passed;
        public String startedAt;
        public String submittedAt;
    }

    public static final class WeakTopicDto {
        public String subjectSlug;
        public String label;
        public Integer wrong;
        public Integer total;
        public Integer wrongPct;
    }

    // ----- flashcards -----

    public static final class CardDto {
        public String cardId;
        public String frontText;
        public String backText;
        public String hint;
        public Integer sortOrder;
    }

    public static final class DeckRowDto {
        public String deckId;
        public String title;
        public String description;
        public String language;
        public String subjectSlug;
        public Integer cardCount;
        public String ownerType; // admin | user
        public String publishState;
        public String updatedAt;
    }

    public static final class DeckDetailDto {
        public String deckId;
        public String title;
        public String description;
        public String language;
        public String subjectSlug;
        public String ownerType;
        public String publishState;
        public List<CardDto> cards;
    }

    // ----- research / scholars / fatwas -----

    public static final class TopicDto {
        public String slug;
        public String name;
        public String nameAr;
        public String description;
        public Integer sortOrder;
        public String status;
    }

    public static final class ArticleTopicDto {
        public String slug;
        public String name;
    }

    public static final class ArticleRowDto {
        public String articleId;
        public String title;
        public ArticleTopicDto topic;
        public String sourceTier; // primary | secondary | editorial
        public String status;
        public String language;
        public String authorDisplayName;
        public String updatedAt;
    }

    public static final class CitationDto {
        public Integer citationId;
        public String citationKind; // quran_ayah | hadith | book | research_article | external
        public Integer surahId;
        public Integer ayahNumber;
        public Long hadithId;
        public String bookPublicId;
        public String articlePublicId;
        public String externalRef;
        public String quote;
        public String note;
    }

    public static final class ArticleDetailDto {
        public String articleId;
        public String title;
        public String titleAr;
        public String summary;
        public String body;
        public String language;
        public String authorDisplayName;
        public String sourceTier;
        public String status;
        public ArticleTopicDto topic;
        public List<CitationDto> citations;
    }

    public static final class ScholarDto {
        public String scholarId;
        public String name;
        public String nameAr;
        public String biography;
        public String qualifications;
        public String areasOfExpertise;
        public String verificationStatus; // unverified | verified | revoked
        public String verifiedAt;
    }

    public static final class FatwaRowDto {
        public String fatwaId;
        public String question;
        public String category;
        public String language;
        public String publicationDate;
    }

    public static final class FatwaReviewDto {
        public String reviewDate;
        public String reviewNotes;
        public ScholarDto scholar;
    }

    public static final class FatwaDetailDto {
        public String fatwaId;
        public String question;
        public String category;
        public String language;
        public String publicationDate;
        public String answer;
        public Object sourceRefs;
        public FatwaReviewDto review;
    }
}
