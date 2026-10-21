package com.example.notifmanager.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationDao) {

    val recent: Flow<List<NotificationEntity>> = dao.observeRecent()

    suspend fun save(record: NotificationEntity) {
        dao.insert(record)
    }

    suspend fun markRemoved(sbnKey: String, removedAt: Long) {
        dao.markRemoved(sbnKey, removedAt)
    }

    suspend fun markMissingRemoved(activeKeys: List<String>, snapshotTime: Long) {
        dao.markMissingRemoved(activeKeys, snapshotTime)
    }

    /** Deletes removed records older than the retention period. */
    suspend fun purgeOld() {
        dao.deleteRemovedOlderThan(System.currentTimeMillis() - RETENTION_MILLIS)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    companion object {
        // How long a removed notification's preview is kept. Change this one number to adjust.
        const val RETENTION_DAYS = 14
        private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

        fun from(context: Context): NotificationRepository =
            NotificationRepository(NotificationDatabase.get(context).notificationDao())
    }
}
