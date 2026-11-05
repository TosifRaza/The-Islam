package app.ikp.backend.scholars;

import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Public scholar directory (spec Module 14). Only VERIFIED profiles are
 * listed publicly; review history is derived from recorded approval audit
 * trails, never claimed without a record.
 */
@RestController
@RequestMapping("/api/v1/scholars")
@Tag(name = "scholars")
public class PublicScholarController {

    private final ScholarService service;

    public PublicScholarController(ScholarService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Verified scholar directory")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<Scholar> result = service.publicList(page, size);
        return ResponseEntity.ok(Pages.envelope(result, s -> service.view(s, false)));
    }

    @GetMapping("/{scholarId}")
    @Operation(summary = "Verified scholar profile with review history")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID scholarId) {
        Scholar s = service.scholar(scholarId);
        if (s.getVerificationStatus() != Scholar.VerificationStatus.verified) {
            throw app.ikp.backend.common.ApiException.notFound("scholar not found");
        }
        Map<String, Object> body = service.view(s, false);
        body.put("reviewHistory", service.reviewHistory(s));
        return ResponseEntity.ok(Pages.envelope(body));
    }
}
