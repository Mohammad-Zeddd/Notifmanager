package com.example.notifmanager.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.notifmanager.data.NotificationEntity

/**
 * Decides what is stored, and in what form.
 * Rules: skip our own, ongoing, group-summary and "live" notifications (media, calls,
 * navigation, progress, alarms); keep short previews only.
 * Text is stored as written (no masking). The database is private to this app.
 */
object NotificationSanitizer {

    private const val MAX_TITLE = 100
    private const val MAX_TEXT = 200

    private val WHITESPACE = Regex("\\s+")

    // "Live" notifications belong in the notification bar, not in the shelf or inbox.
    // A media notification is ongoing while playing, but becomes dismissible when paused,
    // which is why it used to slip through the ongoing check.
    private val LIVE_CATEGORIES = setOf(
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_ALARM
    )

    /** Returns a record to store, or null if this notification should not be stored. */
    fun toRecord(
        sbn: StatusBarNotification,
        appLabel: String,
        ownPackage: String
    ): NotificationEntity? {
        if (sbn.packageName == ownPackage) return null
        if (sbn.isOngoing) return null

        val n = sbn.notification
        // Group summary rows duplicate the real notification, so skip them.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

        val category = n.category
        if (category != null && category in LIVE_CATEGORIES) return null

        val extras = n.extras
        // Media-style notifications carry a media session token.
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        return NotificationEntity(
            dedupeKey = "${sbn.key}|${sbn.postTime}",
            sbnKey = sbn.key,
            sourcePackage = sbn.packageName,
            appLabel = appLabel,
            titleSnippet = clean(title, MAX_TITLE),
            textSnippet = clean(text, MAX_TEXT),
            postedAt = sbn.postTime
        )
    }

    private fun clean(raw: String, maxLength: Int): String =
        raw.replace(WHITESPACE, " ").trim().take(maxLength)
}
