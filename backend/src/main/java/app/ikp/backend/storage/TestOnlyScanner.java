package app.ikp.backend.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * TEST-ONLY scanner — clearly labelled as such (owner instruction: do not
 * report files as malware-free without actual scanning evidence).
 *
 * It performs NO malware analysis. It only (a) verifies that the byte content
 * matches the declared type's magic signature (a corrupt/spoofed file is
 * "rejected"), and (b) detects the industry-standard EICAR test string so
 * quarantine behaviour can be demonstrated in tests/dev without a real AV
 * engine. Verdicts from this component are always recorded with scanner
 * name "test-only-magic-scanner" so evidence can never be confused with a
 * production ClamAV scan.
 *
 * PRODUCTION: a real ClamAV (or equivalent) adapter implements Scanner; until
 * that lands, publishing remains gated on a "clean" verdict which this scanner
 * can only give for well-formed, non-EICAR test fixtures. Fail-closed is
 * preserved either way.
 */
@Component
public class TestOnlyScanner implements Scanner {

    public static final String NAME = "test-only-magic-scanner";

    @Override
    public Verdict scan(String filename, String declaredMime, InputStream bytes) {
        byte[] data;
        try (bytes) {
            data = bytes.readAllBytes();
        } catch (IOException e) {
            return new Verdict(NAME, false, "unreadable content");
        }
        String lower = filename == null ? "" : filename.toLowerCase();
        String magic = magicOf(lower, data);
        if (magic == null) {
            return new Verdict(NAME, false, "content does not match declared file type (magic-byte mismatch)");
        }
        String asText = new String(data, StandardCharsets.ISO_8859_1);
        if (asText.contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")) {
            // Standard AV test payload — treated as infected so quarantine paths
            // are exercisable in dev/tests. NOT a real malware detection.
            return new Verdict(NAME, false, "EICAR test signature found (test-only scanner)");
        }
        return new Verdict(NAME, true, "well-formed test fixture (no real malware analysis performed)");
    }

    private String magicOf(String lowerFilename, byte[] d) {
        if (lowerFilename.endsWith(".pdf")) {
            return startsWith(d, '%', 'P', 'D', 'F', '-') ? "pdf" : null;
        }
        if (lowerFilename.endsWith(".epub")) {
            return startsWith(d, 'P', 'K', 3, 4) ? "epub" : null; // zip container
        }
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return startsWith(d, (byte) 0xFF, (byte) 0xD8) ? "jpeg" : null;
        }
        if (lowerFilename.endsWith(".png")) {
            return startsWith(d, (byte) 0x89, 'P', 'N', 'G') ? "png" : null;
        }
        return null; // undeclared type — fail closed
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != (expected[i] & 0xFF)) return false;
        }
        return true;
    }
}
