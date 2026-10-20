package com.example.notifmanager.ui.inbox

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notifmanager.R
import com.example.notifmanager.data.NotificationEntity

private object NotificationDiff : DiffUtil.ItemCallback<NotificationEntity>() {
    override fun areItemsTheSame(old: NotificationEntity, new: NotificationEntity) = old.id == new.id
    override fun areContentsTheSame(old: NotificationEntity, new: NotificationEntity) = old == new
}

class NotificationAdapter :
    ListAdapter<NotificationEntity, NotificationAdapter.ViewHolder>(NotificationDiff) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val meta: TextView = view.findViewById(R.id.metaText)
        val title: TextView = view.findViewById(R.id.titleText)
        val body: TextView = view.findViewById(R.id.bodyText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val time = DateFormat.format("d MMM, HH:mm", item.postedAt)
        val cleared = if (item.removedAt != null) " · cleared" else ""

        holder.meta.text = "${item.appLabel} · $time$cleared"
        holder.title.text = item.titleSnippet
        holder.body.text = item.textSnippet
        holder.title.visibility = if (item.titleSnippet.isEmpty()) View.GONE else View.VISIBLE
        holder.body.visibility = if (item.textSnippet.isEmpty()) View.GONE else View.VISIBLE
    }
}
