package app.ikp.backend.academy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademyLessonRepository extends JpaRepository<AcademyLesson, Long> {

    Optional<AcademyLesson> findByPublicId(UUID publicId);

    @Query("""
            SELECT l FROM AcademyLesson l
            JOIN AcademyChapter c ON c.id = l.chapter.id
            WHERE c.course.id = :courseId
            ORDER BY c.sortOrder ASC, l.sortOrder ASC
            """)
    List<AcademyLesson> lessonsOfCourseInOrder(Long courseId);

    @Query("SELECT l FROM AcademyLesson l WHERE l.chapter.id = :chapterId ORDER BY l.sortOrder ASC")
    List<AcademyLesson> lessonsOfChapter(Long chapterId);
}
