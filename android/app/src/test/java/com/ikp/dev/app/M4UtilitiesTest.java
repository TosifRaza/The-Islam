package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.Qibla;
import com.batoulapps.adhan.data.DateComponents;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * M4 utilities logic tests (JVM). Prayer-time vectors are the PUBLISHED
 * reference values from the Adhan project test suite (upstream, MIT); Qibla
 * bearings cross-checked against the great-circle formula to the Kaaba.
 */
public class M4UtilitiesTest {

    /** Upstream reference: Raleigh NC, 2015-07-12, ISNA + Hanafi asr. */
    @Test
    public void prayerTimes_matchesAdhanReference() {
        DateComponents date = new DateComponents(2015, 7, 12);
        CalculationParameters params = CalculationMethod.NORTH_AMERICA.getParameters();
        params.madhab = Madhab.HANAFI;
        PrayerTimes times = new PrayerTimes(new Coordinates(35.7750, -78.6336), date, params);
        SimpleDateFormat fmt = new SimpleDateFormat("hh:mm a", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        assertEquals("04:42 AM", fmt.format(times.fajr));
        assertEquals("06:08 AM", fmt.format(times.sunrise));
        assertEquals("01:21 PM", fmt.format(times.dhuhr));
        assertEquals("06:22 PM", fmt.format(times.asr));
        assertEquals("08:32 PM", fmt.format(times.maghrib));
    }

    @Test
    public void prayerTimes_ummAlQuraParameters() {
        CalculationParameters p = CalculationMethod.UMM_AL_QURA.getParameters();
        assertEquals(18.5, p.fajrAngle, 0.0001);
        assertEquals(90, p.ishaInterval);
    }

    /** Qibla from Riyadh points WSW; great-circle bearing ≈ 243.8°. */
    @Test
    public void qibla_bearingFromRiyadh() {
        double bearing = new Qibla(new Coordinates(24.7136, 46.6753)).direction;
        assertEquals(243.8, bearing, 1.0);
    }

    /** Qibla from Washington DC ≈ 56.5° (widely published value). */
    @Test
    public void qibla_bearingFromWashingtonDc() {
        double bearing = new Qibla(new Coordinates(38.9072, -77.0369)).direction;
        assertEquals(56.5, bearing, 1.0);
    }

    /** Two nearby points south-west of Makkah must both point towards it. */
    @Test
    public void qibla_nearbyPointsConvergeOnKaaba() {
        double a = new Qibla(new Coordinates(21.0, 39.0)).direction;
        double b = new Qibla(new Coordinates(21.5, 39.5)).direction;
        assertTrue(a > 40 && a < 130);
        assertTrue(b > 40 && b < 130);
    }
}
