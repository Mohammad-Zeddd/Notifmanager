package com.example.notifmanager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notifmanager.data.NotificationDatabase
import com.example.notifmanager.data.NotificationRepository
import com.example.notifmanager.shelf.ShelfNotifier
import com.example.notifmanager.shelf.ShelfPrefs
import com.example.notifmanager.ui.inbox.NotificationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var disclosureText: TextView
    private lateinit var emptyText: TextView
    private lateinit var shelfToggleButton: Button
    private lateinit var repository: NotificationRepository
    private lateinit var shelf: ShelfNotifier
    private val inboxAdapter = NotificationAdapter()

    // True when the user tapped the shelf button, so a refusal should lead to Settings.
    private var openSettingsIfDenied = false

    // Android 13+ asks the user before an app may post its own notifications (the shelf).
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && openSettingsIfDenied) openAppNotificationSettings()
            openSettingsIfDenied = false
            updateShelfButton()
            refreshShelf()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = NotificationRepository.from(this)
        shelf = ShelfNotifier(
            applicationContext,
            NotificationDatabase.get(this).notificationDao()
        )

        statusText = findViewById(R.id.statusText)
        disclosureText = findViewById(R.id.disclosureText)
        emptyText = findViewById(R.id.emptyText)
        shelfToggleButton = findViewById(R.id.shelfToggleButton)

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = inboxAdapter
        }

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        shelfToggleButton.setOnClickListener { onShelfButtonTapped() }

        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This deletes every stored notification preview from this phone.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        repository.clearHistory()
                        refreshShelf()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Retention: drop old, already-removed records whenever the app opens.
        lifecycleScope.launch { repository.purgeOld() }

        // Keeps the list live: Room pushes a new list whenever the table changes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.recent.collect { list ->
                    inboxAdapter.submitList(list)
                    emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        askForPostPermissionOnceAfterAccessGranted()
        updateShelfButton()
        refreshShelf()
    }

    private fun onShelfButtonTapped() {
        val enabled = ShelfPrefs.isEnabled(this)
        if (enabled && !canPostNotifications()) {
            // The shelf is on but blocked, so the tap fixes the permission instead of switching off.
            askForPostPermission(openSettingsIfDenied = true)
        } else {
            ShelfPrefs.setEnabled(this, !enabled)
            if (!enabled && !canPostNotifications()) askForPostPermission(openSettingsIfDenied = true)
            updateShelfButton()
            refreshShelf()
        }
    }

    // The permission prompt appears once, right after notification access is granted, so the
    // user already knows what the shelf is for. After that it only appears when they tap the button.
    private fun askForPostPermissionOnceAfterAccessGranted() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (isListenerEnabled() &&
            ShelfPrefs.isEnabled(this) &&
            !canPostNotifications() &&
            !prefs.getBoolean("asked_post_permission", false)
        ) {
            prefs.edit().putBoolean("asked_post_permission", true).apply()
            askForPostPermission(openSettingsIfDenied = false)
        }
    }

    private fun askForPostPermission(openSettingsIfDenied: Boolean) {
        this.openSettingsIfDenied = openSettingsIfDenied
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (openSettingsIfDenied) {
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun refreshShelf() {
        lifecycleScope.launch(Dispatchers.IO) { shelf.refresh() }
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun updateStatus() {
        val enabled = isListenerEnabled()
        statusText.text = if (enabled) "Notification access: ON" else "Notification access: OFF"
        // The explanation is shown until access is granted.
        disclosureText.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun updateShelfButton() {
        shelfToggleButton.text = when {
            !ShelfPrefs.isEnabled(this) -> "Shelf: OFF (tap to turn on)"
            !canPostNotifications() -> "Shelf: needs notification permission (tap to fix)"
            else -> "Shelf: ON (tap to turn off)"
        }
    }
}
