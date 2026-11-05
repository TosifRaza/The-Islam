package app.ikp.backend.content;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin CMS management for M4 content domains (M4 §15). Licensing controls:
 * a NON-fixture edition can be published only with a licence attached (LIC-001);
 * fixture editions cannot be published when allow-fixtures=false; recitation
 * audio can never be published while rights are unverified; daily content
 * publish follows draft → approved → published (WORKFLOW-001); the canonical
 * Quran text has no write path.
 */
@RestController
@RequestMapping("/api/v1/admin/content")
@Tag(name = "content-admin-crud")
public class AdminContentController {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final boolean allowFixtures;

    public AdminContentController(JdbcTemplate jdbc, AuditService audit,
                                  @Value("${app.content.allow-fixtures:true}") boolean allowFixtures) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.allowFixtures = allowFixtures;
    }

    // ------------------------------------------------------------ hadith

    @PatchMapping("/hadith-collections/{code}")
    @Operation(summary = "Collection metadata / QA status / publish state")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchCollection(@PathVariable String code,
                                               @RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal SecurityPrincipal p) {
        Long id = require("hadith_collections", "code", code);
        patchColumns("hadith_collections", id, body, List.of(
                "edition_note", "numbering_note", "scholarly_qa_status", "publish_state"));
        audit.record(p.id(), "content.hadith_collection.update", "hadith_collection", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    @PatchMapping("/hadiths/{id}")
    @Operation(summary = "Hadith provenance fields")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchHadith(@PathVariable long id,
                                           @RequestBody Map<String, Object> body,
                                           @AuthenticationPrincipal SecurityPrincipal p) {
        require("hadiths", "id", String.valueOf(id));
        patchColumns("hadiths", id, body, List.of("narrator_chain", "takhrij", "reference_note", "publish_state"));
        audit.record(p.id(), "content.hadith.update", "hadith", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    @PutMapping("/hadiths/{id}/gradings")
    @Operation(summary = "Replace gradings — every entry MUST record gradedBy + source")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> putGradings(@PathVariable long id,
                                           @RequestBody List<Map<String, Object>> gradings,
                                           @AuthenticationPrincipal SecurityPrincipal p) {
        require("hadiths", "id", String.valueOf(id));
        for (Map<String, Object> g : gradings) {
            if (isBlank(g.get("gradedBy")) || isBlank(g.get("grade")) || isBlank(g.get("source"))) {
                throw ApiException.validation("each grading needs gradedBy, grade and source (no unprovenanced grading)");
            }
        }
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(gradings);
        } catch (Exception e) {
            throw ApiException.validation("gradings payload invalid");
        }
        jdbc.update("UPDATE hadiths SET gradings = ?::jsonb WHERE id = ?", json, id);
        audit.record(p.id(), "content.hadith.gradings.update", "hadith", id, null, gradings, null);
        return Pages.envelope(Map.of("id", id, "gradings", gradings));
    }

    // ---------------------------------------------- quran/tafsir editions + words

    @GetMapping("/quran/editions")
    @Operation(summary = "All translation editions incl. drafts (admin view)")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional(readOnly = true)
    public Map<String, Object> listQuranEditions() {
        return Pages.envelope(jdbc.queryForList("""
                SELECT id, code, language, display_name AS "displayName", translator,
                       source_register_id AS "sourceRegisterId", licence_id AS "licenceId",
                       is_fixture AS "isFixture", publish_state AS "publishState"
                FROM quran_translation_editions ORDER BY language, code
                """));
    }

    @PostMapping("/quran/editions")
    @Operation(summary = "Create a translation edition")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createTranslationEdition(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal SecurityPrincipal p) {
        String code = requireText(body, "code");
        jdbc.update("""
                INSERT INTO quran_translation_editions (code, language, display_name, translator,
                    source_register_id, licence_id, is_fixture, publish_state)
                VALUES (?, ?, ?, ?, ?, ?::bigint, ?, 'draft')
                """, code, requireText(body, "language"), requireText(body, "displayName"),
                body.get("translator"), (String) body.get("sourceRegisterId"),
                body.get("licenceId"), Boolean.TRUE.equals(body.get("isFixture")));
        audit.record(p.id(), "content.quran_edition.create", "quran_translation_edition", null, null, body, null);
        return Pages.envelope(Map.of("code", code));
    }

    @PatchMapping("/quran/editions/{id}")
    @Operation(summary = "Update a translation edition (publish gated)")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchTranslationEdition(@PathVariable long id,
                                                       @RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal SecurityPrincipal p) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT is_fixture, licence_id, publish_state FROM quran_translation_editions WHERE id = ?", id);
        publishGuard(body, row, "translation edition");
        patchColumns("quran_translation_editions", id, body, List.of("display_name", "translator", "publish_state"));
        audit.record(p.id(), "content.quran_edition.update", "quran_translation_edition", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    @GetMapping("/tafsir/editions")
    @Operation(summary = "All tafsir editions incl. drafts (admin view)")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional(readOnly = true)
    public Map<String, Object> listTafsirEditions() {
        return Pages.envelope(jdbc.queryForList("""
                SELECT id, code, name_en AS "nameEn", name_ar AS "nameAr", author,
                       source_register_id AS "sourceRegisterId", licence_id AS "licenceId",
                       is_fixture AS "isFixture", publish_state AS "publishState"
                FROM tafsir_editions ORDER BY code
                """));
    }

    @PatchMapping("/tafsir/editions/{id}")
    @Operation(summary = "Update a tafsir edition (same publish guard)")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchTafsirEdition(@PathVariable long id,
                                                  @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal SecurityPrincipal p) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT is_fixture, licence_id, publish_state FROM tafsir_editions WHERE id = ?", id);
        publishGuard(body, row, "tafsir edition");
        patchColumns("tafsir_editions", id, body, List.of("name_en", "name_ar", "author", "publish_state"));
        audit.record(p.id(), "content.tafsir_edition.update", "tafsir_edition", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    @PutMapping("/quran/words/{surahId}/{ayahNumber}")
    @Operation(summary = "Replace word-by-word rows for one ayah")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> putWords(@PathVariable int surahId, @PathVariable int ayahNumber,
                                        @RequestBody List<Map<String, Object>> words,
                                        @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("DELETE FROM quran_word_entries WHERE surah_id = ? AND ayah_number = ?", surahId, ayahNumber);
        int i = 0;
        for (Map<String, Object> w : words) {
            i++;
            jdbc.update("""
                    INSERT INTO quran_word_entries (surah_id, ayah_number, word_position, text_ar,
                        transliteration, meaning_en, meaning_ur, meaning_hi, is_fixture, publish_state)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, coalesce(?::boolean, true), coalesce(?, 'draft'))
                    """, surahId, ayahNumber, i, requireText(w, "textAr"), w.get("transliteration"),
                    w.get("meaningEn"), w.get("meaningUr"), w.get("meaningHi"),
                    String.valueOf(Boolean.TRUE.equals(w.getOrDefault("isFixture", true))),
                    String.valueOf(w.getOrDefault("publishState", "draft")));
        }
        audit.record(p.id(), "content.quran_words.replace", "quran_ayah", null, null,
                Map.of("surah", surahId, "ayah", ayahNumber, "count", words.size()), null);
        return Pages.envelope(Map.of("words", i));
    }

    // ---------------------------------------------------------- dictionary

    @PostMapping("/dictionary")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createDictionaryEntry(@RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("""
                INSERT INTO dictionary_entries (headword_ar, root, part_of_speech, transliteration,
                    pronunciation, meaning_en, meaning_ur, meaning_hi, source_register_id,
                    licence_id, is_fixture, publish_state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::bigint, coalesce(?::boolean, true), 'draft')
                """, requireText(body, "headwordAr"), body.get("root"), body.get("partOfSpeech"),
                body.get("transliteration"), body.get("pronunciation"), body.get("meaningEn"),
                body.get("meaningUr"), body.get("meaningHi"), (String) body.get("sourceRegisterId"),
                body.get("licenceId"), String.valueOf(Boolean.TRUE.equals(body.getOrDefault("isFixture", true))));
        audit.record(p.id(), "content.dictionary.create", "dictionary_entry", null, null, body, null);
        return Pages.envelope(Map.of("created", true));
    }

    @PatchMapping("/dictionary/{id}")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchDictionaryEntry(@PathVariable long id,
                                                    @RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal SecurityPrincipal p) {
        require("dictionary_entries", "id", String.valueOf(id));
        patchColumns("dictionary_entries", id, body, List.of(
                "headword_ar", "root", "part_of_speech", "transliteration", "pronunciation",
                "meaning_en", "meaning_ur", "meaning_hi", "publish_state"));
        audit.record(p.id(), "content.dictionary.update", "dictionary_entry", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    @DeleteMapping("/dictionary/{id}")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> deleteDictionaryEntry(@PathVariable long id,
                                                     @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("DELETE FROM dictionary_entries WHERE id = ?", id);
        audit.record(p.id(), "content.dictionary.delete", "dictionary_entry", id, null, null, null);
        return Pages.envelope(Map.of("deleted", true));
    }

    // ---------------------------------------------------------------- duas

    @PostMapping("/dua-categories")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createDuaCategory(@RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("""
                INSERT INTO dua_categories (code, name_en, name_ar, name_ur, name_hi, sort_order, publish_state)
                VALUES (?, ?, ?, ?, ?, coalesce(?::int, 100), 'draft')
                """, requireText(body, "code"), requireText(body, "nameEn"), body.get("nameAr"),
                body.get("nameUr"), body.get("nameHi"), String.valueOf(body.get("sortOrder")));
        return Pages.envelope(Map.of("created", true));
    }

    @PostMapping("/duas")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createDua(@RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("""
                INSERT INTO duas (category_id, title, text_ar, transliteration, meaning_en, meaning_ur,
                    meaning_hi, reference, source_attribution, authenticity_note, is_fixture, publish_state)
                SELECT id, ?, ?, ?, ?, ?, ?, ?, ?, ?, coalesce(?::boolean, false), 'draft'
                FROM dua_categories WHERE code = ?
                """, body.get("title"), requireText(body, "textAr"), body.get("transliteration"),
                body.get("meaningEn"), body.get("meaningUr"), body.get("meaningHi"),
                body.get("reference"), body.get("sourceAttribution"), body.get("authenticityNote"),
                String.valueOf(Boolean.TRUE.equals(body.getOrDefault("isFixture", false))),
                requireText(body, "categoryCode"));
        return Pages.envelope(Map.of("created", true));
    }

    @PatchMapping("/duas/{id}")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchDua(@PathVariable long id, @RequestBody Map<String, Object> body,
                                        @AuthenticationPrincipal SecurityPrincipal p) {
        require("duas", "id", String.valueOf(id));
        patchColumns("duas", id, body, List.of(
                "title", "text_ar", "transliteration", "meaning_en", "meaning_ur", "meaning_hi",
                "reference", "source_attribution", "authenticity_note", "publish_state"));
        return Pages.envelope(Map.of("id", id));
    }

    // ------------------------------------------------------------- calendar

    @PostMapping("/calendar/events")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createEvent(@RequestBody Map<String, Object> body,
                                           @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("""
                INSERT INTO islamic_events (code, name_en, name_ar, hijri_month, hijri_day, basis,
                    description, is_major, publish_state)
                VALUES (?, ?, ?, ?::int, ?::int, coalesce(?, 'calculated'), ?, ?, 'draft')
                """, requireText(body, "code"), requireText(body, "nameEn"), body.get("nameAr"),
                String.valueOf(body.get("hijriMonth")), String.valueOf(body.get("hijriDay")),
                (String) body.get("basis"), body.get("description"),
                String.valueOf(Boolean.TRUE.equals(body.getOrDefault("isMajor", false))));
        return Pages.envelope(Map.of("created", true));
    }

    @PatchMapping("/calendar/events/{code}")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchEvent(@PathVariable String code, @RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal SecurityPrincipal p) {
        Long id = require("islamic_events", "code", code);
        patchColumns("islamic_events", id, body, List.of(
                "name_en", "name_ar", "hijri_month", "hijri_day", "basis", "description", "publish_state"));
        return Pages.envelope(Map.of("code", code));
    }

    @PatchMapping("/calendar/conventions/{code}")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchConvention(@PathVariable String code,
                                               @RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal SecurityPrincipal p) {
        Long id = require("hijri_conventions", "code", code);
        patchColumns("hijri_conventions", id, body, List.of("name", "default_offset_days", "is_default"));
        return Pages.envelope(Map.of("code", code));
    }

    // -------------------------------------------------------- daily content

    @PostMapping("/daily")
    @Operation(summary = "Create a daily content item (draft)")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createDailyItem(@RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("""
                INSERT INTO daily_content_items (slot_code, publish_date, rotation_weight, content_type,
                    ayah_surah_id, ayah_number, hadith_id, dua_id, event_code, custom_text, attribution,
                    status, created_by)
                VALUES (?, coalesce(?::date, NULL), coalesce(?::int, 1), ?, ?::int, ?::int, ?::bigint,
                        ?::bigint, ?, ?, ?, 'draft', ?)
                """, requireText(body, "slotCode"), (String) body.get("publishDate"),
                String.valueOf(body.get("rotationWeight")), requireText(body, "contentType"),
                str(body.get("ayahSurahId")), str(body.get("ayahNumber")), str(body.get("hadithId")),
                str(body.get("duaId")), (String) body.get("eventCode"), (String) body.get("customText"),
                (String) body.get("attribution"), p.id());
        Long id = jdbc.queryForObject("SELECT max(id) FROM daily_content_items", Long.class);
        audit.record(p.id(), "content.daily.create", "daily_content_item", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER','QURAN_EDITOR','HADITH_EDITOR')")
    @Transactional(readOnly = true)
    public Map<String, Object> listDaily(@RequestParam(defaultValue = "50") int size) {
        int capped = Math.min(Math.max(1, size), 200);
        return Pages.envelope(jdbc.queryForList("""
                SELECT id, slot_code AS "slotCode", publish_date::text AS "publishDate", content_type AS "contentType",
                       status, attribution, created_at::text AS "createdAt"
                FROM daily_content_items ORDER BY id DESC LIMIT ?
                """, capped));
    }

    @PatchMapping("/daily/{id}")
    @Operation(summary = "Edit / approve / publish a daily item (workflow enforced)")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchDailyItem(@PathVariable long id, @RequestBody Map<String, Object> body,
                                              @AuthenticationPrincipal SecurityPrincipal p) {
        require("daily_content_items", "id", String.valueOf(id));
        String target = (String) body.get("status");
        if (target != null) {
            String current = jdbc.queryForObject(
                    "SELECT status FROM daily_content_items WHERE id = ?", String.class, id);
            switch (target) {
                case "in_editorial_review" -> requireState(current, "draft");
                case "approved" -> requireState(current, "in_editorial_review", "in_scholarly_review");
                case "published" -> {
                    requireState(current, "approved");
                    if (p.authorities().stream().noneMatch(a ->
                            a.getAuthority().equals("content.publish") ||
                            a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
                        throw new org.springframework.security.access.AccessDeniedException("content.publish required");
                    }
                    jdbc.update("UPDATE daily_content_items SET published_at = now() WHERE id = ?", id);
                }
                case "archived" -> { }
                default -> throw ApiException.validation("unsupported status " + target);
            }
        }
        patchColumns("daily_content_items", id, body, List.of(
                "publish_date", "rotation_weight", "custom_text", "attribution", "status"));
        audit.record(p.id(), "content.daily.update", "daily_content_item", id, null, body, null);
        return Pages.envelope(Map.of("id", id));
    }

    // ----------------------------------------------------------- recitation

    @PostMapping("/recitations")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> createRecitation(@RequestBody Map<String, Object> body,
                                                @AuthenticationPrincipal SecurityPrincipal p) {
        jdbc.update("""
                INSERT INTO recitation_editions (code, reciter_name, style, source_register_id,
                    licence_id, publish_state, notes)
                VALUES (?, ?, ?, ?, ?::bigint, 'disabled', ?)
                """, requireText(body, "code"), requireText(body, "reciterName"), body.get("style"),
                (String) body.get("sourceRegisterId"), body.get("licenceId"),
                "rights unverified — publishing blocked until licence verification (SR-13/14/15)");
        return Pages.envelope(Map.of("created", true));
    }

    @PatchMapping("/recitations/{id}")
    @PreAuthorize("hasAuthority('content.edit')")
    @Transactional
    public Map<String, Object> patchRecitation(@PathVariable long id, @RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal SecurityPrincipal p) {
        require("recitation_editions", "id", String.valueOf(id));
        if ("published".equalsIgnoreCase(String.valueOf(body.get("publishState")))) {
            throw ApiException.of("LIC-001", org.springframework.http.HttpStatus.FORBIDDEN,
                    "recitation audio cannot be published: redistribution rights unverified (SR-13/14/15)");
        }
        patchColumns("recitation_editions", id, body, List.of("reciter_name", "style", "publish_state", "notes"));
        return Pages.envelope(Map.of("id", id));
    }

    // ------------------------------------------------------------ internals

    private void publishGuard(Map<String, Object> body, Map<String, Object> row, String what) {
        if (body.get("publishState") != null && "published".equals(body.get("publishState"))) {
            boolean fixture = Boolean.TRUE.equals(row.get("is_fixture"));
            if (fixture && !allowFixtures) {
                throw ApiException.of("LIC-001", org.springframework.http.HttpStatus.FORBIDDEN,
                        "fixture content cannot be published in production mode");
            }
            if (!fixture && row.get("licence_id") == null) {
                throw ApiException.of("LIC-001", org.springframework.http.HttpStatus.FORBIDDEN,
                        what + " cannot be published without a verified licence");
            }
        }
    }

    private static void requireState(String current, String... allowed) {
        for (String a : allowed) if (a.equals(current)) return;
        throw ApiException.workflow("status transition not allowed from " + current);
    }

    private Long require(String table, String col, String val) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM " + table + " WHERE " + col + " = ?", Long.class, val);
        if (ids.isEmpty()) throw ApiException.notFound(table + " not found: " + val);
        return ids.get(0);
    }

    private void patchColumns(String table, long id, Map<String, Object> body, List<String> allowed) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String snake = camelToSnake(e.getKey());
            if (!allowed.contains(snake) || e.getValue() == null) continue;
            sets.add(snake + " = ?");
            args.add(e.getValue());
        }
        if (sets.isEmpty()) return;
        args.add(id);
        jdbc.update("UPDATE " + table + " SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
    }

    private static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) throw ApiException.validation(key + " is required");
        return String.valueOf(v);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }
}
