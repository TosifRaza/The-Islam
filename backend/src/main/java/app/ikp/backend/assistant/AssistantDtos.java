package app.ikp.backend.assistant;

import java.util.List;

public final class AssistantDtos {

    public record AssistantQueryRequest(
            String query,
            String conversationId,
            String language
    ) {}

    public record SourceCitationDto(
            String type,            // "article", "quran", "hadith", "fatwa", "scholar"
            String id,              // target UUID or key
            String title,
            String subtitle,
            String referenceCode,   // e.g. "Surah 2:255" or "Sahih Muslim #2699"
            String targetRoute
    ) {}

    public record AssistantResponseDto(
            String answerId,
            String query,
            String title,
            String answer,
            List<SourceCitationDto> citations,
            List<String> followUps,
            String groundedStatus,   // "FULLY_GROUNDED", "PARTIALLY_GROUNDED", "INSUFFICIENT_DATA"
            String disclaimer
    ) {}

    public record SuggestionDto(
            String id,
            String question,
            String topic,
            String preview
    ) {}

    public record AssistantFeedbackRequest(
            String answerId,
            String query,
            String rating,          // "positive", "negative"
            String feedback
    ) {}

    private AssistantDtos() {}
}
