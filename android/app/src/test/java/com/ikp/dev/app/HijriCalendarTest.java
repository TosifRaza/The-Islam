package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.chrono.HijrahEra;
import java.time.temporal.ChronoField;

/**
 * Hijri cross-validation (JVM). The app renders Hijri dates on device via
 * android.icu.util.IslamicCalendar (Umm al-Qura, API 24+); the HOST JDK
 * implements the SAME Umm al-Qura chronology (java.time HijrahChronology), so
 * this suite pins the well-known dates the UI depends on. Values verified
 * against the JDK's islamic-umalqura configuration.
 */
public class HijriCalendarTest {

    private static HijrahDate hijriOf(int y, int m, int d) {
        return HijrahChronology.INSTANCE.date(LocalDate.of(y, m, d));
    }

    /** 1 Muharram 1447 AH (Umm al-Qura civil date). */
    @Test
    public void knownDate_muharram1447() {
        HijrahDate hd = hijriOf(2025, 6, 26);
        assertEquals(1447, hd.get(ChronoField.YEAR));
        assertEquals(1, hd.get(ChronoField.MONTH_OF_YEAR));
        assertEquals(1, hd.get(ChronoField.DAY_OF_MONTH));
    }

    /** Eid al-Adha 1446 (10 Dhul Hijjah). */
    @Test
    public void knownDate_eidAlAdha1446() {
        HijrahDate hd = hijriOf(2025, 6, 6);
        assertEquals(12, hd.get(ChronoField.MONTH_OF_YEAR));
        assertEquals(10, hd.get(ChronoField.DAY_OF_MONTH));
    }

    @Test
    public void roundTripStable() {
        HijrahDate start = hijriOf(2025, 3, 1);
        assertEquals(start, HijrahChronology.INSTANCE.date(LocalDate.from(start)));
    }

    @Test
    public void supportedRangeCoversCurrentCentury() {
        int nowYear = HijrahDate.now().get(ChronoField.YEAR);
        assertTrue(nowYear > 1300 && nowYear < 1600);
        assertEquals(HijrahEra.AH, HijrahDate.now().getEra());
    }

    @Test
    public void monthLengthSanity() {
        int len = hijriOf(2025, 3, 1).lengthOfMonth();
        assertTrue(len == 29 || len == 30);
    }
}
