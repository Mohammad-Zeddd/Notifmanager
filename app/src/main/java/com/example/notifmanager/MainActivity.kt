package com.example.notifmanager

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notifmanager.data.NotificationRepository
import com.example.notifmanager.ui.inbox.NotificationAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var disclosureText: TextView
    private lateinit var emptyText: TextView
    private lateinit var repository: NotificationRepository
    private val inboxAdapter = NotificationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = NotificationRepository.from(this)

        statusText = findViewById(R.id.statusText)
        disclosureText = findViewById(R.id.disclosureText)
        emptyText = findViewById(R.id.emptyText)

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = inboxAdapter
        }

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This deletes every stored notification preview from this phone.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch { repository.clearHistory() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

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
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun updateStatus() {
        val enabled = isListenerEnabled()
        statusText.text = if (enabled) "Notification access: ON" else "Notification access: OFF"
        // The explanation is shown until access is granted.
        disclosureText.visibility = if (enabled) View.GONE else View.VISIBLE
    }
}
