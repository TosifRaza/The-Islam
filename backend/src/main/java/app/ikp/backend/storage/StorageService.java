package app.ikp.backend.storage;

import java.io.InputStream;

/**
 * Object-storage abstraction (approved architecture). Development binding:
 * private local filesystem buckets under app.storage.root with strict
 * quarantine/content separation. Production S3-compatible storage (OD-06)
 * replaces the binding without touching callers.
 */
public interface StorageService {

    /** Buckets: quarantine (unscanned/infected) and content (scan-clean only). */
    String QUARANTINE = "quarantine";
    String CONTENT = "content";

    /** Stores the bytes privately and returns the storage key (bucket/key). */
    String store(String bucket, String originalFilename, InputStream bytes, long size);

    InputStream open(String storageKey);

    void delete(String storageKey);

    /** quarantine/… → content/… promotion after a clean scan verdict. */
    String promote(String quarantineKey);
}
