package app.ikp.backend.content;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for controlled imports and integrity (M4 §15/§16). Imports
 * run only against the server-side registered import directory — request
 * bodies never carry file paths. Canonical Quran text has NO update endpoint:
 * corrections follow the restricted correction process.
 */
@RestController
@RequestMapping("/api/v1/admin/content")
@Tag(name = "content-admin")
public class ContentAdminController {

    private final ContentImportService imports;
    private final IntegrityService integrity;
    private final JdbcTemplate jdbc;

    public ContentAdminController(ContentImportService imports, IntegrityService integrity, JdbcTemplate jdbc) {
        this.imports = imports;
        this.integrity = integrity;
        this.jdbc = jdbc;
    }

    @PostMapping("/import/quran")
    @Operation(summary = "Controlled import: Tanzil Quran text (SR-01, manifest-verified)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER')")
    public Map<String, Object> importQuran(@AuthenticationPrincipal SecurityPrincipal p) {
        return render(imports.importQuran(p.id()));
    }

    @PostMapping("/collections")
    @Operation(summary = "Register the six hadith collections (metadata + licence provenance)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER')")
    public Map<String, Object> registerCollections(@AuthenticationPrincipal SecurityPrincipal p) {
        return Pages.envelope(Map.of("registered", imports.registerHadithCollections()));
    }

    @PostMapping("/import/hadith/{code}")
    @Operation(summary = "Controlled import: one hadith collection (SR-08, manifest-verified)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER')")
    public Map<String, Object> importHadith(@PathVariable String code,
                                            @AuthenticationPrincipal SecurityPrincipal p) {
        return render(imports.importHadith(code, p.id()));
    }

    @PostMapping("/fixtures")
    @Operation(summary = "Seed labelled development fixtures (unresolved-rights content)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER')")
    public Map<String, Object> seedFixtures(@AuthenticationPrincipal SecurityPrincipal p) {
        return render(imports.seedFixtures(p.id()));
    }

    @GetMapping("/integrity")
    @Operation(summary = "Run automated integrity checks (M4 gate)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER','QURAN_EDITOR','HADITH_EDITOR')")
    public Map<String, Object> integrity() {
        return Pages.envelope(integrity.runAll());
    }

    @GetMapping("/imports")
    @Operation(summary = "Import batch history with manifests")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','CONTENT_MANAGER','QURAN_EDITOR','HADITH_EDITOR')")
    public Map<String, Object> history(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        int capped = Math.min(Math.max(1, size), 100);
        int offset = (Math.max(1, page) - 1) * capped;
        var rows = jdbc.queryForList("""
                SELECT id, source_register_id, content_kind, edition_code, status, integrity_status,
                       records_imported, imported_at::text AS imported_at
                FROM import_batches ORDER BY id DESC LIMIT ? OFFSET ?
                """, capped, offset);
        long total = jdbc.queryForObject("SELECT count(*) FROM import_batches", Long.class);
        return Pages.envelope(rows, new Pages.PageMeta(Math.max(1, page), capped, total,
                (int) Math.ceil(total / (double) capped)));
    }

    private Map<String, Object> render(ContentImportService.ImportResult r) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", r.batchId());
        data.put("kind", r.kind());
        data.put("sourceRegisterId", r.sourceRegisterId());
        data.put("recordsImported", r.recordsImported());
        data.put("integrity", r.integrity());
        return Pages.envelope(data);
    }
}
