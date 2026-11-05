package app.ikp.backend.fatwa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Fatwa create payload (approved spec §3.17 admin family). */
public record FatwaRequest(
        @NotBlank @Size(max = 5000) String question,
        @NotBlank @Size(max = 20000) String answerDraft,
        @Size(max = 60) String category,
        @Pattern(regexp = "ar|en|ur|hi") String language,
        List<String> sourceRefs) {
}
