package app.ikp.backend.storage;

import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.audit.AuditService;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.storage.dto.MediaView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Secure upload pipeline (approved spec §3.19.2; docs/07-security-plan.md §5):
 * validation (extension + MIME + magic bytes + size + filename sanitisation +
 * quota) → private quarantine storage → scan → clean promotes to the content
 * bucket, infected/rejected stays quarantined and is never served. Uploads are
 * NEVER public by default and never auto-published.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    public static final List<String> PURPOSES =
            List.of("book_pdf", "book_epub", "cover", "subject_image", "audio", "course_video", "research_attachment",
                    "personal_pdf", "personal_epub", "personal_cover");  // M4: private personal-library buckets

    private final MediaAssetRepository media;
    private final StorageService storage;
    private final Scanner scanner;
    private final AuditService audit;
    private final long maxBytes;
    private final long quotaBytes;

    public MediaService(MediaAssetRepository media, StorageService storage, Scanner scanner, AuditService audit,
                        @Value("${app.upload.max-bytes}") long maxBytes,
                        @Value("${app.upload.quota-bytes-per-user}") long quotaBytes) {
        this.media = media;
        this.storage = storage;
        this.scanner = scanner;
        this.audit = audit;
        this.maxBytes = maxBytes;
        this.quotaBytes = quotaBytes;
    }

    @Transactional
    public MediaAsset upload(MultipartFile file, String purpose, Long licenceId, SecurityPrincipal actor) {
        if (purpose == null || !PURPOSES.contains(purpose)) {
            throw ApiException.validation("purpose must be one of " + PURPOSES);
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw ApiException.validation("file name required");
        }
        String lower = original.toLowerCase(Locale.ROOT);
        boolean isImage = "cover".equals(purpose) || "subject_image".equals(purpose);
        boolean extensionOk = isImage
                ? lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                : "book_pdf".equals(purpose) ? lower.endsWith(".pdf")
                : "book_epub".equals(purpose) ? lower.endsWith(".epub")
                : true; // other purposes: extension validated by magic-byte gate below
        if (!extensionOk) {
            throw ApiException.of("UPLOAD-001", org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "file extension does not match the upload purpose");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.of("UPLOAD-003", org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "file exceeds the size cap");
        }
        long used = media.quotaUsedBy(actor.id());
        if (used + file.getSize() > quotaBytes) {
            throw ApiException.of("QUOTA-001", org.springframework.http.HttpStatus.FORBIDDEN,
                    "storage quota exceeded");
        }
        String declaredMime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        // 1. store privately in quarantine (never publicly reachable)
        String key;
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("unreadable upload");
        }
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            key = storage.store(StorageService.QUARANTINE, original, in, bytes.length);
        } catch (IOException e) {
            throw ApiException.of("SERVER-001", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "storage failure");
        }
        MediaAsset asset = new MediaAsset();
        asset.setStorageKey(key);
        asset.setMime(declaredMime);
        asset.setSizeBytes((long) bytes.length);
        asset.setSha256(sha256(bytes));
        asset.setPurpose(purpose);
        asset.setOriginalFilename(LocalStorageService.sanitise(original));
        asset.setUploadedBy(actor.id());
        asset.setLicenceId(licenceId);
        asset.setScanStatus("pending");
        media.save(asset);

        // 2. scan (synchronous pipeline; verdict recorded — fail closed)
        Scanner.Verdict verdict;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            verdict = scanner.scan(asset.getOriginalFilename(), declaredMime, in);
        } catch (IOException e) {
            verdict = new Scanner.Verdict("pipeline", false, "unreadable content");
        }
        asset.setScanner(verdict.scannerName());
        asset.setScannedAt(java.time.Instant.now());
        if (verdict.clean()) {
            asset.setStorageKey(storage.promote(key));
            asset.setScanStatus("clean");
        } else {
            // magic mismatch = rejected (type gate); anything else = infected (quarantined)
            if (verdict.detail() != null && verdict.detail().contains("magic-byte mismatch")) {
                asset.setScanStatus("rejected");
            } else {
                asset.setScanStatus("infected");
            }
            log.warn("UPLOAD quarantined media={} reason={}", asset.getPublicId(), verdict.detail());
            audit.record(actor.id(), "file.quarantined", "media", asset.getId(), null,
                    Map.of("detail", String.valueOf(verdict.detail()), "scanner", verdict.scannerName()), null);
        }
        media.save(asset);
        audit.record(actor.id(), "file.upload", "media", asset.getId(), null,
                Map.of("purpose", purpose, "scanStatus", asset.getScanStatus(), "sizeBytes", bytes.length), null);
        return asset;
    }

    @Transactional(readOnly = true)
    public Page<MediaAsset> search(String purpose, String scanStatus, int page, int size) {
        return media.findAll(MediaAssetRepository.filter(nullIfBlank(purpose), nullIfBlank(scanStatus)),
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
    }

    @Transactional(readOnly = true)
    public MediaAsset byPublicId(UUID publicId) {
        return media.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("media not found"));
    }

    public static MediaView toView(MediaAsset m) {
        return new MediaView(m.getPublicId(), m.getPurpose(), m.getOriginalFilename(), m.getMime(),
                m.getSizeBytes(), m.getScanStatus(), m.getScanner(), m.getCreatedAt());
    }

    private static String nullIfBlank(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
