package app.ikp.backend.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration foundation evidence (M1):
 * applies the approved forward-only migrations to a REAL PostgreSQL instance
 * (embedded, no Docker) twice — proving versioned migrations are applied once
 * and repeatable seeds re-run idempotently — then asserts the seeded reference
 * data matches the approved blueprint:
 *   - exactly 11 platform roles;
 *   - Fatwa separation of duties (no role holds both publish and approve);
 *   - 15 library-category positions with stable slugs, position 06 hidden;
 *   - Arabic Language Academy at position 07 (audit fix intact).
 * No religious text content exists anywhere (import is gated by G-3/OD-05).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationTest {

    private EmbeddedPostgres pg;
    private JdbcTemplate jdbc;

    @BeforeAll
    void start() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        jdbc = new JdbcTemplate(ds);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration/identity", "classpath:db/migration/content")
                .load();
        flyway.migrate();
        flyway.migrate(); // idempotency: repeatable seeds must re-apply cleanly
    }

    @AfterAll
    void stop() throws IOException {
        if (pg != null) pg.close();
    }

    @Test
    void elevenRolesSeeded() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        assertThat(count).isEqualTo(11);
        List<String> codes = jdbc.queryForList("SELECT code FROM roles ORDER BY id", String.class);
        assertThat(codes).containsExactly(
                "guest", "registered_user", "super_admin", "admin", "content_manager",
                "quran_editor", "hadith_editor", "research_editor", "scholar_reviewer",
                "academy_manager", "support_manager");
    }

    @Test
    void fatwaDutiesAreDisjoint() {
        List<Map<String, Object>> violators = jdbc.queryForList("""
                SELECT r.code, COUNT(DISTINCT p.code) AS held
                FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                JOIN roles r ON r.id = rp.role_id
                WHERE p.code IN ('fatwa.publish','fatwa.review.approve')
                GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 2
                """);
        assertThat(violators).isEmpty();
        Integer scholars = jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                JOIN roles r ON r.id = rp.role_id
                WHERE r.code = 'scholar_reviewer' AND p.code = 'fatwa.review.approve'
                """, Integer.class);
        assertThat(scholars).isEqualTo(1); // sole review authority
    }

    @Test
    void fifteenCategoryPositionsWithStableSlugs() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM subjects", Integer.class);
        assertThat(count).isEqualTo(15);

        Map<String, Object> pos06 = jdbc.queryForMap(
                "SELECT slug, status FROM subjects WHERE sort_order = 6");
        assertThat(pos06.get("slug")).isEqualTo("cat_unconfirmed_06");
        assertThat(pos06.get("status")).isEqualTo("hidden_unconfirmed");

        Map<String, Object> pos07 = jdbc.queryForMap(
                "SELECT slug, name FROM subjects WHERE sort_order = 7");
        assertThat(pos07.get("slug")).isEqualTo("cat_arabic_academy");
        assertThat(pos07.get("name")).isEqualTo("Arabic Language Academy");

        // slug join keys are position-independent
        Integer hiddenBySlug = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE slug = 'cat_unconfirmed_06' AND status = 'hidden_unconfirmed'",
                Integer.class);
        assertThat(hiddenBySlug).isEqualTo(1);
    }

    @Test
    void noUnauthorisedContentStoresExist() {
        // M2 contract: bare-name text stores were forbidden before M4. M4
        // introduces the governed containers (quran_surahs/quran_ayahs/hadiths/
        // duas) whose content arrives ONLY through the controlled import runner
        // with manifest verification (SR-01/SR-08) — the AI subsystem remains
        // forbidden entirely (no ai_chunks before the RAG milestone).
        Integer forbidden = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('surahs','ayahs','ai_chunks','quran_text')
                """, Integer.class);
        assertThat(forbidden).isZero();
        Integer batches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM import_batches", Integer.class);
        assertThat(batches).isZero();
        Integer bookRows = jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class);
        assertThat(bookRows).isZero();
    }

    @Test
    void migrationsAreRecorded() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IS NOT NULL",
                Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(4); // V1..V4 (M2 admin CMS included)
    }

    @Test
    void m2RbacExpansionMatchesApprovedMatrix() {
        // V3: new permission codes exist and no established M1 grant was lost.
        List<String> perms = jdbc.queryForList("SELECT code FROM permissions ORDER BY code", String.class);
        org.assertj.core.api.Assertions.assertThat(perms).contains(
                "content.edit", "content.publish", "fatwa.publish", "fatwa.review.approve",
                "users.manage", "roles.manage", "academy.manage", "support.view_users",
                "users.view", "users.suspend", "users.delete", "audit.view", "files.upload", "fatwa.create");
        Integer supportSuspend = jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id
                WHERE r.code = 'support_manager' AND p.code = 'users.suspend'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(supportSuspend).isEqualTo(1);
        // Fatwa disjointness still holds after the expansion.
        List<Map<String, Object>> violators = jdbc.queryForList("""
                SELECT r.code, COUNT(DISTINCT p.code) AS held
                FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                JOIN roles r ON r.id = rp.role_id
                WHERE p.code IN ('fatwa.publish','fatwa.review.approve')
                GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 2
                """);
        org.assertj.core.api.Assertions.assertThat(violators).isEmpty();
        // Content Manager never received Fatwa draft creation (stricter matrix).
        Integer cmFatwa = jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id
                WHERE r.code = 'content_manager' AND p.code = 'fatwa.create'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(cmFatwa).isZero();
    }

    @Test
    void m2SchemaObjectsExist() {
        for (String table : new String[]{"authors", "books", "book_authors", "book_files",
                "fatwas", "account_deletion_requests"}) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = ?",
                    Integer.class, table);
            org.assertj.core.api.Assertions.assertThat(n).isEqualTo(1);
        }
        // reference licences seeded (approved reference data only)
        Integer licences = jdbc.queryForObject("SELECT COUNT(*) FROM licences", Integer.class);
        org.assertj.core.api.Assertions.assertThat(licences).isGreaterThanOrEqualTo(3);
    }
}
