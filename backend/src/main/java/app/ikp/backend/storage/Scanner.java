package app.ikp.backend.storage;

import java.io.InputStream;

/**
 * Malware scanner port (docs/07-security-plan.md §5). The platform fails
 * closed: only a "clean" verdict from a configured scanner allows promotion
 * out of quarantine and any downstream publishing/download. No file is ever
 * reported malware-free without an actual scan verdict.
 */
public interface Scanner {

    record Verdict(String scannerName, boolean clean, String detail) {
    }

    /** @return the verdict for the object bytes; must never return null. */
    Verdict scan(String filename, String declaredMime, InputStream bytes);
}
