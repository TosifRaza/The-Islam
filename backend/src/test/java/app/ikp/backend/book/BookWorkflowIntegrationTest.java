package app.ikp.backend.book;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 Modules D + review workflow acceptance — the complete publication journey
 * of a book driven purely through the REST API (M2 master prompt §14 E2E):
 * author + file upload → book draft → submit-review → editorial approval →
 * scholarly approval (when required) → publish by a DIFFERENT authorised user →
 * visible in the public content API + audited. Negative paths (invalid state
 * transitions, missing permission, missing rejection reason, publish-with-
 * quarantine) are covered as required by the prompt.
 */
class BookWorkflowIntegrationTest extends IntegrationTestBase {

    // fixtures carry a unique suffix per call: media_assets.sha256 is UNIQUE
    // (V2, applied) so identical bytes can only be stored once — dev fixtures
    // must therefore be unique per upload. Magic bytes stay first.
    private static byte[] uniquePdf() {
        return ("%PDF-1.4 dev sample — SYNTHETIC fixture, no religious content. uid=" + java.util.UUID.randomUUID())
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] uniquePng() {
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0xD, 0xA, 0x1A, 0xA, 0, 0, 0, 13, 'I', 'H', 'D', 'R', 1, 1, 1, 1};
        byte[] out = new byte[head.length + 16];
        System.arraycopy(head, 0, out, 0, head.length);
        byte[] suffix = java.util.UUID.randomUUID().toString().substring(0, 16).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(suffix, 0, out, head.length, 16);
        return out;
    }

    private static byte[] uniqueTxtAsPdf() {
        return ("plain text pretending to be a pdf " + java.util.UUID.randomUUID()).getBytes(StandardCharsets.US_ASCII);
    }

    private record Media(UUID id, String scanStatus) {
    }

    private Media uploadClean(String token, String name, String purpose, byte[] bytes, String mime) {
        var res = upload(token, name, purpose, bytes, mime, null);
        assertThat(res.getStatusCode()).as(res.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        var data = readJson(res.getBody()).path("data");
        return new Media(UUID.fromString(data.path("mediaId").asText()), data.path("scanStatus").asText());
    }

    private UUID createAuthor(String token, String name) {
        var res = post("/api/v1/admin/authors", token, Map.of("name", name));
        assertThat(res.getStatusCode()).as(res.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(readJson(res.getBody()).path("data").path("authorId").asText());
    }

    private UUID createBook(String token, String title, String subjectSlug, boolean scholarly, UUID authorId,
                            UUID coverId, Long licenceId) {
        return createBook(token, title, subjectSlug, scholarly, authorId, coverId, licenceId, true);
    }

    private UUID createBook(String token, String title, String subjectSlug, boolean scholarly, UUID authorId,
                            UUID coverId, Long licenceId, boolean downloadAllowed) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("description", "Synthetic development sample — clearly labelled, no religious content.");
        body.put("language", "en");
        body.put("subjectId", subjectSlug);
        body.put("publisher", "IKP Dev Press");
        body.put("edition", "1st");
        body.put("publicationYear", 2026);
        body.put("pageCount", 42);
        body.put("isbn", "978-0-00000-000-0");
        body.put("copyrightInfo", "© 2026 IKP test fixture");
        body.put("licenceId", licenceId);
        body.put("downloadAllowed", downloadAllowed);
        body.put("scholarlyReviewRequired", scholarly);
        if (authorId != null) body.put("authorIds", List.of(authorId));
        if (coverId != null) body.put("coverMediaId", coverId);
        var res = post("/api/v1/admin/books", token, body);
        assertThat(res.getStatusCode()).as(res.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(readJson(res.getBody()).path("data").path("bookId").asText());
    }

    @Test
    void fullPublicationJourneyAppearsInPublicLibraryAndAudit() {
        var cm = createAccount("content_manager", uniqueEmail("bw-cm"));   // creates + editorial review
        var admin = createAccount("admin", uniqueEmail("bw-admin"));       // publisher (has content.publish)
        String cmToken = login(cm);
        String adminToken = login(admin);

        // 1–3. author, cover + file upload (scan-clean fixtures)
        UUID authorId = createAuthor(cmToken, "Test Author (synthetic)");
        Media cover = uploadClean(cmToken, "cover.png", "cover", uniquePng(), "image/png");
        Media pdf = uploadClean(cmToken, "sample.pdf", "book_pdf", uniquePdf(), "application/pdf");
        assertThat(cover.scanStatus()).isEqualTo("clean");
        assertThat(pdf.scanStatus()).isEqualTo("clean");

        // 4. book draft with full metadata (learning academy → no scholarly review)
        UUID bookId = createBook(cmToken, "Dev Sample: Introduction to Categories (synthetic)",
                "cat_learning_academy", false, authorId, cover.id(), null);

        // 5. attach the scan-clean pdf
        var attached = post("/api/v1/admin/books/" + bookId + "/files", cmToken,
                Map.of("mediaId", pdf.id().toString(), "format", "pdf", "downloadAllowed", true));
        assertThat(attached.getStatusCode()).as(attached.getBody()).isEqualTo(HttpStatus.CREATED);

        // 6. submit for review
        var submitted = post("/api/v1/admin/books/" + bookId + "/submit-review", cmToken, Map.of());
        assertThat(submitted.getStatusCode()).as(submitted.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(submitted.getBody()).path("data").path("state").asText()).isEqualTo("in_editorial_review");

        // 7. editorial approval
        var approvedEditorial = post("/api/v1/admin/books/" + bookId + "/approve-editorial", cmToken,
                Map.of("notes", "editorial check passed (dev sample)"));
        assertThat(approvedEditorial.getStatusCode()).as(approvedEditorial.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(approvedEditorial.getBody()).path("data").path("state").asText()).isEqualTo("approved");

        // 8. publish by an authorised user (draft → published never happens automatically)
        var published = post("/api/v1/admin/books/" + bookId + "/publish", adminToken, Map.of());
        assertThat(published.getStatusCode()).as(published.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(published.getBody()).path("data").path("state").asText()).isEqualTo("published");

        // 9. the content appears in the appropriate public content API
        var lib = rest.getForEntity("/api/v1/library/books", String.class);
        assertThat(lib.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean visible = false;
        for (var row : readJson(lib.getBody()).path("data")) {
            if (row.path("bookId").asText().equals(bookId.toString())) visible = true;
        }
        assertThat(visible).as("published book visible in public library").isTrue();

        var detail = rest.getForEntity("/api/v1/library/books/" + bookId, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(detail.getBody()).path("data").path("title").asText()).contains("Dev Sample");

        // 10. the publishing event appears in the audit log
        var auditRows = readJson(get("/api/v1/admin/audit-logs?action=workflow.publish", adminToken).getBody()).path("data");
        boolean audited = false;
        for (var row : auditRows) {
            if ("workflow.publish".equals(row.path("action").asText())) audited = true;
        }
        assertThat(audited).as("publish audited").isTrue();

        // download flow: token for the attached clean pdf (download_allowed)
        var dt = post("/api/v1/library/books/" + bookId + "/download-token", adminToken,
                Map.of("fileId", pdf.id().toString()));
        assertThat(dt.getStatusCode()).as(dt.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(dt.getBody()).path("data").path("url").asText()).contains("/api/v1/files/");
    }

    @Test
    void invalidTransitionIsRefusedWithWorkflow001AndAudited() {
        var cm = createAccount("content_manager", uniqueEmail("bw-neg"));
        String token = login(cm);
        UUID bookId = createBook(token, "Dev Sample: Negative Path (synthetic)", "cat_learning_academy",
                false, null, null, null);

        // publish straight from draft → refused
        var res = post("/api/v1/admin/books/" + bookId + "/publish", token, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");

        // the denial is audited even though the operation rolled back
        var admin = login(createAccount("admin", uniqueEmail("bw-neg-admin")));
        var auditRows = readJson(get("/api/v1/admin/audit-logs?action=workflow.deny", admin).getBody()).path("data");
        boolean denialAudited = false;
        for (var row : auditRows) {
            if ("workflow.deny".equals(row.path("action").asText())) denialAudited = true;
        }
        assertThat(denialAudited).as("denied attempt logged").isTrue();

        // rejection without a reason is refused
        post("/api/v1/admin/books/" + bookId + "/submit-review", token, Map.of());
        var rejectNoNotes = post("/api/v1/admin/books/" + bookId + "/reject", token, Map.of());
        assertThat(rejectNoNotes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var rejected = post("/api/v1/admin/books/" + bookId + "/reject", token,
                Map.of("notes", "needs a better introduction section"));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(rejected.getBody()).path("data").path("state").asText()).isEqualTo("draft");

        // only drafts are editable
        post("/api/v1/admin/books/" + bookId + "/submit-review", token, Map.of());
        var editInReview = patch("/api/v1/admin/books/" + bookId, token,
                Map.of("title", "Dev Sample: Renamed in review (synthetic)", "language", "en",
                        "subjectId", "cat_learning_academy"));
        assertThat(editInReview.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void scholarlyPathRequiresScholarReviewerAndPublisherPermission() {
        var cm = createAccount("content_manager", uniqueEmail("bw-sch-cm"));
        var scholar = createAccount("scholar_reviewer", uniqueEmail("bw-scholar"));
        String cmToken = login(cm);
        String scholarToken = login(scholar);

        // quran/tafsir subject ⇒ scholarlyReviewRequired defaults to true
        UUID bookId = createBook(cmToken, "Dev Sample: Scholarly Path Fixture (synthetic)",
                "cat_quran_tafsir", true, null, null, null);
        post("/api/v1/admin/books/" + bookId + "/submit-review", cmToken, Map.of());
        var editorial = post("/api/v1/admin/books/" + bookId + "/approve-editorial", cmToken, Map.of());
        assertThat(readJson(editorial.getBody()).path("data").path("state").asText())
                .isEqualTo("in_scholarly_review");

        // content manager cannot approve scholarly (not the Scholar Reviewer) —
        // the service gate answers the established WORKFLOW-001 (409), audited
        var denied = post("/api/v1/admin/books/" + bookId + "/approve", cmToken, Map.of());
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(denied.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");

        // scholar approves → approved
        var scholarOk = post("/api/v1/admin/books/" + bookId + "/approve", scholarToken,
                Map.of("notes", "scholarly check passed (fixture)"));
        assertThat(scholarOk.getStatusCode()).as(scholarOk.getBody()).isEqualTo(HttpStatus.OK);

        // scholar lacks content.publish → the service gate answers WORKFLOW-001
        // (409, audited) — the unified family authorises in the service layer
        var deniedPublish = post("/api/v1/admin/books/" + bookId + "/publish", scholarToken, Map.of());
        assertThat(deniedPublish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(deniedPublish.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");

        // admin publishes → published
        String adminToken = login(createAccount("admin", uniqueEmail("bw-sch-admin")));
        var published = post("/api/v1/admin/books/" + bookId + "/publish", adminToken, Map.of());
        assertThat(published.getStatusCode()).as(published.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(published.getBody()).path("data").path("state").asText()).isEqualTo("published");

        // review history records reviewer decisions (governance)
        var reviews = readJson(get("/api/v1/admin/books/" + bookId + "/reviews", adminToken).getBody()).path("data");
        assertThat(reviews.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void quarantinedMediaCanNeverBeAttachedOrPublished() {
        var cm = createAccount("content_manager", uniqueEmail("bw-q"));
        String token = login(cm);
        // magic-byte mismatch → rejected (quarantined, never clean)
        var res = upload(token, "fake.pdf", "book_pdf", uniqueTxtAsPdf(), "application/pdf", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var data = readJson(res.getBody()).path("data");
        assertThat(data.path("scanStatus").asText()).isIn("rejected", "infected");
        UUID badMedia = UUID.fromString(data.path("mediaId").asText());

        UUID bookId = createBook(token, "Dev Sample: Quarantine Gate (synthetic)",
                "cat_learning_academy", false, null, null, null);
        var attach = post("/api/v1/admin/books/" + bookId + "/files", token,
                Map.of("mediaId", badMedia.toString(), "format", "pdf"));
        assertThat(attach.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(attach.getBody()).contains("UPLOAD-002");
    }

    @Test
    void licenceDownloadRestrictionIsEnforced() {
        var cm = createAccount("content_manager", uniqueEmail("bw-lic"));
        var admin = createAccount("admin", uniqueEmail("bw-lic-admin"));
        String cmToken = login(cm);
        Media pdf = uploadClean(cmToken, "sample.pdf", "book_pdf", uniquePdf(), "application/pdf");
        UUID bookId = createBook(cmToken, "Dev Sample: Licence Gate (synthetic)",
                "cat_learning_academy", false, null, null, null);
        post("/api/v1/admin/books/" + bookId + "/files", cmToken,
                Map.of("mediaId", pdf.id().toString(), "format", "pdf", "downloadAllowed", true));
        post("/api/v1/admin/books/" + bookId + "/submit-review", cmToken, Map.of());
        post("/api/v1/admin/books/" + bookId + "/approve-editorial", cmToken, Map.of());
        String adminToken = login(admin);
        post("/api/v1/admin/books/" + bookId + "/publish", adminToken, Map.of());

        // second book with downloadAllowed=false needs its OWN media (media is
        // attached 1:1 — book_files.media_id is UNIQUE)
        Media pdf2 = uploadClean(cmToken, "sample2.pdf", "book_pdf", uniquePdf(), "application/pdf");
        UUID book2 = createBook(cmToken, "Dev Sample: No Downloads (synthetic)",
                "cat_learning_academy", false, null, null, null, false);
        post("/api/v1/admin/books/" + book2 + "/files", cmToken,
                Map.of("mediaId", pdf2.id().toString(), "format", "pdf", "downloadAllowed", false));
        post("/api/v1/admin/books/" + book2 + "/submit-review", cmToken, Map.of());
        post("/api/v1/admin/books/" + book2 + "/approve-editorial", cmToken, Map.of());
        post("/api/v1/admin/books/" + book2 + "/publish", adminToken, Map.of());

        // downloads not permitted by licence/metadata → LIC-001
        var lic = post("/api/v1/library/books/" + book2 + "/download-token", adminToken,
                Map.of("fileId", pdf2.id().toString()));
        assertThat(lic.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJson(lic.getBody()).path("code").asText()).isEqualTo("LIC-001");
    }

    @Test
    void privateRegisteredUserIsDeniedAdminBookApis() {
        var reader = createAccount("registered_user", uniqueEmail("bw-reader"));
        String token = login(reader);
        assertThat(post("/api/v1/admin/books", token, Map.of("title", "x", "language", "en",
                "subjectId", "cat_aqeedah")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/books", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
