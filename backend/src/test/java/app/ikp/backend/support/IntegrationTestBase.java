package app.ikp.backend.support;

import app.ikp.backend.user.Role;
import app.ikp.backend.user.RoleRepository;
import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 integration base: real PostgreSQL + real HTTP (random port) + full
 * security chain. Shared DB keeps the (heavy) context single-boot across all
 * M2 suites. Login rate limit is raised for tests only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected RoleRepository roles;
    @Autowired
    protected PasswordEncoder encoder;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        DataSource ds = SharedPostgres.get();
        registry.add("spring.datasource.url", () -> urlOf(ds));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("app.security.login-rate-limit", () -> "100000");
        registry.add("app.storage.root", () -> "target/test-storage");
        registry.add("app.upload.max-bytes", () -> "512000");       // 500 KB test cap
        registry.add("app.upload.quota-bytes-per-user", () -> "9000000");
        registry.add("spring.jpa.properties.hibernate.show_sql", () -> "true");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "false");
    }

    private static String urlOf(DataSource ds) {
        try {
            return ds.getConnection().getMetaData().getURL();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- account helpers ----------

    public record Account(UUID id, String email, String password) {
    }

    protected Account createAccount(String roleCode, String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("Test#Password-123"));
        Role role = roles.findByCode(roleCode).orElseThrow();
        u.getRoles().add(role);
        users.save(u);
        return new Account(u.getPublicId(), email, "Test#Password-123");
    }

    protected String login(String email, String password) {
        ResponseEntity<String> res = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password, "deviceName", "it"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(res.getBody()).path("data").path("accessToken").asText();
    }

    protected String login(Account a) {
        return login(a.email(), a.password());
    }

    protected JsonNode readJson(String body) {
        try {
            return json.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException(body, e);
        }
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected HttpEntity<String> entity(String token, Object body) {
        try {
            return new HttpEntity<>(body == null ? null : json.writeValueAsString(body), bearer(token));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ResponseEntity<String> post(String url, String token, Object body) {
        return rest.exchange(url, HttpMethod.POST, entity(token, body), String.class);
    }

    /** Multipart upload against /api/v1/admin/files (spec §3.19.2). */
    protected ResponseEntity<String> upload(String token, String filename, String purpose,
                                            byte[] bytes, String mime, Long licenceId) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        org.springframework.core.io.ByteArrayResource filePart =
                new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", filePart);
        body.add("purpose", purpose);
        if (licenceId != null) body.add("licenceId", licenceId);
        return rest.postForEntity("/api/v1/admin/files", new HttpEntity<>(body, h), String.class);
    }

    protected ResponseEntity<String> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, entity(token, null), String.class);
    }

    protected ResponseEntity<String> patch(String url, String token, Object body) {
        return rest.exchange(url, HttpMethod.PATCH, entity(token, body), String.class);
    }

    /** M5: PUT helper (notes upsert, research article replace). */
    protected ResponseEntity<String> put(String url, String token, Object body) {
        return rest.exchange(url, HttpMethod.PUT, entity(token, body), String.class);
    }

    protected ResponseEntity<String> delete(String url, String token) {
        return rest.exchange(url, HttpMethod.DELETE, entity(token, null), String.class);
    }

    /** Stable unique email per test run. */
    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@ikp.test";
    }
}
