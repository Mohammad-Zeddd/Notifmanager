package com.example.notifmanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row of the shelf summary: an app and how many of its notifications are still pending. */
data class AppCount(val appLabel: String, val count: Int)

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

    // Retention: only records already removed from the bar are deleted. Active ones are
    // still in the notification bar, so deleting them would just bring them back on reconnect.
    @Query("DELETE FROM notification_record WHERE removedAt IS NOT NULL AND postedAt < :cutoff")
    suspend fun deleteRemovedOlderThan(cutoff: Long)

    // ---- Shelf queries ("active" means not yet removed from the notification bar) ----

    @Query("SELECT COUNT(*) FROM notification_record WHERE removedAt IS NULL")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM notification_record WHERE removedAt IS NULL ORDER BY postedAt DESC LIMIT :limit")
    suspend fun latestActive(limit: Int): List<NotificationEntity>

    @Query(
        "SELECT appLabel AS appLabel, COUNT(*) AS count FROM notification_record " +
            "WHERE removedAt IS NULL GROUP BY appLabel ORDER BY count DESC, appLabel ASC LIMIT 3"
    )
    suspend fun topApps(): List<AppCount>

    // Fixes records whose removal we missed (for example while our process was dead).
    // Only records posted at or before the snapshot are touched, so a notification that
    // arrives right after the snapshot is never marked removed by mistake.
    @Query(
        "UPDATE notification_record SET removedAt = :snapshotTime " +
            "WHERE removedAt IS NULL AND postedAt <= :snapshotTime AND sbnKey NOT IN (:activeKeys)"
    )
    suspend fun markMissingRemoved(activeKeys: List<String>, snapshotTime: Long)
}
