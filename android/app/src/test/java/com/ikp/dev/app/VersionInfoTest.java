package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/**
 * JVM-only unit test (runs without the Android SDK — plain JUnit on the
 * app's pure-Java classes). Device/instrumentation tests come with the UI phase.
 */
public class VersionInfoTest {

    @Test
    public void displayFormatsVersionMilestoneAndCode() {
        VersionInfo info = new VersionInfo("0.1.0-m1", 1, "M1 foundation");
        assertEquals("0.1.0-m1 (1) — M1 foundation", info.display());
    }

    @Test
    public void rejectsEmptyVersionName() {
        assertThrows(IllegalArgumentException.class, () -> new VersionInfo("", 1, "M1"));
    }

    @Test
    public void rejectsNonPositiveVersionCode() {
        assertThrows(IllegalArgumentException.class, () -> new VersionInfo("0.1.0-m1", 0, "M1"));
    }
}
