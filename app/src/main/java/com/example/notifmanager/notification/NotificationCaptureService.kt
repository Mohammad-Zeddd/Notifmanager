package com.example.notifmanager.notification

import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.notifmanager.data.NotificationDatabase
import com.example.notifmanager.data.NotificationRepository
import com.example.notifmanager.shelf.ShelfNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stores sanitized records in Room and keeps the shelf notification up to date.
 * Logcat shows metadata only, never content.
 */
class NotificationCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: NotificationRepository
    private lateinit var shelf: ShelfNotifier
    private var shelfJob: Job? = null

    // Every database write goes through this one queue and runs strictly in the order the
    // events arrived. Without it, a quick "posted, then removed" pair could finish in the
    // wrong order and leave an already-cleared notification stored as pending.
    private val writeQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository.from(applicationContext)
        shelf = ShelfNotifier(
            applicationContext,
            NotificationDatabase.get(applicationContext).notificationDao()
        )
        scope.launch {
            for (work in writeQueue) {
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "WRITE_FAILED", e)
                }
            }
        }
    }

    override fun onDestroy() {
        writeQueue.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        val snapshotTime = System.currentTimeMillis()
        val active: Array<StatusBarNotification>? = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        }
        Log.d(TAG, "CONNECTED activeCount=${active?.size ?: -1}")
        logPreviousProcessExits()

        if (active != null) {
            // Android does not replay notifications posted while our process was dead,
            // so re-read the active ones (the unique dedupeKey skips what we already have)...
            active.forEach { handlePosted(it) }
            // ...and mark anything we stored that is no longer in the notification bar.
            val activeKeys = active.map { it.key }
            enqueue { repository.markMissingRemoved(activeKeys, snapshotTime) }
        }
        // Retention: delete old records that have already been removed from the bar.
        enqueue { repository.purgeOld() }
        scheduleShelfRefresh()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val eligible = handlePosted(sbn)
        Log.d(TAG, "POSTED pkg=${sbn.packageName} eligible=$eligible")
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        if (sbn.packageName == packageName) {
            if (sbn.id == ShelfNotifier.NOTIFICATION_ID) {
                // Shows how this phone treats the shelf (2 = swiped away, 3 = clear all, ...).
                Log.d(TAG, "SHELF_REMOVED reason=$reason")
                // Posts it again, unless the user switched the shelf off.
                scheduleShelfRefresh()
            }
            return
        }

        Log.d(TAG, "REMOVED pkg=${sbn.packageName} reason=$reason")
        val key = sbn.key
        val removedAt = System.currentTimeMillis()
        enqueue {
            repository.markRemoved(key, removedAt)
            scheduleShelfRefresh()
        }
    }

    /** Returns true if the notification was eligible for storage. */
    private fun handlePosted(sbn: StatusBarNotification): Boolean {
        val record = NotificationSanitizer.toRecord(sbn, appLabelFor(sbn.packageName), packageName)
            ?: return false
        enqueue {
            repository.save(record)
            scheduleShelfRefresh()
        }
        return true
    }

    private fun enqueue(work: suspend () -> Unit) {
        writeQueue.trySend(work)
    }

    // Waits briefly so a burst of events causes one shelf update instead of many.
    private fun scheduleShelfRefresh() {
        shelfJob?.cancel()
        shelfJob = scope.launch {
            delay(400)
            shelf.refresh()
        }
    }

    @Suppress("DEPRECATION")
    private fun appLabelFor(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    // Diagnostic: shows why earlier processes of this app ended (API 30+).
    private fun logPreviousProcessExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(packageName, 0, 3).forEach {
            Log.d(TAG, "PREV_EXIT reason=${it.reason} at=${it.timestamp} desc=${it.description}")
        }
    }

    companion object {
        private const val TAG = "NotifPoC"
    }
}
