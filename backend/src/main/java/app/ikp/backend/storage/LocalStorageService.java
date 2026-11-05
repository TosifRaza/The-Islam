package app.ikp.backend.storage;

import app.ikp.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Development object storage: private local filesystem under the configured
 * root. Layout: {root}/{bucket}/{yyyy}/{mm}/{ulid}-{sanitised-name}. Buckets
 * are private by construction (no static serving); access is only through the
 * authorised, short-lived signed-URL stream.
 */
@Service
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${app.storage.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create storage root " + this.root, e);
        }
    }

    @Override
    public String store(String bucket, String originalFilename, InputStream bytes, long size) {
        String safe = sanitise(originalFilename);
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        Path dir = root.resolve(bucket).resolve(String.format("%04d", now.getYear())).resolve(String.format("%02d", now.getMonthValue()));
        String key = bucket + "/" + String.format("%04d", now.getYear()) + "/" + String.format("%02d", now.getMonthValue())
                + "/" + UUID.randomUUID() + "-" + safe;
        Path target = root.resolve(key);
        try {
            Files.createDirectories(dir);
            try (bytes) {
                Files.copy(bytes, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw ApiException.of("SERVER-001", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "storage write failed");
        }
        return key;
    }

    @Override
    public InputStream open(String storageKey) {
        Path path = resolveKey(storageKey);
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw ApiException.notFound("object not found");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveKey(storageKey));
        } catch (IOException e) {
            throw ApiException.of("SERVER-001", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "storage delete failed");
        }
    }

    @Override
    public String promote(String quarantineKey) {
        if (!quarantineKey.startsWith(QUARANTINE + "/")) {
            throw ApiException.validation("only quarantine objects can be promoted");
        }
        String contentKey = CONTENT + "/" + quarantineKey.substring(QUARANTINE.length() + 1);
        try {
            Path from = resolveKey(quarantineKey);
            Path to = resolveKey(contentKey);
            Files.createDirectories(to.getParent());
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw ApiException.of("SERVER-001", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "promotion failed");
        }
        return contentKey;
    }

    private Path resolveKey(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            // path traversal guard (filename sanitisation + containment)
            throw ApiException.validation("invalid storage key");
        }
        return path;
    }

    /** Filename sanitisation per docs/07-security-plan.md §5. */
    static String sanitise(String filename) {
        String name = filename == null ? "file" : filename;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isBlank()) name = "file";
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
