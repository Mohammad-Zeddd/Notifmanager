package com.example.notifmanager.shelf

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.notifmanager.MainActivity
import com.example.notifmanager.R
import com.example.notifmanager.data.AppCount
import com.example.notifmanager.data.NotificationDao
import com.example.notifmanager.data.NotificationEntity

/** The shelf's on/off switch, kept in this app's private preferences. */
object ShelfPrefs {
    private const val FILE = "shelf_prefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

/**
 * Builds and posts the one big, pinned "shelf" notification from the database.
 * Step 1: it only displays; it does not cancel any original notification yet.
 */
class ShelfNotifier(
    private val context: Context,
    private val dao: NotificationDao
) {

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked before notify()
    suspend fun refresh() {
        val manager = NotificationManagerCompat.from(context)

        if (!ShelfPrefs.isEnabled(context)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(manager)

        val total = dao.activeCount()
        val latest = dao.latestActive(ROW_IDS.size)
        val summary = summaryLine(total, dao.topApps())

        val collapsed = RemoteViews(context.packageName, R.layout.shelf_collapsed).apply {
            setTextViewText(R.id.shelfSummary, summary)
        }

        val expanded = RemoteViews(context.packageName, R.layout.shelf_expanded).apply {
            setTextViewText(R.id.shelfSummary, summary)
            ROW_IDS.forEachIndexed { index, rowId ->
                val item = latest.getOrNull(index)
                if (item == null) {
                    setViewVisibility(rowId, View.GONE)
                } else {
                    setViewVisibility(rowId, View.VISIBLE)
                    setTextViewText(rowId, rowText(item))
                }
            }
        }

        // What is shown on the lock screen: counts only, never message content.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shelf)
            .setContentTitle("Notification shelf")
            .setContentText(if (total == 0) "Nothing pending" else "$total pending")
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shelf)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun summaryLine(total: Int, topApps: List<AppCount>): String {
        if (total == 0) return "Nothing pending"
        val perApp = topApps.joinToString(" · ") { "${it.appLabel} ${it.count}" }
        return "$total pending · $perApp"
    }

    private fun rowText(item: NotificationEntity): String {
        val content = listOf(item.titleSnippet, item.textSnippet)
            .filter { it.isNotEmpty() }
            .joinToString(" — ")
        return "${item.appLabel}: $content"
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // DEFAULT importance with no sound or vibration. Android keeps a channel's importance after
    // it is first created, so to try a different importance, change CHANNEL_ID (or uninstall).
    private fun ensureChannel(manager: NotificationManagerCompat) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName("Notification shelf")
            .setDescription("The pinned summary of your notifications")
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "shelf_v1"

        // The expanded layout has six rows, so the shelf shows the six newest pending items.
        private val ROW_IDS = intArrayOf(
            R.id.shelfRow1, R.id.shelfRow2, R.id.shelfRow3,
            R.id.shelfRow4, R.id.shelfRow5, R.id.shelfRow6
        )
    }
}
