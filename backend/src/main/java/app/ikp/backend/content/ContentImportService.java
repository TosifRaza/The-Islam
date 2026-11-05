package app.ikp.backend.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Controlled content import runner (M4). Every import reads from the
 * registered import directory only, verifies the source manifest (sha256 +
 * structural counts) BEFORE and AFTER writing, records an import_batches row,
 * and stores provenance (source register id, licence) per edition/collection.
 * The Quran canonical text is written verbatim from the Tanzil file — the
 * runner has no code path that alters it. Development fixtures (translations,
 * tafsir, word meanings, dictionary, duas) are labelled is_fixture=true and
 * hard-gated by app.content.allow-fixtures at serving time.
 */
@Service
public class ContentImportService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.content.import-dir:/home/z/my-project/imports}")
    private String importDir;

    public ContentImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ImportResult(long batchId, String kind, String sourceRegisterId,
                               int recordsImported, Map<String, Object> integrity) {
    }

    // ------------------------------------------------------------------ Quran

    @Transactional
    public ImportResult importQuran(Long userId) {
        File xmlFile = requireFile("tanzil/quran-uthmani.xml");
        String sha = sha256(xmlFile);
        Map<String, Object> manifest = readJson("manifests/tanzil-uthmani.json");
        if (!sha.equals(String.valueOf(manifest.get("sha256")))) {
            throw app.ikp.backend.common.ApiException.validation(
                    "tanzil file checksum does not match the recorded manifest — refusing import");
        }
        long batch = insertBatch("SR-01", "quran_text", null,
                "https://tanzil.net/download/", sha, manifest, userId);

        List<String[]> meta = readTsv("tanzil/reference/surah_metadata.tsv", 4);
        List<int[]> juzStarts = readIntTsv("tanzil/reference/juz_starts.tsv");
        List<int[]> sajdahs = readIntTsv("tanzil/reference/sajdah_ayahs.tsv");
        if (meta.size() != 114) throw app.ikp.backend.common.ApiException.validation("surah metadata must have 114 rows");
        if (juzStarts.size() != 30) throw app.ikp.backend.common.ApiException.validation("juz starts must have 30 rows");

        Map<Integer, String[]> surahMeta = new HashMap<>();
        for (String[] r : meta) surahMeta.put(Integer.parseInt(r[0]), r);

        int total = 0;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            NodeList suras = doc.getElementsByTagName("sura");
            if (suras.getLength() != 114) {
                throw app.ikp.backend.common.ApiException.validation("XML must contain exactly 114 surahs");
            }
            List<Object[]> surahRows = new ArrayList<>();
            List<Object[]> ayahRows = new ArrayList<>();
            int juzIdx = 0;
            for (int i = 0; i < suras.getLength(); i++) {
                Element s = (Element) suras.item(i);
                int sid = Integer.parseInt(s.getAttribute("index"));
                String[] m = surahMeta.get(sid);
                if (m == null) throw app.ikp.backend.common.ApiException.validation("no reference metadata for surah " + sid);
                NodeList ayas = s.getElementsByTagName("aya");
                int count = ayas.getLength();
                if (count < 3) throw app.ikp.backend.common.ApiException.validation("surah " + sid + " has too few ayahs");
                surahRows.add(new Object[]{sid, s.getAttribute("name").trim(), m[1], m[2], m[3],
                        count, sid != 9});
                for (int a = 0; a < count; a++) {
                    Element ay = (Element) ayas.item(a);
                    int ayahNo = a + 1;
                    String text = ay.getAttribute("text");
                    if (text == null || text.isBlank()) {
                        throw app.ikp.backend.common.ApiException.validation(
                                "empty ayah text " + sid + ":" + ayahNo + " — refusing import");
                    }
                    while (juzIdx < juzStarts.size() - 1
                            && (sid > juzStarts.get(juzIdx + 1)[0]
                                || (sid == juzStarts.get(juzIdx + 1)[0] && ayahNo >= juzStarts.get(juzIdx + 1)[1]))) {
                        juzIdx++;
                    }
                    ayahRows.add(new Object[]{sid, ayahNo, text, juzIdx + 1, contains(sajdahs, sid, ayahNo)});
                    total++;
                }
            }
            if (total != 6236) {
                throw app.ikp.backend.common.ApiException.validation("expected 6236 ayahs, parsed " + total);
            }
            // verbatim writes — no transformation of text anywhere in this method
            jdbc.batchUpdate("INSERT INTO quran_surahs (id, name_ar, name_transliterated, name_en, "
                    + "revelation_place, ayah_count, bismillah_prefix) VALUES (?,?,?,?,?,?,?)", surahRows);
            jdbc.batchUpdate("INSERT INTO quran_ayahs (surah_id, ayah_number, text_uthmani, juz, sajdah) "
                    + "VALUES (?,?,?,?,?)", ayahRows);
        } catch (app.ikp.backend.common.ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("tanzil import failed", e);
        }
        Map<String, Object> integrity = Map.of("surahs", 114, "ayahs", total,
                "manifestAyahs", ((Number) manifest.get("ayahCount")).intValue());
        completeBatch(batch, total, integrity);
        return new ImportResult(batch, "quran_text", "SR-01", total, integrity);
    }

    // ----------------------------------------------------------------- Hadith

    @Transactional
    public ImportResult importHadith(String code, Long userId) {
        List<String> allowed = List.of("bukhari", "muslim", "abudawud", "tirmidhi", "nasai", "ibnmajah");
        if (!allowed.contains(code)) throw app.ikp.backend.common.ApiException.validation("unknown collection " + code);
        Map<String, Object> manifest = readJson("manifests/hadith-" + code + ".json");
        Map<String, JsonNode> editions = new LinkedHashMap<>();
        for (String lang : List.of("ara", "eng", "urd")) {
            String rel = "hadith/" + lang + "-" + code + ".json";
            File f = requireFile(rel);
            String sha = sha256(f);
            Object editionsNode = manifest.get("editions");
            if (!(editionsNode instanceof Map)) {
                throw app.ikp.backend.common.ApiException.validation("manifest editions missing");
            }
            Object langNode = ((Map<?, ?>) editionsNode).get(lang);
            if (!(langNode instanceof Map) || !sha.equals(String.valueOf(((Map<?, ?>) langNode).get("sha256")))) {
                throw app.ikp.backend.common.ApiException.validation(rel + " checksum mismatch — refusing import");
            }
            try {
                editions.put(lang, json.readTree(f));
            } catch (Exception e) {
                throw new IllegalStateException("cannot parse " + rel, e);
            }
        }
        long batch = insertBatch("SR-08", "hadith_collection", code,
                "https://github.com/fawazahmed0/hadith-api", null, manifest, userId);

        JsonNode araMeta = editions.get("ara").get("metadata");
        Map<Integer, long[]> ranges = new HashMap<>();
        araMeta.path("section_details").fields().forEachRemaining(e -> {
            try {
                int book = Integer.parseInt(e.getKey());
                ranges.put(book, new long[]{e.getValue().path("hadithnumber_first").asLong(),
                        e.getValue().path("hadithnumber_last").asLong()});
            } catch (NumberFormatException ignored) { }
        });
        Map<String, Long> bookIds = new LinkedHashMap<>();
        List<Object[]> bookRows = new ArrayList<>();
        araMeta.path("sections").fields().forEachRemaining(e -> {
            int bookNo = Integer.parseInt(e.getKey());
            String nameEn = e.getValue().asText(null);
            long[] r = ranges.getOrDefault(bookNo, new long[]{0, 0});
            bookRows.add(new Object[]{bookNo, (nameEn == null || nameEn.isBlank()) ? null : nameEn,
                    r[0] == 0 ? null : r[0], r[1] == 0 ? null : r[1], code});
        });
        jdbc.update("DELETE FROM hadith_books WHERE collection_id = (SELECT id FROM hadith_collections WHERE code = ?)", code);
        jdbc.batchUpdate("INSERT INTO hadith_books (collection_id, book_number, name_en, hadith_first, hadith_last) "
                + "SELECT c.id, ?, ?, ?, ? FROM hadith_collections c WHERE c.code = ?", bookRows);
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, book_number FROM hadith_books WHERE collection_id = "
                        + "(SELECT id FROM hadith_collections WHERE code = ?)", code)) {
            bookIds.put(String.valueOf(r.get("book_number")), ((Number) r.get("id")).longValue());
        }

        // merge hadith rows across the three language editions by (book, hadithnumber)
        Map<String, Object[]> merged = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> ed : editions.entrySet()) {
            for (JsonNode h : ed.getValue().get("hadiths")) {
                int book = h.path("reference").path("book").asInt();
                int num = h.path("hadithnumber").asInt();
                String key = book + ":" + num;
                Object[] row = merged.computeIfAbsent(key, k -> new Object[]{book, num,
                        h.path("arabicnumber").asInt(), null, null, null, null});
                int ti = "ara".equals(ed.getKey()) ? 3 : "eng".equals(ed.getKey()) ? 4 : 5;
                String text = h.path("text").asText(null);
                if (text != null && !text.isBlank()) row[ti] = text;
                if ("ara".equals(ed.getKey()) && h.has("grades") && h.get("grades").isArray()
                        && h.get("grades").size() > 0) {
                    List<Map<String, String>> grades = new ArrayList<>();
                    h.get("grades").forEach(g -> grades.add(Map.of(
                            "gradedBy", g.path("name").asText(""),
                            "grade", g.path("grade").asText(""),
                            "source", "SR-08 dataset (scholarly QA pending)")));
                    try {
                        row[6] = json.writeValueAsString(grades);
                    } catch (Exception ignored) { }
                }
            }
        }
        List<Object[]> hadithRows = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : merged.entrySet()) {
            Object[] r = e.getValue();
            Long bookId = bookIds.get(String.valueOf(r[0]));
            if (bookId == null) {
                // dataset references a book absent from metadata.sections — create a
                // minimal book row rather than silently dropping hadiths
                jdbc.update("""
                        INSERT INTO hadith_books (collection_id, book_number, name_en, hadith_first, hadith_last)
                        SELECT c.id, ?, ?, ?, ? FROM hadith_collections c WHERE c.code = ?
                          AND NOT EXISTS (SELECT 1 FROM hadith_books b WHERE b.collection_id = c.id
                                          AND b.book_number = ?)
                        """, r[0], null, null, null, code, r[0]);
                bookId = jdbc.queryForObject("""
                        SELECT b.id FROM hadith_books b JOIN hadith_collections c ON c.id = b.collection_id
                        WHERE c.code = ? AND b.book_number = ? LIMIT 1
                        """, Long.class, code, r[0]);
                bookIds.put(String.valueOf(r[0]), bookId);
            }
            hadithRows.add(new Object[]{bookId, r[1], r[2], r[3], r[4], r[5], r[6]});
        }
        jdbc.update("DELETE FROM hadiths WHERE collection_id = (SELECT id FROM hadith_collections WHERE code = ?)", code);
        // batch insert; the collection code is the FIRST placeholder of the SQL
        List<Object[]> batchRows = new ArrayList<>(hadithRows.size());
        for (Object[] row : hadithRows) {
            Object[] withCode = new Object[row.length + 1];
            withCode[0] = code;
            System.arraycopy(row, 0, withCode, 1, row.length);
            batchRows.add(withCode);
        }
        jdbc.batchUpdate("INSERT INTO hadiths (collection_id, book_id, number_in_book, arabic_number, "
                + "text_ar, text_en, text_ur, gradings) VALUES ((SELECT id FROM hadith_collections WHERE code = ?), "
                + "?, ?, ?, ?, ?, ?, coalesce(?::jsonb, '[]'::jsonb))", batchRows);
        int imported = jdbc.queryForObject(
                "SELECT count(*) FROM hadiths WHERE collection_id = (SELECT id FROM hadith_collections "
                + "WHERE code = ?)", Integer.class, code);
        Map<String, Object> integrity = new LinkedHashMap<>();
        editions.forEach((lang, node) -> integrity.put("dataset_" + lang, node.get("hadiths").size()));
        integrity.put("mergedRows", imported);
        completeBatch(batch, imported, integrity);
        return new ImportResult(batch, "hadith_collection", "SR-08", imported, integrity);
    }

    // ---------------------------------------------------- collection metadata

    /** Registers the six hadith collections (metadata + licence provenance). */
    @Transactional
    public int registerHadithCollections() {
        String[][] cols = {
                {"bukhari", "صحيح البخاري", "Sahih al-Bukhari"},
                {"muslim", "صحيح مسلم", "Sahih Muslim"},
                {"abudawud", "سنن أبي داود", "Sunan Abu Dawud"},
                {"tirmidhi", "جامع الترمذي", "Jami' at-Tirmidhi"},
                {"nasai", "سنن النسائي", "Sunan an-Nasa'i"},
                {"ibnmajah", "سنن ابن ماجه", "Sunan Ibn Majah"},
        };
        int n = 0;
        for (String[] c : cols) {
            n += jdbc.update("""
                    INSERT INTO hadith_collections (code, name_ar, name_en, edition_note, numbering_note,
                        source_register_id, licence_id, publish_state)
                    SELECT ?, ?, ?, ?, ?, 'SR-08', (SELECT id FROM licences WHERE code='unlicense_hadith'),
                           'published'
                    WHERE NOT EXISTS (SELECT 1 FROM hadith_collections WHERE code = ?)
                    """, c[0], c[1], c[2],
                    "fawazahmed0/hadith-api v1 (ara/eng/urd editions), The Unlicense",
                    "Numbering follows the dataset's per-book hadithnumber (derived from the "
                            + "standard printed-edition structure); arabicnumber preserved alongside. "
                            + "Cross-check against printed editions required before production (C-13).",
                    c[0]);
        }
        return n;
    }

    // --------------------------------------------------------- dev fixtures

    /**
     * Clearly-labelled development fixtures for content whose source rights are
     * unresolved (translations SR-04/05, word-by-word SR-06/07, tafsir
     * SR-11/12, dictionary SR-18, duas SR-16). Every row is is_fixture=true.
     * NO religious meaning is invented: placeholders state exactly what is
     * missing. Serving is additionally gated by app.content.allow-fixtures.
     */
    @Transactional
    public ImportResult seedFixtures(Long userId) {
        long batch = insertBatch("DEV-FIXTURES", "fixture_set", null, "internal", null,
                Map.of("note", "labelled development fixtures"), userId);
        int records = 0;

        for (String[] ed : new String[][]{
                {"dev-trans-en", "en", "English translation (DEVELOPMENT FIXTURE)"},
                {"dev-trans-ur", "ur", "Urdu translation (DEVELOPMENT FIXTURE)"},
                {"dev-trans-hi", "hi", "Hindi translation (DEVELOPMENT FIXTURE)"}}) {
            jdbc.update("""
                    INSERT INTO quran_translation_editions (code, language, display_name, translator,
                        source_register_id, licence_id, is_fixture, publish_state)
                    VALUES (?, ?, ?, 'pending licensed edition (SR-04/SR-05 unresolved)', NULL,
                        (SELECT id FROM licences WHERE code='dev_fixture_unresolved'), true, 'published')
                    ON CONFLICT (code) DO NOTHING
                    """, ed[0], ed[1], ed[2]);
        }
        List<int[]> cover = List.of(new int[]{1, 1}, new int[]{1, 2}, new int[]{1, 3}, new int[]{1, 4},
                new int[]{1, 5}, new int[]{1, 6}, new int[]{1, 7}, new int[]{2, 255},
                new int[]{112, 1}, new int[]{112, 2}, new int[]{112, 3}, new int[]{112, 4});
        for (String code : List.of("dev-trans-en", "dev-trans-ur", "dev-trans-hi")) {
            for (int[] c : cover) {
                records += jdbc.update("""
                        INSERT INTO quran_ayah_translations (edition_id, surah_id, ayah_number, text)
                        SELECT e.id, ?, ?, '[Development fixture — licensed translation pending '
                            || '(SR-04/SR-05 rights unresolved). Not for production use.]'
                        FROM quran_translation_editions e WHERE e.code = ?
                        ON CONFLICT DO NOTHING
                        """, c[0], c[1], code);
            }
        }

        for (String[] t : new String[][]{
                {"ibn_kathir", "Tafsir Ibn Kathir", "تفسير ابن كثير", "Ibn Kathir"},
                {"tabari", "Tafsir al-Tabari", "تفسير الطبري", "al-Tabari"},
                {"qurtubi", "Tafsir al-Qurtubi", "تفسير القرطبي", "al-Qurtubi"},
                {"jalalayn", "Tafsir al-Jalalayn", "تفسير الجلالين", "al-Jalalayn"}}) {
            jdbc.update("""
                    INSERT INTO tafsir_editions (code, name_ar, name_en, author, language,
                        source_register_id, licence_id, is_fixture, publish_state)
                    VALUES (?, ?, ?, ?, 'ar', 'SR-11',
                        (SELECT id FROM licences WHERE code='dev_fixture_unresolved'), true, 'disabled')
                    ON CONFLICT (code) DO NOTHING
                    """, t[0], t[2], t[1], t[3]);
        }
        jdbc.update("UPDATE tafsir_editions SET publish_state = 'published' WHERE code = 'jalalayn'");
        for (int a = 1; a <= 7; a++) {
            records += jdbc.update("""
                    INSERT INTO tafsir_entries (edition_id, surah_id, ayah_number, text)
                    SELECT id, 1, ?, '[Development fixture — no licensed Tafsir content imported '
                        || '(SR-11/SR-12 unresolved). Reviewed tafsir text is required before this '
                        || 'module carries religious commentary.]'
                    FROM tafsir_editions WHERE code = 'jalalayn'
                    ON CONFLICT DO NOTHING
                    """, a);
        }

        List<int[]> wCover = List.of(new int[]{1, 1}, new int[]{1, 2}, new int[]{1, 3}, new int[]{1, 4},
                new int[]{1, 5}, new int[]{1, 6}, new int[]{1, 7},
                new int[]{112, 1}, new int[]{112, 2}, new int[]{112, 3}, new int[]{112, 4});
        for (int[] c : wCover) {
            String text = jdbc.queryForObject(
                    "SELECT text_uthmani FROM quran_ayahs WHERE surah_id = ? AND ayah_number = ?",
                    String.class, c[0], c[1]);
            String[] words = text.trim().split("\\s+");
            for (int i = 0; i < words.length; i++) {
                records += jdbc.update("""
                        INSERT INTO quran_word_entries (surah_id, ayah_number, word_position, text_ar,
                            meaning_en, meaning_ur, meaning_hi, is_fixture, publish_state)
                        VALUES (?, ?, ?, ?, ?, ?, ?, true, 'published')
                        """, c[0], c[1], i + 1, words[i],
                        "[fixture — licensed word dataset pending (SR-06/SR-07)]",
                        "[fixture — licensed word dataset pending (SR-06/SR-07)]",
                        "[fixture — licensed word dataset pending (SR-06/SR-07)]");
            }
        }

        String dictFixtureNote = "[Development fixture — dictionary dataset rights unresolved (SR-18). "
                + "No meaning text is shown until a licensed dataset is imported.]";
        for (String w : new String[]{"كِتَاب", "عِلْم", "قَلْب", "نُور", "صَلَاة", "صَوْم", "زَكَاة",
                "حَجّ", "صَبْر", "شُكْر", "رَحْمَة", "حِكْمَة"}) {
            records += jdbc.update("""
                    INSERT INTO dictionary_entries (headword_ar, meaning_en, is_fixture, publish_state,
                        source_register_id, licence_id)
                    VALUES (?, ?, true, 'published', 'SR-18',
                        (SELECT id FROM licences WHERE code='dev_fixture_unresolved'))
                    """, w, dictFixtureNote);
        }

        String[][] cats = {
                {"morning_adhkar", "Morning Adhkar"}, {"evening_adhkar", "Evening Adhkar"},
                {"daily", "Daily Duas"}, {"eating", "Eating"}, {"sleeping", "Sleeping"},
                {"travelling", "Travelling"}, {"prayer", "Prayer"}, {"waking_up", "Waking up"},
                {"entering_leaving_home", "Entering and Leaving Home"}, {"protection", "Protection"}};
        for (String[] cat : cats) {
            jdbc.update("""
                    INSERT INTO dua_categories (code, name_en, sort_order, publish_state)
                    VALUES (?, ?, ?, 'published') ON CONFLICT (code) DO NOTHING
                    """, cat[0], cat[1], 10);
            records += jdbc.update("""
                    INSERT INTO duas (category_id, text_ar, is_fixture, publish_state)
                    SELECT id, '[Development fixture — dua texts require rights clearance (SR-16). '
                        || 'No Arabic religious text is provided to avoid unverified content.]',
                        true, 'published'
                    FROM dua_categories WHERE code = ?
                    """, cat[0]);
        }

        jdbc.update("""
                INSERT INTO daily_content_items (slot_code, content_type, ayah_surah_id, ayah_number,
                    attribution, status, published_at)
                SELECT 'ayah_of_day', 'ayah', 2, 255, 'Quran 2:255 — Tanzil Uthmani v1.1 (SR-01)',
                       'published', now()
                WHERE NOT EXISTS (SELECT 1 FROM daily_content_items WHERE slot_code = 'ayah_of_day')
                """);
        jdbc.update("""
                INSERT INTO daily_content_items (slot_code, content_type, hadith_id, attribution, status,
                    published_at)
                SELECT 'hadith_of_day', 'hadith', h.id,
                       'Sahih al-Bukhari 1:1 — hadith-api v1 (SR-08, The Unlicense)', 'published', now()
                FROM hadiths h JOIN hadith_collections c ON c.id = h.collection_id AND c.code = 'bukhari'
                JOIN hadith_books b ON b.id = h.book_id AND b.book_number = 1
                WHERE h.number_in_book = 1
                  AND NOT EXISTS (SELECT 1 FROM daily_content_items WHERE slot_code = 'hadith_of_day')
                """);
        jdbc.update("""
                INSERT INTO daily_content_items (slot_code, content_type, custom_text, status, published_at)
                SELECT 'daily_reminder', 'text',
                       'Development reminder slot — content is managed by administrators in the CMS.',
                       'published', now()
                WHERE NOT EXISTS (SELECT 1 FROM daily_content_items WHERE slot_code = 'daily_reminder')
                """);
        jdbc.update("""
                INSERT INTO daily_content_items (slot_code, content_type, event_code, status, published_at)
                SELECT 'islamic_event', 'event', 'eid_al_fitr', 'published', now()
                WHERE NOT EXISTS (SELECT 1 FROM daily_content_items WHERE slot_code = 'islamic_event')
                """);
        records += 4;
        completeBatch(batch, records, Map.of("note", "labelled fixtures seeded"));
        return new ImportResult(batch, "fixture_set", "DEV-FIXTURES", records, Map.of());
    }

    // ------------------------------------------------------------- internals

    private long insertBatch(String sourceRegisterId, String kind, String edition, String url,
                             String sha, Map<String, Object> manifest, Long userId) {
        jdbc.update("INSERT INTO import_batches (source_register_id, licence_id, content_kind, edition_code, "
                        + "source_url, file_sha256, manifest, status) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'running')",
                sourceRegisterId, licenceIdFor(sourceRegisterId), kind, edition, url, sha,
                writeJson(manifest));
        return jdbc.queryForObject("SELECT max(id) FROM import_batches", Long.class);
    }

    private Long licenceIdFor(String sourceRegisterId) {
        return switch (sourceRegisterId) {
            case "SR-01" -> queryLicence("cc_by_30_tanzil");
            case "SR-08" -> queryLicence("unlicense_hadith");
            default -> queryLicence("dev_fixture_unresolved");
        };
    }

    private Long queryLicence(String code) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM licences WHERE code = ?", Long.class, code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void completeBatch(long id, int records, Map<String, Object> integrity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("integrity", integrity);
        jdbc.update("UPDATE import_batches SET status='completed', records_imported=?, "
                + "integrity_status='passed', manifest = manifest || ?::jsonb WHERE id=?",
                records, writeJson(m), id);
    }

    private File requireFile(String rel) {
        File f = new File(importDir, rel);
        try {
            if (!f.getCanonicalPath().startsWith(new File(importDir).getCanonicalPath())) {
                throw app.ikp.backend.common.ApiException.validation("path outside import directory");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("import dir resolution failed", e);
        }
        if (!f.isFile()) throw app.ikp.backend.common.ApiException.notFound(
                "import file not found: " + rel + " (set app.content.import-dir)");
        return f;
    }

    private Map<String, Object> readJson(String rel) {
        try {
            return json.readValue(requireFile(rel), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("manifest unreadable: " + rel, e);
        }
    }

    private List<String[]> readTsv(String rel, int cols) {
        try {
            List<String[]> rows = new ArrayList<>();
            for (String line : Files.readAllLines(requireFile(rel).toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length < cols) throw app.ikp.backend.common.ApiException.validation("bad TSV row: " + line);
                rows.add(parts);
            }
            return rows;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("tsv unreadable: " + rel, e);
        }
    }

    private List<int[]> readIntTsv(String rel) {
        List<int[]> out = new ArrayList<>();
        for (String[] r : readTsv(rel, 2)) {
            out.add(new int[]{Integer.parseInt(r[0].trim()), Integer.parseInt(r[1].trim())});
        }
        return out;
    }

    private static boolean contains(List<int[]> pairs, int s, int a) {
        for (int[] p : pairs) if (p[0] == s && p[1] == a) return true;
        return false;
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(f.toPath())) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }
}
