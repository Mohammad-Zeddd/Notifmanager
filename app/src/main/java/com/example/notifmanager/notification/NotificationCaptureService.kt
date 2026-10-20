package com.example.notifmanager.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Week 1 proof of concept.
 *
 * Goal: prove the listener connects, stays alive, and receives posted/removed events
 * on a real phone. It logs ONLY safe metadata. Never log the title, text, or extras.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onListenerConnected() {
        // Count only. We do not read or log any notification content here.
        val activeCount = try {
            activeNotifications?.size ?: 0
        } catch (e: SecurityException) {
            -1
        }
        Log.d(TAG, "CONNECTED activeCount=$activeCount")
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras

        // Only record whether these fields exist and how long they are, never their content.
        val titleLen = extras.getCharSequence(Notification.EXTRA_TITLE)?.length ?: 0
        val textLen = extras.getCharSequence(Notification.EXTRA_TEXT)?.length ?: 0

        Log.d(
            TAG,
            "POSTED pkg=${sbn.packageName} category=${n.category} " +
                    "ongoing=${sbn.isOngoing} group=${(n.flags and Notification.FLAG_GROUP_SUMMARY) != 0} " +
                    "titleLen=$titleLen textLen=$textLen postTime=${sbn.postTime}"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "REMOVED pkg=${sbn.packageName}")
    }

    companion object {
        private const val TAG = "NotifPoC"
    }
}
