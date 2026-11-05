package app.ikp.backend.subject;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 Module C acceptance — subject/category management over the fixed
 * 15-position client reference. Category 06 stays hidden, unnamed and
 * unrenamable; stable slugs are the relationship key (sort_order is
 * display-only); the public API never leaks the reserved position.
 */
class SubjectCmsIntegrationTest extends IntegrationTestBase {

    @Test
    void adminSeesAll15IncludingReservedCategory06() {
        var cm = createAccount("content_manager", uniqueEmail("sub-cm"));
        var rows = readJson(get("/api/v1/admin/subjects", login(cm)).getBody()).path("data");
        assertThat(rows.size()).isEqualTo(15);
        boolean has06 = false;
        for (var row : rows) {
            if (row.path("slug").asText().equals("cat_unconfirmed_06")) {
                has06 = true;
                assertThat(row.path("status").asText()).isEqualTo("hidden_unconfirmed");
                assertThat(row.path("position").asInt()).isEqualTo(6);
            }
        }
        assertThat(has06).as("reserved category 06 visible to admins").isTrue();
    }

    @Test
    void publicListExcludesCategory06AndKeepsDisplayOrder() {
        var rows = readJson(rest.getForEntity("/api/v1/subjects", String.class).getBody()).path("data");
        List<Integer> positions = new ArrayList<>();
        for (var row : rows) {
            assertThat(row.path("slug").asText()).isNotEqualTo("cat_unconfirmed_06");
            positions.add(row.path("position").asInt());
        }
        assertThat(rows.size()).isEqualTo(14);
        assertThat(positions).startsWith(1).isSorted().contains(6 - 1, 6 + 1);
    }

    @Test
    void publicDetailOfReservedSlugIs404IndistinguishableFromAbsent() {
        var res = rest.getForEntity("/api/v1/subjects/cat_unconfirmed_06", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("NF-001");
    }

    @Test
    void adminCanUpdateOrdinaryMetadataWithoutCodeChange() {
        var cm = createAccount("content_manager", uniqueEmail("sub-edit"));
        String body = get("/api/v1/admin/subjects", login(cm)).getBody();
        var updated = patch("/api/v1/admin/subjects/cat_learning_academy", login(cm),
                Map.of("description", "Arabic language programmes, dictionary and courses (admin-editable metadata)",
                        "image", "https://cdn.example.test/img/learning.png"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(updated.getBody()).path("data").path("description").asText()).contains("admin-editable");
    }

    @Test
    void reservedCategory06CannotBeRenamedOrMovedToActiveState() {
        var sa = createAccount("super_admin", uniqueEmail("sub-sa06"));
        String token = login(sa);

        var rename = patch("/api/v1/admin/subjects/cat_unconfirmed_06", token, Map.of("name", "Invented Name"));
        assertThat(rename.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rename.getBody()).contains("WORKFLOW-001");

        var activate = patch("/api/v1/admin/subjects/cat_unconfirmed_06", token, Map.of("status", "active"));
        assertThat(activate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(activate.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");
    }

    @Test
    void createAndDeleteAreRefusedByDesign() {
        var cm = createAccount("content_manager", uniqueEmail("sub-create"));
        String token = login(cm);
        var created = post("/api/v1/admin/subjects", token, Map.of());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(created.getBody()).path("code").asText()).isEqualTo("WORKFLOW-001");

        var deleted = delete("/api/v1/admin/subjects/cat_aqeedah", token);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reorderRequiresCompletePositionSet1To15() {
        var cm = createAccount("content_manager", uniqueEmail("sub-order"));
        String token = login(cm);

        var bad = post("/api/v1/admin/subjects/reorder", token,
                Map.of("order", List.of(Map.of("subjectId", "cat_quran_tafsir", "position", 1))));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // valid swap of positions 2 and 3 (display order only — slugs unchanged)
        List<Map<String, Object>> order = new ArrayList<>();
        var rows = readJson(get("/api/v1/admin/subjects", token).getBody()).path("data");
        for (var row : rows) {
            String slug = row.path("slug").asText();
            int pos = row.path("position").asInt();
            if (pos == 2) pos = 3;
            else if (pos == 3) pos = 2;
            order.add(Map.of("subjectId", slug, "position", pos));
        }
        var ok = post("/api/v1/admin/subjects/reorder", token, Map.of("order", order));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(ok.getBody()).path("data").path("updated").asInt()).isGreaterThanOrEqualTo(2);

        // slugs are the stable join key; the swap must not rename anything
        var after = readJson(get("/api/v1/admin/subjects", token).getBody()).path("data");
        boolean had2 = false, had3 = false;
        for (var row : after) {
            if (row.path("slug").asText().equals("cat_hadith") && row.path("position").asInt() == 3) had2 = true;
            if (row.path("slug").asText().equals("cat_fiqh") && row.path("position").asInt() == 2) had3 = true;
        }
        assertThat(had2 && had3).as("display order changed while slugs stayed stable").isTrue();
    }

    @Test
    void registeredUserCannotUseAdminSubjectApis() {
        var reader = createAccount("registered_user", uniqueEmail("sub-reader"));
        assertThat(get("/api/v1/admin/subjects", login(reader)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void subjectWritesAreAudited() {
        var cm = createAccount("content_manager", uniqueEmail("sub-audit"));
        String token = login(cm);
        patch("/api/v1/admin/subjects/cat_duas", token, Map.of("description", "audit probe description"));
        var auditRows = readJson(get("/api/v1/admin/audit-logs?action=subject.update", saToken()).getBody()).path("data");
        boolean found = false;
        for (var row : auditRows) {
            if ("subject.update".equals(row.path("action").asText())
                    && row.path("afterHash").asText().length() == 64) found = true;
        }
        assertThat(found).as("subject.update audited with after-state hash").isTrue();
    }

    private String saToken() {
        return login(createAccount("super_admin", uniqueEmail("sub-sa-helper")));
    }
}
