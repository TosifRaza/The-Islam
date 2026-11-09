package app.ikp.backend.assistant;

import app.ikp.backend.common.Pages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI Research Assistant API (M6 Research Subsystem).
 * Formulates structured answers grounded strictly in peer-reviewed research articles,
 * verified fatwas, authenticated Hadith, and the Holy Qur'an.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Curated research questions grounded in approved library")
    public ResponseEntity<Map<String, Object>> suggestions() {
        List<AssistantDtos.SuggestionDto> list = assistantService.getSuggestions();
        return ResponseEntity.ok(Pages.envelope(list));
    }

    @PostMapping("/query")
    @Operation(summary = "Ask research question grounded in approved scholarly library")
    public ResponseEntity<Map<String, Object>> query(@RequestBody AssistantDtos.AssistantQueryRequest req) {
        AssistantDtos.AssistantResponseDto res = assistantService.processQuery(req);
        return ResponseEntity.ok(Pages.envelope(res));
    }

    @PostMapping("/feedback")
    @Operation(summary = "Record user feedback for assistant answers")
    public ResponseEntity<Map<String, Object>> feedback(@RequestBody AssistantDtos.AssistantFeedbackRequest req) {
        assistantService.recordFeedback(req);
        return ResponseEntity.ok(Pages.envelope(Map.of("status", "recorded")));
    }

    @GetMapping("/overview")
    @Operation(summary = "Overview of indexed knowledge and assistant metrics (Admin CMS)")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(Pages.envelope(assistantService.getAssistantOverview()));
    }
}
