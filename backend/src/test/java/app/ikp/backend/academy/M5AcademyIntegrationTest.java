package app.ikp.backend.academy;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5 Academy acceptance — course lifecycle, publish governance (state machine,
 * reviewer ≠ creator), public visibility of published courses only, enrolment
 * + progress ownership, continue-learning rail. Real HTTP, real PostgreSQL.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M5AcademyIntegrationTest extends IntegrationTestBase {

    private static String courseId;
    private static String lessonId;
    private static Account creatorAccount;
    private static String coursePublicTitle = "Foundations of Tafsir " + UUID.randomUUID().toString().substring(0, 6);

    @Test
    @Order(1)
    void academyManagerCreatesCourseWithStructure() {
        creatorAccount = createAccount("content_manager", uniqueEmail("cm0")); // editorial-capable creator
        String token = login(creatorAccount);

        ResponseEntity<String> res = post("/api/v1/admin/academy/courses", token, Map.of(
                "title", coursePublicTitle,
                "description", "Introductory course on the discipline of tafsir.",
                "language", "en", "level", "beginner",
                "subjectSlug", "cat_quran_tafsir",
                "instructorDisplayName", "Ustadh Ahmad (dev data)",
                "instructorCredentialsNote", "Credentials recorded by admin; platform does not generate them",
                "estimatedHours", 6));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        courseId = readJson(res.getBody()).path("data").path("courseId").asText();
        assertThat(courseId).isNotBlank();

        ResponseEntity<String> ch = post("/api/v1/admin/academy/courses/" + courseId + "/chapters", token,
                Map.of("title", "Chapter 1 — What is tafsir?", "sortOrder", 1));
        assertThat(ch.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String chapterId = readJson(ch.getBody()).path("data").path("chapterId").asText();

        ResponseEntity<String> ls = post("/api/v1/admin/academy/chapters/" + chapterId + "/lessons", token,
                Map.of("title", "Lesson 1 — Definition and sources", "lessonType", "text",
                        "bodyText", "Tafsir is the disciplined explanation of the Quran.", "sortOrder", 1));
        assertThat(ls.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        lessonId = readJson(ls.getBody()).path("data").path("lessonId").asText();
    }

    @Test
    @Order(2)
    void unpublishedCourseIsInvisibleToGuests() {
        ResponseEntity<String> res = get("/api/v1/academy/courses/" + courseId, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // and it is absent from the public catalogue
        ResponseEntity<String> list = get("/api/v1/academy/courses", null);
        assertThat(readJson(list.getBody()).toString()).doesNotContain(coursePublicTitle);
    }

    @Test
    @Order(3)
    void publishIsBlockedBeforeApprovalAndSelfApprovalDenied() {
        String token = login(creatorAccount);

        // publish before approval → WORKFLOW-001
        ResponseEntity<String> early = post("/api/v1/admin/academy/courses/" + courseId + "/publish", token, null);
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(early.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");

        // the CREATOR cannot be their own editorial approver (separation of duties)
        post("/api/v1/admin/academy/courses/" + courseId + "/submit-review", token, null);
        ResponseEntity<String> self = post("/api/v1/admin/academy/courses/" + courseId + "/approve-editorial",
                token, null);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(self.getBody()).path("detail").asText()).contains("reviewer other than the creator");
    }

    @Test
    @Order(4)
    void editorialApprovalThenPublishMakesCoursePublic() {
        var approver = createAccount("content_manager", uniqueEmail("cm5"));
        String approveToken = login(approver);
        var publisher = createAccount("super_admin", uniqueEmail("sa5"));
        String publishToken = login(publisher);

        assertThat(post("/api/v1/admin/academy/courses/" + courseId + "/approve-editorial", approveToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/admin/academy/courses/" + courseId + "/publish", publishToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> guest = get("/api/v1/academy/courses/" + courseId, null);
        assertThat(guest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(guest.getBody()).path("data").path("title").asText()).isEqualTo(coursePublicTitle);
        assertThat(readJson(guest.getBody()).path("data").path("chapters").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(5)
    void enrolmentAndProgressAreOwnershipScoped() {
        var reader = createAccount("registered_user", uniqueEmail("rd5"));
        String token = login(reader);

        ResponseEntity<String> enrol = post("/api/v1/academy/courses/" + courseId + "/enrol", token, null);
        assertThat(enrol.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(readJson(enrol.getBody()).path("data").path("status").asText()).isEqualTo("active");

        ResponseEntity<String> done = post("/api/v1/academy/lessons/" + lessonId + "/complete", token, null);
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        var progress = readJson(done.getBody()).path("data");
        assertThat(progress.path("percent").asInt()).isEqualTo(100);
        assertThat(progress.path("status").asText()).isEqualTo("completed"); // single lesson → course completed

        ResponseEntity<String> rail = get("/api/v1/academy/continue?limit=5", token);
        // course completed → not in the active continue rail
        assertThat(readJson(rail.getBody()).toString()).doesNotContain(coursePublicTitle);
    }

    @Test
    @Order(6)
    void unauthenticatedEnrolmentIsRejected() {
        ResponseEntity<String> res = rest.postForEntity(
                "/api/v1/academy/courses/" + courseId + "/enrol", null, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(7)
    void draftOnlyEditingIsEnforced() {
        var mgr = createAccount("academy_manager", uniqueEmail("am5c"));
        String token = login(mgr);
        ResponseEntity<String> res = patch("/api/v1/admin/academy/courses/" + courseId, token, Map.of(
                "title", "X", "subjectSlug", "cat_quran_tafsir"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");
    }

    @Test
    @Order(8)
    void continueLearningShowsActiveEnrolmentWithNextLesson() {
        var reader = createAccount("registered_user", uniqueEmail("rd5b"));
        String token = login(reader);
        // second course, kept active (two lessons, complete only one)
        var mgr = createAccount("academy_manager", uniqueEmail("am5d"));
        String mgrToken = login(mgr);
        String c2 = readJson(post("/api/v1/admin/academy/courses", mgrToken, Map.of(
                "title", "Seerah Basics " + UUID.randomUUID().toString().substring(0, 5),
                "subjectSlug", "cat_history_seerah", "level", "beginner"))
                .getBody()).path("data").path("courseId").asText();
        String ch2 = readJson(post("/api/v1/admin/academy/courses/" + c2 + "/chapters", mgrToken,
                Map.of("title", "Ch1", "sortOrder", 1)).getBody()).path("data").path("chapterId").asText();
        String l1 = readJson(post("/api/v1/admin/academy/chapters/" + ch2 + "/lessons", mgrToken,
                Map.of("title", "L1", "lessonType", "text", "bodyText", "a", "sortOrder", 1))
                .getBody()).path("data").path("lessonId").asText();
        String l2 = readJson(post("/api/v1/admin/academy/chapters/" + ch2 + "/lessons", mgrToken,
                Map.of("title", "L2", "lessonType", "text", "bodyText", "b", "sortOrder", 2))
                .getBody()).path("data").path("lessonId").asText();

        var approver = createAccount("content_manager", uniqueEmail("cm5b"));
        String approveToken = login(approver);
        var publisher = createAccount("super_admin", uniqueEmail("sa5b"));
        String publishToken = login(publisher);
        post("/api/v1/admin/academy/courses/" + c2 + "/submit-review", mgrToken, null);
        post("/api/v1/admin/academy/courses/" + c2 + "/approve-editorial", approveToken, null);
        post("/api/v1/admin/academy/courses/" + c2 + "/publish", publishToken, null);

        post("/api/v1/academy/courses/" + c2 + "/enrol", token, null);
        post("/api/v1/academy/lessons/" + l1 + "/complete", token, null);

        ResponseEntity<String> rail = get("/api/v1/academy/continue", token);
        var data = readJson(rail.getBody()).path("data");
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (var item : data) {
            if (item.path("courseId").asText().equals(c2)) {
                found = true;
                assertThat(item.path("percent").asInt()).isEqualTo(50);
                assertThat(item.path("nextLessonId").asText()).isEqualTo(l2);
            }
        }
        assertThat(found).isTrue();
    }
}
