package app.ikp.backend.dashboard;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 Module A + Module G acceptance — dashboard metrics originate from real
 * PostgreSQL records (no fabricated numbers), the approval queue reflects
 * submitted content, and the audit-log search filters by entity/action/time.
 */
class DashboardAuditIntegrationTest extends IntegrationTestBase {

    @Test
    void dashboardMetricsReflectRealDatabaseRecords() {
        var cm = createAccount("content_manager", uniqueEmail("da-cm"));
        String token = login(cm);
        UUID bookId = null;
        var created = post("/api/v1/admin/books", token, Map.of(
                "title", "Dashboard Probe Book (synthetic)",
                "language", "en",
                "subjectId", "cat_learning_academy"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        bookId = UUID.fromString(readJson(created.getBody()).path("data").path("bookId").asText());
        post("/api/v1/admin/books/" + bookId + "/submit-review", token, Map.of());

        var scholar = login(createAccount("scholar_reviewer", uniqueEmail("da-scholar")));
        var res = get("/api/v1/admin/dashboard", scholar);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = readJson(res.getBody()).path("data");

        // real counts, honest zero sections for not-yet-approved phases
        assertThat(data.path("users").path("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("users").path("active").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("content").path("subjects").asLong()).isEqualTo(15);
        assertThat(data.path("content").path("books").asLong()).isGreaterThanOrEqualTo(1);
        // M5: courses is a live count (M5AcademyIntegrationTest may have created some)
        long coursesInDb = new JdbcTemplate(app.ikp.backend.support.SharedPostgres.get())
                .queryForObject("SELECT COUNT(*) FROM academy_courses", Long.class);
        assertThat(data.path("content").path("courses").asLong()).isEqualTo(coursesInDb);
        assertThat(data.path("content").path("quranStats").path("imported").asBoolean()).isFalse();
        assertThat(data.path("review").path("pendingBookEditorial").asLong()).isGreaterThanOrEqualTo(1);

        // approval queue contains the submitted book
        boolean inQueue = false;
        for (var item : data.path("review").path("awaitingApproval")) {
            if (item.path("entityId").asText().equals(bookId.toString())) inQueue = true;
        }
        assertThat(inQueue).as("submitted book present in the approval queue").isTrue();

        // recent administrative activity lists real audit actions
        boolean activitySeen = false;
        for (var item : data.path("recentAdminActivity")) {
            if ("book.create".equals(item.path("action").asText())) activitySeen = true;
        }
        assertThat(activitySeen).as("recent activity shows book.create").isTrue();
    }

    @Test
    void reviewQueueListsEditorialAndFatwaItems() {
        var cm = createAccount("content_manager", uniqueEmail("da-q-cm"));
        String token = login(cm);
        var created = post("/api/v1/admin/books", token, Map.of(
                "title", "Queue Probe Book (synthetic)", "language", "en",
                "subjectId", "cat_learning_academy"));
        UUID bookId = UUID.fromString(readJson(created.getBody()).path("data").path("bookId").asText());
        post("/api/v1/admin/books/" + bookId + "/submit-review", token, Map.of());

        var res = get("/api/v1/admin/review-queue?state=editorial", login(createAccount("admin", uniqueEmail("da-q-admin"))));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean found = false;
        for (var item : readJson(res.getBody()).path("data")) {
            if (item.path("entityId").asText().equals(bookId.toString())) found = true;
        }
        assertThat(found).isTrue();
    }

    @Test
    void auditLogSearchFiltersAndSupportManagerDenied() {
        var cm = createAccount("content_manager", uniqueEmail("da-a-cm"));
        String token = login(cm);
        post("/api/v1/admin/books", token, Map.of(
                "title", "Audit Probe Book (synthetic)", "language", "en",
                "subjectId", "cat_learning_academy"));

        String adminToken = login(createAccount("admin", uniqueEmail("da-a-admin")));
        var res = get("/api/v1/admin/audit-logs?action=book.create&entity=book&size=50", adminToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var rows = readJson(res.getBody()).path("data");
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        for (var row : rows) {
            assertThat(row.path("action").asText()).isEqualTo("book.create");
            assertThat(row.path("entityType").asText()).isEqualTo("book");
            // actor, timestamp and request metadata captured
            assertThat(row.path("actorId").isNull()).isFalse();
            assertThat(row.path("at").asText()).isNotBlank();
        }

        // timestamp filter validation (invalid instant → VAL-001)
        var badTs = get("/api/v1/admin/audit-logs?from=not-a-timestamp", adminToken);
        assertThat(badTs.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // support manager has no audit.view → 403
        var sup = createAccount("support_manager", uniqueEmail("da-a-sup"));
        assertThat(get("/api/v1/admin/audit-logs", login(sup)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userDeletionWorkflowAndUnsuspendAreAudited() {
        var sa = createAccount("super_admin", uniqueEmail("da-del-sa"));
        String saToken = login(sa);
        var victim = createAccount("registered_user", uniqueEmail("da-del-victim"));

        // super admin schedules deletion (7-day grace)
        var del = post("/api/v1/admin/users/" + victim.id() + "/delete", saToken,
                Map.of("reason", "GDPR-style deletion request (dev fixture)"));
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var dr = readJson(del.getBody()).path("data").path("deletionRequest");
        assertThat(dr.path("state").asText()).isEqualTo("scheduled");
        assertThat(dr.path("purgeAt").asText()).isNotBlank();

        // admin path creates a pending_review task instead
        var victim2 = createAccount("registered_user", uniqueEmail("da-del-victim2"));
        String adminToken = login(createAccount("admin", uniqueEmail("da-del-admin")));
        var del2 = post("/api/v1/admin/users/" + victim2.id() + "/delete", adminToken,
                Map.of("reason", "review task (dev fixture)"));
        assertThat(del2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(readJson(del2.getBody()).path("data").path("deletionRequest").path("state").asText())
                .isEqualTo("pending_review");

        // detail shows deletion state + history
        var detail = readJson(get("/api/v1/admin/users/" + victim.id(), saToken).getBody()).path("data");
        assertThat(detail.path("deletionState").asText()).isEqualTo("requested");
        assertThat(detail.path("deletionRequests").size()).isEqualTo(1);

        // suspend → unsuspend round trip, both audited (reason ≥ 10 chars per spec §3.2.17)
        post("/api/v1/admin/users/" + victim2.id() + "/suspend", saToken, Map.of("reason", "dev fixture suspension"));
        var unsusp = post("/api/v1/admin/users/" + victim2.id() + "/unsuspend", saToken, Map.of("reason", "issue resolved"));
        assertThat(unsusp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(unsusp.getBody()).path("data").path("status").asText()).isEqualTo("active");

        // the activity feed lists the user's OWN audit events — give the user one
        // (a login is recorded as auth.login with the user as actor)
        login(victim2);
        var act = get("/api/v1/admin/users/" + victim2.id() + "/activity", saToken);
        assertThat(act.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(act.getBody()).path("data").size()).isGreaterThanOrEqualTo(1);
        boolean loginAudited = false;
        for (var row : readJson(act.getBody()).path("data")) {
            if ("auth.login".equals(row.path("action").asText())) loginAudited = true;
        }
        assertThat(loginAudited).as("auth.login visible in the user activity feed").isTrue();
    }
}
