package com.example.notifmanager.notification

import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.notifmanager.data.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Week 2: stores sanitized records in Room. Logcat still shows metadata only, never content.
 */
class NotificationCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: NotificationRepository

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository.from(applicationContext)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        val active: Array<StatusBarNotification>? = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        }
        Log.d(TAG, "CONNECTED activeCount=${active?.size ?: -1}")
        logPreviousProcessExits()

        // Android does not replay notifications posted while our process was dead.
        // Re-reading the active ones fills the gap; the unique dedupeKey skips what we already have.
        active?.forEach { handlePosted(it) }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val stored = handlePosted(sbn)
        Log.d(TAG, "POSTED pkg=${sbn.packageName} eligible=$stored")
    }

    // The 3-argument version tells us WHY it was removed (swipe, app cancel, timeout, ...).
    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        Log.d(TAG, "REMOVED pkg=${sbn.packageName} reason=$reason")
        scope.launch { repository.markRemoved(sbn.key, System.currentTimeMillis()) }
    }

    /** Returns true if the notification was eligible for storage. */
    private fun handlePosted(sbn: StatusBarNotification): Boolean {
        val record = NotificationSanitizer.toRecord(sbn, appLabelFor(sbn.packageName), packageName)
            ?: return false
        scope.launch { repository.save(record) }
        return true
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
