package app.ikp.backend.content;

import app.ikp.backend.quran.QuranRepository;
import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 controlled-import + integrity gate (acceptance §5/§6). Imports the REAL
 * verified sources (SR-01 Tanzil; SR-08 hadith Bukhari edition files) plus the
 * labelled development fixtures, then asserts the integrity report passes and
 * the public API serves the content under the licensing rules.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M4ContentImportIntegrityTest extends IntegrationTestBase {

    @Autowired
    ContentImportService imports;
    @Autowired
    IntegrityService integrity;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @Order(1)
    void quranImport_isVerbatimAndComplete() {
        ContentImportService.ImportResult r = imports.importQuran(null);
        assertThat(r.recordsImported()).isEqualTo(6236);
        Integer surahs = jdbc.queryForObject("SELECT count(*) FROM quran_surahs", Integer.class);
        Integer ayahs = jdbc.queryForObject("SELECT count(*) FROM quran_ayahs", Integer.class);
        assertThat(surahs).isEqualTo(114);
        assertThat(ayahs).isEqualTo(6236);
        // verbatim spot-check: Ayat al-Kursi opens with the Uthmani wasla-form word
        String kursi = jdbc.queryForObject(
                "SELECT text_uthmani FROM quran_ayahs WHERE surah_id = 2 AND ayah_number = 255", String.class);
        assertThat(kursi).startsWith("\u0671\u0644\u0644\u0651\u064e\u0647\u064f");
        Integer sajdah = jdbc.queryForObject("SELECT count(*) FROM quran_ayahs WHERE sajdah", Integer.class);
        assertThat(sajdah).isEqualTo(15);
        Integer badJuz = jdbc.queryForObject(
                "SELECT count(*) FROM quran_ayahs WHERE juz IS NULL OR juz NOT BETWEEN 1 AND 30", Integer.class);
        assertThat(badJuz).isZero();
    }

    @Test
    @Order(2)
    void hadithImport_bukhariWithProvenance() {
        imports.registerHadithCollections();
        ContentImportService.ImportResult r = imports.importHadith("bukhari", null);
        assertThat(r.recordsImported()).isGreaterThanOrEqualTo(7000);
        Integer arabic = jdbc.queryForObject("""
                SELECT count(*) FROM hadiths h JOIN hadith_collections c ON c.id = h.collection_id
                WHERE c.code = 'bukhari' AND h.text_ar IS NOT NULL
                """, Integer.class);
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM hadiths h JOIN hadith_collections c ON c.id = h.collection_id
                WHERE c.code = 'bukhari'
                """, Integer.class);
        // cross-edition numbering gaps exist in the dataset (a handful of
        // urd-only/ara-only keys) — require ≥99% Arabic coverage
        assertThat(arabic).isGreaterThanOrEqualTo(total - 25);
        String numbering = jdbc.queryForObject(
                "SELECT numbering_note FROM hadith_collections WHERE code = 'bukhari'", String.class);
        assertThat(numbering).isNotBlank();
        Integer badGrades = jdbc.queryForObject("""
                SELECT count(*) FROM hadiths
                WHERE gradings::text <> '[]'
                  AND EXISTS (SELECT 1 FROM jsonb_array_elements(gradings) g
                              WHERE coalesce(g->>'gradedBy','') = '' OR coalesce(g->>'source','') = '')
                """, Integer.class);
        assertThat(badGrades).isZero();
    }

    @Test
    @Order(3)
    void fixturesAreLabelled() {
        imports.seedFixtures(null);
        Integer fixtureEditions = jdbc.queryForObject(
                "SELECT count(*) FROM quran_translation_editions WHERE is_fixture", Integer.class);
        assertThat(fixtureEditions).isGreaterThanOrEqualTo(3);
        Integer realEditions = jdbc.queryForObject(
                "SELECT count(*) FROM quran_translation_editions WHERE NOT is_fixture", Integer.class);
        assertThat(realEditions).isZero();
        Integer tafsirs = jdbc.queryForObject("SELECT count(*) FROM tafsir_editions", Integer.class);
        assertThat(tafsirs).isEqualTo(4);
    }

    @Test
    @Order(4)
    void integrityReportPasses() {
        Map<String, Object> report = integrity.runAll();
        assertThat(report.get("passed")).as("report: %s", report).isEqualTo(true);
    }

    @Test
    @Order(5)
    void fixtureGate_hidesFixturesInProductionPosture() {
        QuranRepository restricted = new QuranRepository(jdbc, false);
        assertThat(restricted.publishedTranslationEditions()).isEmpty();
        QuranRepository dev = new QuranRepository(jdbc, true);
        List<Map<String, Object>> devEditions = dev.publishedTranslationEditions();
        assertThat(devEditions).isNotEmpty();
        assertThat(devEditions).anySatisfy(e -> assertThat(e.get("isFixture")).isEqualTo(true));
    }

    @Test
    @Order(6)
    void publicQuranApi_servesSurahsAndAyahs() {
        ResponseEntity<String> surahs = rest.getForEntity("/api/v1/quran/surahs", String.class);
        assertThat(surahs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(surahs.getBody()).path("data").size()).isEqualTo(114);
        ResponseEntity<String> ayahs = rest.getForEntity(
                "/api/v1/quran/surahs/2/ayahs?page=2&limit=5", String.class);
        assertThat(ayahs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(ayahs.getBody()).path("meta").path("totalElements").asLong()).isEqualTo(286);
        ResponseEntity<String> noAuth = rest.getForEntity("/api/v1/quran/ayahs/2:255", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(noAuth.getBody()).path("data").path("textUthmani").asText())
                .startsWith("\u0671\u0644\u0644\u0651\u064e\u0647\u064f");
    }

    @Test
    @Order(7)
    void publicHadithApi_servesCollectionsAndBooks() {
        ResponseEntity<String> cols = rest.getForEntity("/api/v1/hadith/collections", String.class);
        assertThat(cols.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(cols.getBody()).path("data").size()).isEqualTo(6);
        ResponseEntity<String> books = rest.getForEntity(
                "/api/v1/hadith/collections/bukhari/books", String.class);
        assertThat(readJson(books.getBody()).path("data").size()).isGreaterThanOrEqualTo(90);
        long bookId = readJson(books.getBody()).path("data").get(0).path("id").asLong();
        ResponseEntity<String> hadiths = rest.getForEntity(
                "/api/v1/hadith/books/" + bookId + "/hadiths?page=1&limit=3", String.class);
        assertThat(hadiths.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(hadiths.getBody()).path("data").size()).isEqualTo(3);
    }

    @Test
    @Order(8)
    void dailyContentAndHome_servePublishedRecordsOnly() {
        ResponseEntity<String> daily = rest.getForEntity("/api/v1/home/daily", String.class);
        assertThat(daily.getStatusCode()).isEqualTo(HttpStatus.OK);
        var slot = readJson(daily.getBody()).path("data").path("ayah_of_day");
        assertThat(slot.path("ayah").path("surahId").asInt()).isEqualTo(2);
        assertThat(slot.path("attribution").asText()).contains("Tanzil");
        ResponseEntity<String> home = rest.getForEntity("/api/v1/home", String.class);
        assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(home.getBody()).path("data").path("hijriToday").path("calculated").asBoolean())
                .isTrue();
    }

    @Test
    @Order(9)
    void globalSearch_groupsAreLiveFromM5() {
        // M5 contract change (evidence: docs-m5/m5-api-integration-report.md):
        // the courses/research groups are real queries now — no later-milestone stub.
        ResponseEntity<String> res2 = rest.getForEntity("/api/v1/search?q=prayer", String.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);
        var groups = readJson(res2.getBody()).path("data");
        assertThat(groups.path("courses").has("total")).isTrue();
        assertThat(groups.path("research").has("total")).isTrue();
        assertThat(groups.path("hadith").path("total").asInt()).isGreaterThanOrEqualTo(0);
    }
}
