package com.ikp.dev.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ikp.dev.app.data.local.entity.UserDataEntities.BookmarkEntity;
import com.ikp.dev.app.data.local.entity.UserDataEntities.DownloadMetadataEntity;
import com.ikp.dev.app.data.local.entity.UserDataEntities.NoteEntity;
import com.ikp.dev.app.data.local.entity.UserDataEntities.ReadingProgressEntity;
import com.ikp.dev.app.data.local.entity.UserDataEntities.RecentHistoryEntity;
import com.ikp.dev.app.data.local.entity.UserDataEntities.UserPreferenceEntity;

import java.util.List;

@Dao
public interface UserDataDao {

    // ---- preferences ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertPreference(UserPreferenceEntity pref);

    @Query("SELECT * FROM user_preferences WHERE `key` = :key LIMIT 1")
    UserPreferenceEntity preference(String key);

    // ---- recent history ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long addRecent(RecentHistoryEntity entry);

    @Query("SELECT * FROM recent_history WHERE contentType = :type ORDER BY viewedAtEpochMs DESC LIMIT :limit")
    List<RecentHistoryEntity> recent(String type, int limit);

    @Query("DELETE FROM recent_history WHERE contentId = :contentId AND contentType = :type")
    void removeRecent(String contentId, String type);

    // ---- reading progress ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertProgress(ReadingProgressEntity progress);

    @Query("SELECT * FROM reading_progress WHERE contentId = :contentId LIMIT 1")
    ReadingProgressEntity progress(String contentId);

    // ---- bookmarks ----
    @Insert
    long addBookmark(BookmarkEntity bookmark);

    @Query("SELECT * FROM bookmarks WHERE contentType = :type ORDER BY createdAtEpochMs DESC")
    List<BookmarkEntity> bookmarks(String type);

    @Delete
    void deleteBookmark(BookmarkEntity bookmark);

    // ---- notes ----
    @Insert
    long addNote(NoteEntity note);

    @Query("SELECT * FROM notes WHERE contentType = :type ORDER BY updatedAtEpochMs DESC")
    List<NoteEntity> notes(String type);

    // ---- download metadata ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDownload(DownloadMetadataEntity download);

    @Query("SELECT * FROM download_metadata ORDER BY downloadedAtEpochMs DESC")
    List<DownloadMetadataEntity> downloads();

    @Query("SELECT * FROM download_metadata WHERE mediaId = :mediaId LIMIT 1")
    DownloadMetadataEntity download(String mediaId);

    @Query("DELETE FROM download_metadata WHERE mediaId = :mediaId")
    void deleteDownload(String mediaId);
}
