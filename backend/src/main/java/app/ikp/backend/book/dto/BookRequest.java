package app.ikp.backend.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Book create/update payload (approved spec §3.5; M2 Module D). The subject is
 * referenced by its stable CAT-* slug (the stable category ID). */
public record BookRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 300) String subtitle,
        @Size(max = 5000) String description,
        @NotBlank @Pattern(regexp = "ar|en|ur|hi") String language,
        @NotBlank String subjectId,
        List<UUID> authorIds,
        @Size(max = 200) String publisher,
        @Size(max = 100) String edition,
        @Min(1000) @Max(2100) Integer publicationYear,
        @Min(1) Integer pageCount,
        @Size(max = 32) String isbn,
        @Size(max = 500) String copyrightInfo,
        Long licenceId,
        Boolean downloadAllowed,
        Boolean scholarlyReviewRequired,
        UUID coverMediaId) {
}
