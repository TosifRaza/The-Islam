package app.ikp.backend.academy;

import app.ikp.backend.assessment.AssessmentQuiz;
import app.ikp.backend.assessment.FlashcardDeck;
import app.ikp.backend.storage.MediaAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Lesson inside a chapter. lesson_type decides which payload field carries the
 * content: text → bodyText, video → videoUrl (external reference only, OD-07:
 * no paid hosting is activated), pdf → mediaAsset (scan-gated storage),
 * quiz → quiz, flashcards → flashcardDeck.
 */
@Entity
@Table(name = "academy_lessons")
public class AcademyLesson {

    public enum LessonType { text, video, pdf, quiz, flashcards }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private AcademyChapter chapter;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "lesson_type", nullable = false, length = 20)
    private LessonType lessonType = LessonType.text;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "media_asset_id")
    private MediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quiz_id")
    private AssessmentQuiz quiz;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flashcard_deck_id")
    private FlashcardDeck flashcardDeck;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void defaults() {
        if (publicId == null) publicId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public AcademyChapter getChapter() { return chapter; }
    public void setChapter(AcademyChapter chapter) { this.chapter = chapter; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LessonType getLessonType() { return lessonType; }
    public void setLessonType(LessonType lessonType) { this.lessonType = lessonType; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public MediaAsset getMediaAsset() { return mediaAsset; }
    public void setMediaAsset(MediaAsset mediaAsset) { this.mediaAsset = mediaAsset; }
    public AssessmentQuiz getQuiz() { return quiz; }
    public void setQuiz(AssessmentQuiz quiz) { this.quiz = quiz; }
    public FlashcardDeck getFlashcardDeck() { return flashcardDeck; }
    public void setFlashcardDeck(FlashcardDeck flashcardDeck) { this.flashcardDeck = flashcardDeck; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
