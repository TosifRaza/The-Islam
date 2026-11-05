package app.ikp.backend.research;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5 Research + Scholars + Fatwa-directory acceptance. Verifies: topic
 * taxonomy seeded (10), citation verification (a citation pointing to a
 * non-existent ayah is rejected), three-tier classification + scholarly
 * review gate for primary/secondary articles, public visibility rules,
 * scholar verification flow, and the published-only Fatwa directory (OD-08).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M5ResearchScholarsIntegrationTest extends IntegrationTestBase {

    private final JdbcTemplate jdbc = new JdbcTemplate(app.ikp.backend.support.SharedPostgres.get());

    private static String articleId;
    private static String articleTitle = "Studying Sabab al-Nuzul " + UUID.randomUUID().toString().substring(0, 5);
    private static String scholarId;
    private static String publishedFatwaId;

    @Test
    @Order(1)
    void researchTopicTaxonomyIsSeeded() {
        ResponseEntity<String> res = get("/api/v1/research/topics", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var topics = readJson(res.getBody()).path("data");
        assertThat(topics.size()).isEqualTo(10);
        List<String> slugs = new java.util.ArrayList<>();
        topics.forEach(t -> slugs.add(t.path("slug").asText()));
        assertThat(slugs).contains("quran", "hadith", "fiqh", "usul_al_fiqh", "aqeedah",
                "islamic_history", "seerah", "arabic_language", "classical_literature", "comparative_studies");
    }

    @Test
    @Order(2)
    void citationPointingToNonExistentAyahIsRejected() {
        var editor = createAccount("research_editor", uniqueEmail("rs1"));
        String token = login(editor);
        ResponseEntity<String> res = post("/api/v1/admin/research/articles", token, Map.of(
                "topicSlug", "quran",
                "title", articleTitle,
                "summary", "Why the occasion of revelation matters in tafsir.",
                "body", "Sabab al-nuzul links revelation to events; a citation must resolve.",
                "sourceTier", "secondary",
                "citations", List.of(Map.of(
                        "citationKind", "quran_ayah", "surahId", 999, "ayahNumber", 1))));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("VAL-001");
    }

    @Test
    @Order(3)
    void articleWithVerifiedAyahCitationIsCreatedAndNeedsScholarlyReview() {
        var editor = createAccount("research_editor", uniqueEmail("rs2"));
        String editorToken = login(editor);
        var reviewer = createAccount("content_manager", uniqueEmail("rs3"));
        String reviewToken = login(reviewer);
        var scholar = createAccount("scholar_reviewer", uniqueEmail("rs4"));
        String scholarToken = login(scholar);
        var publisher = createAccount("super_admin", uniqueEmail("rs5"));
        String publishToken = login(publisher);

        ResponseEntity<String> res = post("/api/v1/admin/research/articles", editorToken, Map.of(
                "topicSlug", "quran",
                "title", articleTitle,
                "summary", "Why the occasion of revelation matters in tafsir.",
                "body", "Sabab al-nuzul links revelation to events; e.g. the opening of Surah al-Fatiha.",
                "sourceTier", "secondary",
                "citations", List.of(Map.of(
                        "citationKind", "quran_ayah", "surahId", 1, "ayahNumber", 1,
                        "note", "Fatiha 1:1 as a structural anchor"))));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        articleId = readJson(res.getBody()).path("data").path("articleId").asText();
        var citations = readJson(res.getBody()).path("data").path("citations");
        assertThat(citations.size()).isEqualTo(1);
        assertThat(citations.get(0).path("resolved").path("textUthmani").asText()).isNotBlank();

        // secondary tier → editorial approve routes to SCHOLARLY review
        post("/api/v1/admin/research/articles/" + articleId + "/submit-review", editorToken, null);
        post("/api/v1/admin/research/articles/" + articleId + "/approve-editorial", reviewToken, null);
        ResponseEntity<String> state = get("/api/v1/admin/research/articles/" + articleId, editorToken);
        assertThat(readJson(state.getBody()).path("data").path("status").asText())
                .isEqualTo("in_scholarly_review");

        // publish before scholarly approval → refused
        assertThat(post("/api/v1/admin/research/articles/" + articleId + "/publish", publishToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // non-scholar cannot take the scholarly decision
        assertThat(post("/api/v1/admin/research/articles/" + articleId + "/approve", reviewToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // scholar approves → publish allowed
        assertThat(post("/api/v1/admin/research/articles/" + articleId + "/approve", scholarToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/admin/research/articles/" + articleId + "/publish", publishToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // public read shows the tier label and the citation
        ResponseEntity<String> pub = get("/api/v1/research/articles/" + articleId, null);
        assertThat(pub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(pub.getBody()).path("data").path("sourceTier").asText()).isEqualTo("secondary");
    }

    @Test
    @Order(4)
    void unapprovedArticlesAreInvisiblePublicly() {
        var editor = createAccount("research_editor", uniqueEmail("rs6"));
        String token = login(editor);
        String draft = readJson(post("/api/v1/admin/research/articles", token, Map.of(
                "topicSlug", "hadith", "title", "Draft only article", "body", "b",
                "sourceTier", "editorial")).getBody()).path("data").path("articleId").asText();
        assertThat(get("/api/v1/research/articles/" + draft, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void scholarDirectoryVerificationFlow() {
        var admin = createAccount("admin", uniqueEmail("sc1"));
        String adminToken = login(admin);

        ResponseEntity<String> res = post("/api/v1/admin/scholars", adminToken, Map.of(
                "name", "Dev Scholar Profile " + UUID.randomUUID().toString().substring(0, 4),
                "biography", "Test-only profile",
                "qualifications", "Recorded by admin (test)",
                "areasOfExpertise", "Hadith sciences"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        scholarId = readJson(res.getBody()).path("data").path("scholarId").asText();
        assertThat(readJson(res.getBody()).path("data").path("verificationStatus").asText())
                .isEqualTo("unverified");

        // unverified profiles are NOT in the public directory
        ResponseEntity<String> pubList = get("/api/v1/scholars", null);
        assertThat(readJson(pubList.getBody()).toString()).doesNotContain(scholarId);
        assertThat(get("/api/v1/scholars/" + scholarId, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // verify → public
        assertThat(post("/api/v1/admin/scholars/" + scholarId + "/verify", adminToken,
                Map.of("status", "verified")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/scholars/" + scholarId, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(6)
    void fatwaDirectoryServesPublishedOnlyWithReviewerScholarInfo() {
        // Publish a fatwa through the REAL M2 separation-of-duties workflow
        var creator = createAccount("scholar_reviewer", uniqueEmail("fw1")); // fatwa.create holder per M2 matrix
        String creatorToken = login(creator);
        var scholar = createAccount("scholar_reviewer", uniqueEmail("fw2"));
        String scholarToken = login(scholar);
        var publisher = createAccount("admin", uniqueEmail("fw3"));
        String publisherToken = login(publisher);

        String fatwaId = readJson(post("/api/v1/admin/fatwas", creatorToken, Map.of(
                "question", "Public directory test question " + UUID.randomUUID().toString().substring(0, 5),
                "answerDraft", "Answer recorded by staff; reviewer must approve.",
                "category", "purification", "language", "en",
                "sourceRefs", List.of("Sahih al-Bukhari (test ref)"))).getBody())
                .path("data").path("fatwaId").asText();

        // draft → not in public directory
        ResponseEntity<String> dir = get("/api/v1/fatwas", null);
        assertThat(readJson(dir.getBody()).toString()).doesNotContain("Public directory test question");

        post("/api/v1/admin/fatwas/" + fatwaId + "/submit-review", creatorToken, null);
        // link the reviewing scholar profile to the reviewer account so the directory can resolve the name
        var admin = createAccount("admin", uniqueEmail("fw4"));
        String adminToken = login(admin);
        post("/api/v1/admin/scholars", adminToken, Map.of(
                "name", "Directory Reviewer " + UUID.randomUUID().toString().substring(0, 4),
                "qualifications", "Test", "linkedUserPublicId", scholar.id().toString()));
        assertThat(post("/api/v1/admin/fatwas/" + fatwaId + "/decision", scholarToken,
                Map.of("decision", "approve", "notes", "ok")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/admin/fatwas/" + fatwaId + "/publish", publisherToken, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> pub = get("/api/v1/fatwas/" + fatwaId, null);
        assertThat(pub.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = readJson(pub.getBody()).path("data");
        assertThat(data.path("answer").asText()).contains("Answer recorded by staff");
        assertThat(data.path("review").path("reviewDate").asText()).isNotBlank();
        assertThat(data.path("review").has("scholar")).isTrue();
        publishedFatwaId = fatwaId;

        // full directory includes it; search by topic text
        ResponseEntity<String> dir2 = get("/api/v1/fatwas?q=Public directory test", null);
        assertThat(readJson(dir2.getBody()).toString()).contains("fatwaId");
    }

    @Test
    @Order(7)
    void globalSearchReturnsRealM5Groups() {
        ResponseEntity<String> res = get("/api/v1/search?q=tafsir&groups=courses,research,scholars,fatwas", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = readJson(res.getBody()).path("data");
        assertThat(data.has("courses")).isTrue();
        assertThat(data.has("research")).isTrue();
        assertThat(data.has("scholars")).isTrue();
        assertThat(data.has("fatwas")).isTrue();
        // no more "content arrives in a later milestone" stubs
        assertThat(data.toString()).doesNotContain("later milestone");
    }

    @Test
    @Order(8)
    void userContentRefKeysAcceptM5KindsAndStayOwnershipScoped() {
        var reader = createAccount("registered_user", uniqueEmail("uc1"));
        String token = login(reader);
        String researchRef = "research:" + UUID.randomUUID();
        // saving research (Module 11) reuses the M4 ownership-scoped pipeline
        assertThat(put("/api/v1/user-content/bookmarks", token, Map.of("refKey", researchRef))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> list = get("/api/v1/user-content/bookmarks", token);
        assertThat(readJson(list.getBody()).toString()).contains(researchRef);
        // other M5 kinds accepted; legacy M4 kinds untouched
        assertThat(put("/api/v1/user-content/favourites", token, Map.of("refKey",
                "course:" + UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put("/api/v1/user-content/notes", token, Map.of("refKey",
                "fatwa:" + UUID.randomUUID(), "body", "personal note")).getStatusCode()).isEqualTo(HttpStatus.OK);
        // unknown prefixes remain rejected
        assertThat(put("/api/v1/user-content/bookmarks", token, Map.of("refKey", "evil:1"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // another user never sees these rows
        var stranger = createAccount("registered_user", uniqueEmail("uc2"));
        assertThat(readJson(get("/api/v1/user-content/bookmarks", login(stranger)).getBody())
                .toString()).doesNotContain(researchRef);
    }
}
