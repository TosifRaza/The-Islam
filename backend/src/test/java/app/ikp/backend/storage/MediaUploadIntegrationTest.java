package app.ikp.backend.storage;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 file-upload security acceptance (spec §3.19.2, security plan §5):
 * validation gates (extension, magic bytes, size, quota), quarantine vs clean
 * states, private-by-default access and the short-lived signed download URL.
 *
 * Fixtures are byte-unique per call: media_assets.sha256 is UNIQUE (V2,
 * applied), so identical content can only be stored once — dev fixtures
 * therefore always carry a random suffix after their magic bytes.
 */
class MediaUploadIntegrationTest extends IntegrationTestBase {

    private static byte[] uniquePng() {
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0xD, 0xA, 0x1A, 0xA, 0, 0, 0, 13};
        byte[] out = new byte[head.length + 24];
        System.arraycopy(head, 0, out, 0, head.length);
        byte[] suffix = UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(suffix, 0, out, head.length, 24);
        return out;
    }

    private static byte[] uniquePdf(int sizeBytes) {
        byte[] head = ("%PDF-1.4 synthetic dev fixture — no religious content. uid=" + UUID.randomUUID())
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[Math.max(sizeBytes, head.length)];
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    /** Valid PNG magic + the EICAR AV-test string inside → passes the magic gate, infected verdict. */
    private static byte[] eicarPng() {
        byte[] png = uniquePng();
        byte[] eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[png.length + eicar.length];
        System.arraycopy(png, 0, out, 0, png.length);
        System.arraycopy(eicar, 0, out, png.length, eicar.length);
        return out;
    }

    @Test
    void cleanPngUploadIsPromotedWithTestScannerVerdict() {
        var cm = createAccount("content_manager", uniqueEmail("mu-clean"));
        var res = upload(login(cm), "cover.png", "cover", uniquePng(), "image/png", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var data = readJson(res.getBody()).path("data");
        assertThat(data.path("scanStatus").asText()).isEqualTo("clean");
        assertThat(data.path("scanner").asText()).isEqualTo(TestOnlyScanner.NAME);
    }

    @Test
    void eicarFixtureIsQuarantinedAndNeverServed() {
        var cm = createAccount("content_manager", uniqueEmail("mu-eicar"));
        String token = login(cm);
        var res = upload(token, "cover.png", "cover", eicarPng(), "image/png", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var data = readJson(res.getBody()).path("data");
        assertThat(data.path("scanStatus").asText()).isEqualTo("infected");
        UUID mediaId = UUID.fromString(data.path("mediaId").asText());

        // quarantined files are never exposed: download refused (UPLOAD-002)
        var dt = post("/api/v1/admin/files/" + mediaId + "/download-token", token, Map.of());
        assertThat(dt.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(dt.getBody()).contains("UPLOAD-002");
    }

    @Test
    void magicByteMismatchIsRejectedNotClean() {
        var cm = createAccount("content_manager", uniqueEmail("mu-magic"));
        var res = upload(login(cm), "fake.pdf", "book_pdf",
                ("not a pdf at all " + UUID.randomUUID()).getBytes(StandardCharsets.US_ASCII),
                "application/pdf", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(readJson(res.getBody()).path("data").path("scanStatus").asText()).isEqualTo("rejected");
    }

    @Test
    void wrongExtensionIsRefused() {
        var cm = createAccount("content_manager", uniqueEmail("mu-ext"));
        var res = upload(login(cm), "cover.gif", "cover", uniquePng(), "image/gif", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("UPLOAD-001");
    }

    @Test
    void oversizeUploadIsRefused413() {
        var cm = createAccount("content_manager", uniqueEmail("mu-size"));
        // test cap is 500 KB (IntegrationTestBase) — send 600 KB of valid-pdf-headed bytes
        var res = upload(login(cm), "big.pdf", "book_pdf", uniquePdf(600_000), "application/pdf", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("UPLOAD-003");
    }

    @Test
    void quotaExhaustionIsRefused() {
        var cm = createAccount("content_manager", uniqueEmail("mu-quota"));
        String token = login(cm);
        // test quota is 9,000,000 bytes: 18 uploads × 500,000 B fill it; #19 → QUOTA-001
        for (int i = 0; i < 18; i++) {
            var res = upload(token, "q" + i + ".pdf", "book_pdf", uniquePdf(500_000), "application/pdf", null);
            assertThat(res.getStatusCode()).as("upload " + i + ": " + res.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        }
        var res = upload(token, "overflow.pdf", "book_pdf", uniquePdf(500_000), "application/pdf", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("QUOTA-001");
    }

    @Test
    void mediaAccessRequiresSignedUrl() {
        var cm = createAccount("content_manager", uniqueEmail("mu-priv"));
        String token = login(cm);
        var res = upload(token, "cover.png", "cover", uniquePng(), "image/png", null);
        UUID mediaId = UUID.fromString(readJson(res.getBody()).path("data").path("mediaId").asText());

        // no token → the established AUTH-002 problem+json (401)
        var anonymous = rest.getForEntity("/api/v1/files/" + mediaId, String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).contains("AUTH-002");

        // forged signature → same refusal
        var forged = rest.getForEntity("/api/v1/files/" + mediaId + "?token=forged", String.class);
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // valid signed URL → contents retrievable
        var dt = post("/api/v1/admin/files/" + mediaId + "/download-token", token, Map.of());
        assertThat(dt.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = readJson(dt.getBody()).path("data").path("url").asText();
        var bytes = rest.getForEntity(url, byte[].class);
        assertThat(bytes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void fileInventoryFilteringWorks() {
        var cm = createAccount("content_manager", uniqueEmail("mu-list"));
        String token = login(cm);
        upload(token, "a.png", "cover", uniquePng(), "image/png", null);
        upload(token, "b.pdf", "book_pdf", uniquePdf(1024), "application/pdf", null);
        var covers = get("/api/v1/admin/files?purpose=cover&scanStatus=clean", token);
        assertThat(covers.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (var row : readJson(covers.getBody()).path("data")) {
            assertThat(row.path("purpose").asText()).isEqualTo("cover");
            assertThat(row.path("scanStatus").asText()).isEqualTo("clean");
        }
    }
}
