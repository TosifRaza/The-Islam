package com.ikp.dev.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ikp.dev.app.data.local.entity.CacheEntities.BookEntity;
import com.ikp.dev.app.data.local.entity.CacheEntities.SubjectEntity;

import java.util.List;

@Dao
public interface LibraryDao {

    // ---- subjects cache ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSubjects(List<SubjectEntity> subjects);

    @Query("SELECT * FROM subjects ORDER BY position ASC")
    List<SubjectEntity> subjectsInOrder();

    @Query("SELECT COUNT(*) FROM subjects WHERE cachedAtEpochMs > :freshAfterEpochMs")
    int countFreshSubjects(long freshAfterEpochMs);

    @Query("DELETE FROM subjects")
    void clearSubjects();

    // ---- books cache ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertBooks(List<BookEntity> books);

    @Query("SELECT * FROM books WHERE queryKey = :queryKey ORDER BY rowid ASC")
    List<BookEntity> booksForQuery(String queryKey);

    @Query("SELECT * FROM books WHERE bookId = :bookId LIMIT 1")
    BookEntity bookById(String bookId);

    @Query("DELETE FROM books WHERE queryKey = :queryKey")
    void clearQuery(String queryKey);

    @Query("DELETE FROM books")
    void clearBooks();
}
