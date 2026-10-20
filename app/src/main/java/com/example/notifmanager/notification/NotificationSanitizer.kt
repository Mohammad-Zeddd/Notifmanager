package com.example.notifmanager.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.notifmanager.data.NotificationEntity

/**
 * Decides what is allowed to be stored, and in what form.
 * Rules: skip our own, ongoing and group-summary notifications; keep short previews only;
 * mask digit runs when the text looks like an OTP or password message.
 */
object NotificationSanitizer {

    private const val MAX_TITLE = 100
    private const val MAX_TEXT = 200
    private const val MASK = "••••"

    private val SENSITIVE_HINT = Regex(
        "otp|one[- ]time|verification|passcode|password|\\bcode\\b|\\bpin\\b",
        RegexOption.IGNORE_CASE
    )
    private val DIGIT_RUN = Regex("\\b\\d{4,8}\\b")
    private val WHITESPACE = Regex("\\s+")

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

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        val sensitive = SENSITIVE_HINT.containsMatchIn(title) || SENSITIVE_HINT.containsMatchIn(text)

        return NotificationEntity(
            dedupeKey = "${sbn.key}|${sbn.postTime}",
            sbnKey = sbn.key,
            sourcePackage = sbn.packageName,
            appLabel = appLabel,
            titleSnippet = clean(title, sensitive, MAX_TITLE),
            textSnippet = clean(text, sensitive, MAX_TEXT),
            postedAt = sbn.postTime
        )
    }

    private fun clean(raw: String, sensitive: Boolean, maxLength: Int): String {
        val masked = if (sensitive) DIGIT_RUN.replace(raw, MASK) else raw
        return masked.replace(WHITESPACE, " ").trim().take(maxLength)
    }
}
