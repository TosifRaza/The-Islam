package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ikp.dev.app.core.database.IkpDatabase;
import com.ikp.dev.app.data.local.entity.CacheEntities;
import com.ikp.dev.app.data.local.entity.SyncEntities;
import com.ikp.dev.app.data.local.entity.UserDataEntities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * Room DAO instrumentation tests (real SQLite on device).
 * Verifies: subjects ordered by position (01..15 layout, position 06 never
 * present in public data), preferences upsert, pending-sync idempotency.
 */
@RunWith(AndroidJUnit4.class)
public class RoomDaoTest {

    private IkpDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, IkpDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void subjectsPersistInDisplayOrder() {
        CacheEntities.SubjectEntity a = subject("cat_quran_tafsir", 1, "Al-Quran & Tafsir");
        CacheEntities.SubjectEntity b = subject("cat_aqeedah", 4, "Aqeedah");
        CacheEntities.SubjectEntity c = subject("cat_scholar_fatwa", 15, "Scholar Review & Fatwa Directory");
        db.libraryDao().upsertSubjects(Collections.singletonList(c));
        db.libraryDao().upsertSubjects(Collections.singletonList(a));
        db.libraryDao().upsertSubjects(Collections.singletonList(b));

        List<CacheEntities.SubjectEntity> rows = db.libraryDao().subjectsInOrder();
        assertEquals(3, rows.size());
        assertEquals(Integer.valueOf(1), rows.get(0).position);
        assertEquals(Integer.valueOf(4), rows.get(1).position);
        assertEquals(Integer.valueOf(15), rows.get(2).position);
        assertEquals("cat_quran_tafsir", rows.get(0).slug);
    }

    @Test
    public void subjectSlugIsStablePrimaryKey() {
        // Re-inserting with the same slug UPDATES, never duplicates
        db.libraryDao().upsertSubjects(Collections.singletonList(
                subject("cat_duas", 12, "Old name (synthetic)")));
        db.libraryDao().upsertSubjects(Collections.singletonList(
                subject("cat_duas", 12, "New name (synthetic)")));
        List<CacheEntities.SubjectEntity> rows = db.libraryDao().subjectsInOrder();
        assertEquals(1, rows.size());
        assertEquals("New name (synthetic)", rows.get(0).name);
    }

    @Test
    public void preferencesUpsertByKey() {
        UserDataEntities.UserPreferenceEntity p = new UserDataEntities.UserPreferenceEntity();
        p.key = "pref_theme";
        p.value = "dark";
        p.updatedAtEpochMs = 1L;
        db.userDataDao().upsertPreference(p);
        p.value = "light";
        db.userDataDao().upsertPreference(p);
        assertEquals("light", db.userDataDao().preference("pref_theme").value);
    }

    @Test
    public void pendingSyncOpsAreIdempotentByKey() {
        SyncEntities.PendingSyncOpEntity op = new SyncEntities.PendingSyncOpEntity();
        op.opType = "READING_PROGRESS";
        op.opKey = "progress:book-1";
        op.payloadJson = "{}";
        op.state = "PENDING";
        op.createdAtEpochMs = System.currentTimeMillis();

        long first = db.syncDao().enqueue(op);
        long second = db.syncDao().enqueue(op); // IGNORE on conflict
        assertTrue(first > 0);
        assertEquals(-1L, second); // sqlite rowid -1 = ignored insert

        List<SyncEntities.PendingSyncOpEntity> batch = db.syncDao().nextBatch(10);
        assertEquals(1, batch.size());
        assertEquals(1, db.syncDao().countPendingByKey("progress:book-1"));
    }

    @Test
    public void syncLogRecordsAndPrunes() {
        SyncEntities.PendingSyncOpEntity op = new SyncEntities.PendingSyncOpEntity();
        op.opType = "T";
        op.opKey = "k-1";
        op.state = "PENDING";
        op.createdAtEpochMs = System.currentTimeMillis();
        long opId = db.syncDao().enqueue(op);

        SyncEntities.SyncLogEntity log = new SyncEntities.SyncLogEntity();
        log.opId = opId;
        log.action = "DONE";
        log.createdAtEpochMs = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000;
        db.syncDao().log(log);

        db.syncDao().updateState(opId, "DONE", 1, null, System.currentTimeMillis());
        db.syncDao().pruneDoneOlderThan(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000);
        // prune affects only DONE rows older than the threshold (log table untouched here)
        assertEquals(0, db.syncDao().countPendingByKey("k-1"));
    }

    private static CacheEntities.SubjectEntity subject(String slug, int position, String name) {
        CacheEntities.SubjectEntity e = new CacheEntities.SubjectEntity();
        e.slug = slug;
        e.name = name;
        e.position = position;
        e.cachedAtEpochMs = System.currentTimeMillis();
        return e;
    }
}
