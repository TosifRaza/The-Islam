package com.ikp.dev.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ikp.dev.app.data.local.entity.CacheEntities.UserProfileEntity;

@Dao
public interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserProfileEntity profile);

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    UserProfileEntity byId(String userId);

    @Query("SELECT * FROM user_profile LIMIT 1")
    UserProfileEntity first();

    @Query("DELETE FROM user_profile")
    void clear();
}
