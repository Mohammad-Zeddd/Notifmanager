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

    suspend fun clearHistory() {
        dao.clearAll()
    }

    companion object {
        fun from(context: Context): NotificationRepository =
            NotificationRepository(NotificationDatabase.get(context).notificationDao())
    }
}
