package app.ikp.backend.storage;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.Pages;
import app.ikp.backend.storage.dto.MediaView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Admin file upload & inventory per approved spec §3.19.2–3.19.4. Auth:
 * files.upload (Super Admin/Admin/Content Manager per matrix "Books / Upload
 * files"). Files are private by default; download access only via short-lived
 * signed URLs.
 */
@RestController
@RequestMapping("/api/v1/admin/files")
@Tag(name = "admin-files")
public class AdminFileController {

    private final MediaService mediaService;
    private final DownloadTokenService downloadTokens;

    public AdminFileController(MediaService mediaService, DownloadTokenService downloadTokens) {
        this.mediaService = mediaService;
        this.downloadTokens = downloadTokens;
    }

    @PostMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Upload a content file (spec §3.19.2) — quarantined until scan passes")
    @PreAuthorize("hasAuthority('files.upload')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam String purpose,
                                                      @RequestParam(required = false) Long licenceId,
                                                      @AuthenticationPrincipal SecurityPrincipal actor) {
        MediaAsset asset = mediaService.upload(file, purpose, licenceId, actor);
        return ResponseEntity.accepted().body(Pages.envelope(MediaService.toView(asset)));
    }

    @GetMapping
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "File inventory with filters (spec §3.19.4)")
    @PreAuthorize("hasAuthority('files.upload')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String purpose,
                                                    @RequestParam(required = false) String scanStatus,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<MediaAsset> result = mediaService.search(purpose, scanStatus, page, size);
        return ResponseEntity.ok(Pages.envelope(result, MediaService::toView));
    }

    @GetMapping("/{mediaId}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Scan state + metadata (spec §3.19.3)")
    @PreAuthorize("hasAuthority('files.upload')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID mediaId) {
        return ResponseEntity.ok(Pages.envelope(MediaService.toView(mediaService.byPublicId(mediaId))));
    }

    @PostMapping("/{mediaId}/download-token")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Authorised short-lived download URL (admin; ≤15 min)")
    @PreAuthorize("hasAuthority('files.upload')")
    public ResponseEntity<Map<String, Object>> downloadToken(@PathVariable UUID mediaId, HttpServletRequest http) {
        MediaAsset asset = mediaService.byPublicId(mediaId);
        if (!asset.isClean()) {
            // Quarantined files are never exposed to application users.
            throw app.ikp.backend.common.ApiException.of("UPLOAD-002",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "media is not scan-clean; download refused");
        }
        String base = http.getScheme() + "://" + http.getServerName() + (http.getServerPort() == 80 || http.getServerPort() == 8080 ? "" : ":" + http.getServerPort());
        DownloadTokenService.SignedUrl url = downloadTokens.sign(base, asset.getPublicId());
        return ResponseEntity.ok(Pages.envelope(Map.of(
                "mediaId", asset.getPublicId(), "url", url.url(), "expiresAt", String.valueOf(url.expiresAt()),
                "sizeBytes", asset.getSizeBytes() == null ? 0 : asset.getSizeBytes(), "sha256", String.valueOf(asset.getSha256()))));
    }
}
