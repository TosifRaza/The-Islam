package app.ikp.backend.storage;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Signed-URL media delivery. The endpoint itself is "public" but every request
 * must carry a valid short-lived HMAC signature (validated here); quarantined
 * or rejected objects are never served. Private user uploads live under the
 * separate user-uploads domain and never in the content bucket (strict
 * personal-library separation).
 */
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "files")
public class MediaFileController {

    private final MediaAssetRepository media;
    private final StorageService storage;
    private final DownloadTokenService tokens;

    public MediaFileController(MediaAssetRepository media, StorageService storage, DownloadTokenService tokens) {
        this.media = media;
        this.storage = storage;
        this.tokens = tokens;
    }

    @GetMapping("/{mediaId}")
    @Operation(summary = "Stream a media object with a valid signed URL")
    public ResponseEntity<InputStreamResource> stream(@PathVariable UUID mediaId,
                                                      @RequestParam(required = false) String token,
                                                      @RequestParam(required = false) String exp) {
        if (!tokens.validate(mediaId, token, exp)) {
            throw ApiException.of("AUTH-002", org.springframework.http.HttpStatus.UNAUTHORIZED, "invalid or expired media token");
        }
        MediaAsset asset = media.findByPublicId(mediaId).orElseThrow(() -> ApiException.notFound("media not found"));
        if (!asset.isClean()) {
            // fail closed: quarantined/infected/rejected never leave storage
            throw ApiException.of("UPLOAD-002", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "media is not scan-clean");
        }
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mt = MediaType.parseMediaType(asset.getMime());
        } catch (Exception ignored) {
            // keep octet-stream
        }
        return ResponseEntity.ok()
                .contentType(mt)
                .header("Content-Disposition", "attachment; filename=\"" + (asset.getOriginalFilename() == null ? "file" : asset.getOriginalFilename()) + "\"")
                .body(new InputStreamResource(storage.open(asset.getStorageKey())));
    }
}
