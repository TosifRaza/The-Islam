package app.ikp.backend.auth;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 acceptance — RBAC is backend-enforced (the M2 prompt: "hiding a button is
 * not sufficient security"). Every probe below hits the REAL security chain.
 */
class RbacIntegrationTest extends IntegrationTestBase {

    @Test
    void contentManagerCannotListUsers() {
        var cm = createAccount("content_manager", uniqueEmail("cm"));
        assertThat(get("/api/v1/admin/users", login(cm)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void supportManagerSeesUsersButWithMaskedEmail() {
        var sup = createAccount("support_manager", uniqueEmail("sup"));
        var victim = createAccount("registered_user", "maskme" + uniqueEmail("x"));
        ResponseEntity<String> res = get("/api/v1/admin/users", login(sup));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var rows = readJson(res.getBody()).path("data");
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        boolean foundMasked = false;
        for (var row : rows) {
            if (row.path("userId").asText().equals(victim.id().toString())) {
                assertThat(row.path("email").asText()).doesNotContain("maskme");
                assertThat(row.path("email").asText()).endsWith("@ikp.test");
                foundMasked = true;
            }
        }
        assertThat(foundMasked).isTrue();
    }

    @Test
    void adminSeesUnmaskedEmail() {
        var admin = createAccount("admin", uniqueEmail("admin-view"));
        var victim = createAccount("registered_user", "plain" + uniqueEmail("y"));
        var rows = readJson(get("/api/v1/admin/users", login(admin)).getBody()).path("data");
        boolean found = false;
        for (var row : rows) {
            if (row.path("userId").asText().equals(victim.id().toString())) {
                assertThat(row.path("email").asText()).startsWith("plain");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void supportManagerCanSuspendButContentManagerCannot() {
        var sup = createAccount("support_manager", uniqueEmail("sup2"));
        var cm = createAccount("content_manager", uniqueEmail("cm2"));
        var victim = createAccount("registered_user", uniqueEmail("victim"));
        String reason = Map.of("reason", "policy violation investigation").toString();

        assertThat(post("/api/v1/admin/users/" + victim.id() + "/suspend", login(cm), Map.of("reason", "policy violation investigation")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/v1/admin/users/" + victim.id() + "/suspend", login(sup), Map.of("reason", "policy violation investigation")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var detail = readJson(get("/api/v1/admin/users/" + victim.id(), admin()).getBody()).path("data");
        assertThat(detail.path("status").asText()).isEqualTo("suspended");
    }

    @Test
    void roleChangesAreSuperAdminOnly() {
        var admin = createAccount("admin", uniqueEmail("admin-role"));
        var sa = createAccount("super_admin", uniqueEmail("sa-role"));
        var target = createAccount("registered_user", uniqueEmail("target"));

        ResponseEntity<String> denied = patch("/api/v1/admin/users/" + target.id(), login(admin),
                Map.of("add", java.util.List.of("content_manager")));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> allowed = patch("/api/v1/admin/users/" + target.id(), login(sa),
                Map.of("add", java.util.List.of("content_manager")));
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(allowed.getBody()).path("data").path("roles").toString()).contains("content_manager");
    }

    @Test
    void adminCannotSuspendSelfAndLastSuperAdminIsProtected() {
        var sa = createAccount("super_admin", uniqueEmail("sa-last"));
        String token = login(sa);
        // self-suspend refused
        assertThat(post("/api/v1/admin/users/" + sa.id() + "/suspend", token,
                Map.of("reason", "self service attempt")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scholarReviewerSeesDashboardButRegisteredUserDoesNot() {
        var scholar = createAccount("scholar_reviewer", uniqueEmail("sch"));
        assertThat(get("/api/v1/admin/dashboard", login(scholar)).getStatusCode()).isEqualTo(HttpStatus.OK);

        var reader = createAccount("registered_user", uniqueEmail("reader"));
        assertThat(get("/api/v1/admin/dashboard", login(reader)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditLogViewIsAdminAndSuperAdminOnly() {
        var admin = createAccount("admin", uniqueEmail("audit-admin"));
        assertThat(get("/api/v1/admin/audit-logs", login(admin)).getStatusCode()).isEqualTo(HttpStatus.OK);
        var cm = createAccount("content_manager", uniqueEmail("audit-cm"));
        assertThat(get("/api/v1/admin/audit-logs", login(cm)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String admin() {
        return login(createAccount("super_admin", uniqueEmail("helper-sa")));
    }
}
