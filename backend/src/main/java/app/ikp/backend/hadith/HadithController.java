package app.ikp.backend.hadith;

import app.ikp.backend.common.ApiException;
import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public Hadith API (M4 §8). Grading provenance and "scholarly QA pending"
 * state travel with every payload; nothing fabricates narrators or gradings
 * that the dataset lacks.
 */
@RestController
@RequestMapping("/api/v1/hadith")
@Tag(name = "hadith")
public class HadithController {

    private final HadithRepository repo;
    private final JdbcTemplate jdbc;

    public HadithController(HadithRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @GetMapping("/collections")
    @Operation(summary = "The six collections with provenance + QA status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> collections() {
        return ResponseEntity.ok(Pages.envelope(repo.collections()));
    }

    @GetMapping("/collections/{code}/books")
    @Operation(summary = "Books of a collection")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> books(@PathVariable String code) {
        if (repo.collection(code) == null) throw ApiException.notFound("collection not found");
        return ResponseEntity.ok(Pages.envelope(repo.books(code)));
    }

    @GetMapping("/books/{bookId}/hadiths")
    @Operation(summary = "Hadiths of a book (paginated)")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> hadiths(@PathVariable long bookId,
                                                       @RequestParam(defaultValue = "20") int limit,
                                                       @RequestParam(defaultValue = "1") int page) {
        if (repo.book(bookId) == null) throw ApiException.notFound("book not found");
        int capped = Math.min(Math.max(1, limit), 100);
        int offset = (Math.max(1, page) - 1) * capped;
        long total = jdbc.queryForObject("SELECT count(*) FROM hadiths WHERE book_id = ?", Long.class, bookId);
        long totalPages = (total + capped - 1) / capped;
        return ResponseEntity.ok(Pages.envelope(repo.hadiths(bookId, offset, capped),
                new Pages.PageMeta(Math.max(1, page), capped, total, (int) totalPages)));
    }

    @GetMapping("/hadiths/{id}")
    @Operation(summary = "Hadith detail with provenance")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> hadith(@PathVariable long id) {
        Map<String, Object> h = repo.hadith(id);
        if (h == null) throw ApiException.notFound("hadith not found");
        return ResponseEntity.ok(Pages.envelope(h));
    }

    @GetMapping("/search")
    @Operation(summary = "Search across published collections")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q,
                                                      @RequestParam(required = false) String collection,
                                                      @RequestParam(defaultValue = "20") int limit,
                                                      @RequestParam(defaultValue = "1") int page) {
        if (q == null || q.isBlank()) throw ApiException.validation("q is required");
        if (collection != null && repo.collection(collection) == null) {
            throw ApiException.notFound("collection not found");
        }
        int capped = Math.min(Math.max(1, limit), 50);
        int offset = (Math.max(1, page) - 1) * capped;
        return ResponseEntity.ok(Pages.envelope(repo.search(q, collection, offset, capped)));
    }
}
