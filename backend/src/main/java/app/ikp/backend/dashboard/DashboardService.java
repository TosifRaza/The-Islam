package app.ikp.backend.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard aggregation over real PostgreSQL data (M2 Module A). Zero states
 * are honest zeros for an empty database. Sections per approved spec §3.19.1:
 * users / content / review (incl. awaiting-approval queue) / recent activity.
 */
@Service
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {
        Map<String, Object> data = new LinkedHashMap<>();

        // --- users ---
        Long totalUsers = q("SELECT COUNT(*) FROM users");
        Long activeUsers = q("SELECT COUNT(*) FROM users WHERE status = 'active'");
        Long newRegistrations = q("SELECT COUNT(*) FROM users WHERE created_at >= now() - interval '30 days'");
        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", totalUsers);
        users.put("active", activeUsers);
        users.put("newRegistrations", newRegistrations);
        data.put("users", users);

        // --- content ---
        Long totalSubjects = q("SELECT COUNT(*) FROM subjects");
        Long publishedBooks = q("SELECT COUNT(*) FROM books WHERE status = 'published'");
        Long draftBooks = q("SELECT COUNT(*) FROM books WHERE status = 'draft'");
        Long publishedContent = publishedBooks; // books are the published content type in M2
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("subjects", totalSubjects);
        content.put("books", q("SELECT COUNT(*) FROM books"));
        content.put("publishedBooks", publishedBooks);
        content.put("draftBooks", draftBooks);
        content.put("publishedContent", publishedContent);
        content.put("uploads", q("SELECT COUNT(*) FROM media_assets"));
        // M5: academy and research are live content types now (counts are real)
        content.put("courses", q("SELECT COUNT(*) FROM academy_courses"));
        content.put("publishedCourses", q("SELECT COUNT(*) FROM academy_courses WHERE status = 'published'"));
        content.put("researchArticles", q("SELECT COUNT(*) FROM research_articles"));
        content.put("enrolments", q("SELECT COUNT(*) FROM academy_enrolments"));
        content.put("assessmentAttempts", q("SELECT COUNT(*) FROM assessment_attempts"));
        content.put("quranStats", Map.of("imported", false, "note", "import gated at OD-05/G-3"));
        content.put("hadithStats", Map.of("imported", false, "note", "import gated at OD-05/G-3"));
        data.put("content", content);

        // --- review queues ---
        Long pendingBookEditorial = q("SELECT COUNT(*) FROM books WHERE status = 'in_editorial_review'");
        Long pendingBookScholarly = q("SELECT COUNT(*) FROM books WHERE status = 'in_scholarly_review'");
        Long pendingFatwaReview = q("SELECT COUNT(*) FROM fatwas WHERE state = 'in_review'");
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("pendingBookEditorial", pendingBookEditorial);
        review.put("pendingBookScholarly", pendingBookScholarly);
        review.put("pendingFatwaReview", pendingFatwaReview);
        review.put("awaitingApproval", approvalQueue());
        data.put("review", review);

        // --- recent administrative activity ---
        data.put("recentAdminActivity", recentActivity());

        return data;
    }

    private List<Map<String, Object>> approvalQueue() {
        String sql = """
                (SELECT b.public_id::text AS entity_id, 'books' AS entity, b.title AS title,
                        b.status::text AS state, b.subject_id, b.updated_at
                 FROM books b WHERE b.status IN ('in_editorial_review','in_scholarly_review','approved')
                 ORDER BY b.updated_at DESC LIMIT 20)
                UNION ALL
                (SELECT f.public_id::text, 'fatwas', left(f.question, 120), f.state::text, NULL, f.updated_at
                 FROM fatwas f WHERE f.state = 'in_review'
                 ORDER BY f.updated_at DESC LIMIT 20)
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.queryForList(sql).forEach(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity", r.get("entity"));
            row.put("entityId", r.get("entity_id"));
            row.put("title", r.get("title"));
            row.put("state", r.get("state"));
            row.put("waitingDays", r.get("updated_at") == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(
                            ((java.sql.Timestamp) r.get("updated_at")).toInstant(),
                            java.time.Instant.now()));
            rows.add(row);
        });
        return rows;
    }

    private List<Map<String, Object>> recentActivity() {
        String sql = """
                SELECT a.created_at, a.action, a.entity_type, a.entity_id, u.email AS actor
                FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
                ORDER BY a.id DESC LIMIT 10
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.queryForList(sql).forEach(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", r.get("created_at") == null ? null : ((java.sql.Timestamp) r.get("created_at")).toInstant().toString());
            row.put("action", r.get("action"));
            row.put("entity", r.get("entity_type"));
            row.put("entityId", r.get("entity_id") == null ? null : String.valueOf(r.get("entity_id")));
            row.put("actor", r.get("actor"));
            rows.add(row);
        });
        return rows;
    }

    private Long q(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }
}
