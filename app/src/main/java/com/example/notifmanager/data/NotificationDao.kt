package com.example.notifmanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    // IGNORE means a record with the same dedupeKey is silently skipped.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: NotificationEntity): Long

    @Query("SELECT * FROM notification_record ORDER BY postedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<NotificationEntity>>

    @Query("UPDATE notification_record SET removedAt = :removedAt WHERE sbnKey = :sbnKey AND removedAt IS NULL")
    suspend fun markRemoved(sbnKey: String, removedAt: Long)

    @Query("DELETE FROM notification_record")
    suspend fun clearAll()
}
