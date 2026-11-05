package app.ikp.backend.personal;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 Personal Library privacy gate (acceptance §7 / PRS-05). Ownership is
 * enforced server-side: another user gets 404s, admins never see personal
 * books through the PLATFORM book domain, uploads never leak into the public
 * library, and the file-type gate refuses non-PDF/EPUB.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M4PersonalLibraryPrivacyTest extends IntegrationTestBase {

    private static final byte[] PDF_BYTES = ("%%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n"
            + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes();

    @Test
    @Order(1)
    void uploadRejectsUnsupportedTypes() {
        var owner = createAccount("registered_user", uniqueEmail("pl-owner"));
        String token = login(owner);
        ResponseEntity<String> res = uploadPersonal(token, "notes.txt", "text/plain", "hello".getBytes());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("VAL-001");
    }

    @Test
    @Order(2)
    void anotherUserGetsNotFoundForForeignBooks() {
        var owner = createAccount("registered_user", uniqueEmail("pl-a"));
        var intruder = createAccount("registered_user", uniqueEmail("pl-b"));
        String ownerToken = login(owner);
        String intruderToken = login(intruder);
        ResponseEntity<String> up = uploadPersonal(ownerToken, "my-book.pdf", "application/pdf", PDF_BYTES);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        String bookId = readJson(up.getBody()).path("data").path("bookId").asText();
        // intruder gets 404 on every path (existence never disclosed)
        assertThat(get("/api/v1/personal/books/" + bookId, intruderToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch("/api/v1/personal/books/" + bookId, intruderToken,
                Map.of("title", "hijacked")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/v1/personal/books/" + bookId, intruderToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/v1/personal/books/" + bookId + "/download-token",
                intruderToken, Map.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/personal/books/" + bookId, ownerToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(3)
    void personalUploadsNeverAppearInPlatformLibraryOrAdminBookLists() {
        var owner = createAccount("registered_user", uniqueEmail("pl-c"));
        String ownerToken = login(owner);
        String marker = "UNIQUE-PL-MARKER-" + uniqueEmail("x");
        ResponseEntity<String> up = uploadPersonal(ownerToken, marker + ".pdf", "application/pdf", PDF_BYTES);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> pub = rest.getForEntity("/api/v1/library/books?limit=100", String.class);
        assertThat(pub.getBody()).doesNotContain(marker);
        var admin = createAccount("super_admin", uniqueEmail("pl-admin"));
        String adminToken = login(admin);
        ResponseEntity<String> adminList = get("/api/v1/admin/books?size=100", adminToken);
        assertThat(adminList.getStatusCode()).as("admin body: %s", adminList.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(adminList.getBody()).doesNotContain(marker);
    }

    @Test
    @Order(4)
    void downloadTokenRequiresScanCleanMedia() {
        var owner = createAccount("registered_user", uniqueEmail("pl-d"));
        String ownerToken = login(owner);
        ResponseEntity<String> up = uploadPersonal(ownerToken, "scan.pdf", "application/pdf", PDF_BYTES);
        String bookId = readJson(up.getBody()).path("data").path("bookId").asText();
        ResponseEntity<String> res = post("/api/v1/personal/books/" + bookId + "/download-token",
                ownerToken, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(readJson(res.getBody()).path("code").asText()).isEqualTo("UPLOAD-002");
    }

    @Test
    @Order(5)
    void collectionsAreOwnerScoped() {
        var a = createAccount("registered_user", uniqueEmail("pl-e"));
        var b = createAccount("registered_user", uniqueEmail("pl-f"));
        String ta = login(a);
        String tb = login(b);
        ResponseEntity<String> created = post("/api/v1/personal/collections", ta,
                Map.of("name", "Seerah research"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> listB = get("/api/v1/personal/collections", tb);
        assertThat(readJson(listB.getBody()).path("data").size()).isZero();
    }

    private ResponseEntity<String> uploadPersonal(String token, String filename,
                                                  String mime, byte[] bytes) {
        // unique bytes per upload: media_assets.sha256 is UNIQUE, so identical
        // personal uploads from different tests would collide
        bytes = (filename + ":" + java.util.Arrays.hashCode(bytes) + ":"
                + java.util.UUID.randomUUID()).getBytes();
        HttpHeaders h = bearer(token);
        h.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource filePart = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        body.add("title", filename.replace(".pdf", "").replace(".epub", ""));
        body.add("author", "Test Author");
        body.add("description", "personal upload integration fixture");
        return rest.exchange("/api/v1/personal/books", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
    }
}
