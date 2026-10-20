package com.example.notifmanager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_record",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["postedAt"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // sbn.key + "|" + postTime. The unique index stops duplicates when the listener reconnects.
    val dedupeKey: String,

    // Android's own key for the notification. Used to record when it was removed.
    val sbnKey: String,

    val sourcePackage: String,
    val appLabel: String,

    // Bounded, redacted previews only. Never the full extras bundle.
    val titleSnippet: String,
    val textSnippet: String,

    val postedAt: Long,
    val removedAt: Long? = null,

    // Placeholders until the classifier arrives in Week 3.
    val category: String = "UNSORTED",
    val priority: String = "NORMAL",

    val isRead: Boolean = false,
    val userOverride: Boolean = false
)
