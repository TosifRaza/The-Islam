package app.ikp.backend.academy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AcademyLessonProgressRepository extends JpaRepository<AcademyLessonProgress, AcademyLessonProgress.PK> {

    @Query("""
            SELECT p FROM AcademyLessonProgress p
            JOIN AcademyLesson l ON l.id = p.lessonId
            JOIN AcademyChapter c ON c.id = l.chapter.id
            WHERE p.userId = :userId AND c.course.id = :courseId
            """)
    List<AcademyLessonProgress> completedInCourse(Long userId, Long courseId);
}
