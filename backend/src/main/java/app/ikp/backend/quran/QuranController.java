package app.ikp.backend.quran;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public Quran API (M4 §7). Guests may read (whitelisted GETs). Every response
 * carries licence attribution for the Tanzil text (SR-01 CC BY 3.0) and fixture
 * labels where applicable. Audio metadata reports the honest "no licensed
 * recitation" state (SR-13/14/15 unresolved — no downloads).
 */
@RestController
@RequestMapping("/api/v1/quran")
@Tag(name = "quran")
public class QuranController {

    private final QuranRepository repo;

    public QuranController(QuranRepository repo) {
        this.repo = repo;
    }

    private static final String ATTRIBUTION =
            "Quran text: Tanzil Quran Text (Uthmani, v1.1), CC BY 3.0 — tanzil.net";

    @GetMapping("/surahs")
    @Operation(summary = "All 114 surahs with metadata")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> surahs() {
        return ResponseEntity.ok(Pages.envelope(repo.surahs()));
    }

    @GetMapping("/surahs/{surahId}")
    @Operation(summary = "Surah detail")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> surah(@PathVariable int surahId) {
        Map<String, Object> s = repo.surah(surahId);
        if (s == null) throw ApiException.notFound("surah not found");
        Map<String, Object> data = new LinkedHashMap<>(s);
        data.put("translations", repo.publishedTranslationEditions());
        data.put("tafsirs", repo.publishedTafsirEditions());
        data.put("attribution", ATTRIBUTION);
        return ResponseEntity.ok(Pages.envelope(data));
    }

    @GetMapping("/surahs/{surahId}/ayahs")
    @Operation(summary = "Ayahs of a surah, optionally with translation/tafsir/words")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> ayahs(@PathVariable int surahId,
                                                     @RequestParam(required = false) String translation,
                                                     @RequestParam(required = false) String tafsir,
                                                     @RequestParam(defaultValue = "false") boolean words,
                                                     @RequestParam(defaultValue = "50") int limit,
                                                     @RequestParam(defaultValue = "1") int page) {
        if (repo.surah(surahId) == null) throw ApiException.notFound("surah not found");
        int capped = Math.min(Math.max(1, limit), 100);
        int offset = (Math.max(1, page) - 1) * capped;
        List<Map<String, Object>> ayahs = repo.ayahs(surahId, offset, capped);
        Map<String, List<Map<String, Object>>> translationsByAyah = new LinkedHashMap<>();
        if (translation != null && !translation.isBlank()) {
            for (Map<String, Object> t : repo.translationsForSurah(surahId, translation, offset, capped)) {
                translationsByAyah.computeIfAbsent(String.valueOf(t.get("ayahNumber")),
                        k -> new ArrayList<>()).add(t);
            }
        }
        Map<Integer, Map<String, Object>> tafsirByAyah = new LinkedHashMap<>();
        if (tafsir != null && !tafsir.isBlank()) {
            for (Map<String, Object> t : repo.tafsirForSurah(surahId, tafsir, offset, capped)) {
                tafsirByAyah.put((Integer) t.get("ayahNumber"), t);
            }
        }
        List<Map<String, Object>> data = new ArrayList<>();
        boolean fixtureUsed = false;
        for (Map<String, Object> a : ayahs) {
            Map<String, Object> row = new LinkedHashMap<>(a);
            row.put("attribution", ATTRIBUTION);
            Integer ayahNumber = (Integer) a.get("ayahNumber");
            if (!translationsByAyah.isEmpty()) {
                row.put("translation", translationsByAyah.get(String.valueOf(ayahNumber)));
            } else if (translation != null && !translation.isBlank()) {
                row.put("translation", List.of());
            }
            if (tafsir != null && !tafsir.isBlank()) {
                row.put("tafsir", tafsirByAyah.get(ayahNumber));
            }
            if (words) {
                row.put("words", repo.wordsForAyah(surahId, ayahNumber));
            }
            if (Boolean.TRUE.equals(row.get("isFixture"))) fixtureUsed = true;
            if (row.get("translation") instanceof List<?> l && !l.isEmpty()
                    && l.get(0) instanceof Map<?, ?> t && Boolean.TRUE.equals(t.get("isFixture"))) fixtureUsed = true;
            if (row.get("tafsir") instanceof Map<?, ?> t && Boolean.TRUE.equals(t.get("isFixture"))) fixtureUsed = true;
            data.add(row);
        }
        if (fixtureUsed) {
            for (Map<String, Object> row : data) row.put("fixtureContent", true);
        }
        long total = repo.ayahCount(surahId);
        long totalPages = (total + capped - 1) / capped;
        return ResponseEntity.ok(Pages.envelope(data,
                new Pages.PageMeta(Math.max(1, page), capped, total, (int) totalPages)));
    }

    @GetMapping("/ayahs/{surahId}:{ayahNumber}")
    @Operation(summary = "Single ayah study view: translations, tafsirs, words, notes")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> ayah(@PathVariable int surahId,
                                                    @PathVariable int ayahNumber) {
        Map<String, Object> a = repo.ayah(surahId, ayahNumber);
        if (a == null) throw ApiException.notFound("ayah not found");
        Map<String, Object> data = new LinkedHashMap<>(a);
        data.put("attribution", ATTRIBUTION);
        data.put("translations", repo.translationsForAyah(surahId, ayahNumber));
        data.put("words", repo.wordsForAyah(surahId, ayahNumber));
        data.put("studyNotes", repo.studyNotes(surahId, ayahNumber));
        data.put("tafsirs", repo.publishedTafsirEditions());
        return ResponseEntity.ok(Pages.envelope(data));
    }

    @GetMapping("/translations")
    @Operation(summary = "Available translation editions (licence + fixture state)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> translations() {
        return ResponseEntity.ok(Pages.envelope(repo.publishedTranslationEditions()));
    }

    @GetMapping("/tafsirs")
    @Operation(summary = "Available tafsir editions")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> tafsirs() {
        return ResponseEntity.ok(Pages.envelope(repo.publishedTafsirEditions()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search Arabic text and published translations")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q,
                                                      @RequestParam(defaultValue = "20") int limit,
                                                      @RequestParam(defaultValue = "1") int page) {
        if (q == null || q.isBlank()) throw ApiException.validation("q is required");
        int capped = Math.min(Math.max(1, limit), 50);
        int offset = (Math.max(1, page) - 1) * capped;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("arabic", repo.searchArabic(q, offset, capped));
        data.put("translations", repo.searchTranslations(q, offset, capped));
        return ResponseEntity.ok(Pages.envelope(data));
    }
}
