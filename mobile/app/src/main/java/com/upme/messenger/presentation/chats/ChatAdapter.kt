package com.upme.messenger.presentation.chats
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.upme.messenger.presentation.common.AppAvatarView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.upme.messenger.R

/**
 * Adapter Chats : avatar bulat + nama + preview + jam + tick + badge.
 * Pakai DiffUtil & getItemViewType-ready, hemat RAM (ViewHolder ringan, no notifyDataSetChanged).
 */
data class ChatItem(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val status: String, // PENDING / SENT / READ
    val unread: Int = 0,
    val initial: String = name.take(1).uppercase()
)

class ChatAdapter(private val onClick: ((ChatItem) -> Unit)? = null) : ListAdapter<ChatItem, ChatAdapter.Holder>(DIFF) {
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(a: ChatItem, b: ChatItem) = a.id == b.id
            override fun areContentsTheSame(a: ChatItem, b: ChatItem) = a == b
        }
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: AppAvatarView = v.findViewById(R.id.avatar)
        val name: TextView = v.findViewById(R.id.name)
        val time: TextView = v.findViewById(R.id.time)
        val preview: TextView = v.findViewById(R.id.preview)
        val tick: TextView = v.findViewById(R.id.tick)
        val badge: TextView = v.findViewById(R.id.badge)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): Holder =
        Holder(LayoutInflater.from(p.context).inflate(R.layout.item_chat, p, false))

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val item = getItem(pos)
        h.avatar.bind(item.id, item.name)
        h.name.text = item.name
        h.time.text = item.time
        h.preview.text = item.preview
        // Tick: tampil sesuai status
        when (item.status) {
            "READ" -> { h.tick.visibility = View.VISIBLE; h.tick.text = "✓✓"; h.tick.setTextColor(h.itemView.context.getColor(R.color.blue_tick)) }
            "SENT" -> { h.tick.visibility = View.VISIBLE; h.tick.text = "✓✓"; h.tick.setTextColor(h.itemView.context.getColor(R.color.text_time)) }
            "PENDING" -> { h.tick.visibility = View.VISIBLE; h.tick.text = "✓"; h.tick.setTextColor(h.itemView.context.getColor(R.color.text_time)) }
            else -> h.tick.visibility = View.GONE
        }
        if (item.unread > 0) {
            h.badge.visibility = View.VISIBLE
            h.badge.text = if (item.unread > 99) "99+" else item.unread.toString()
        } else h.badge.visibility = View.GONE

        h.itemView.setOnClickListener { onClick?.invoke(item) }
    }
}
