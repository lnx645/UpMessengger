package com.upme.messenger.presentation.detail
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.upme.messenger.R

/**
 * Bubble : sent = hijau muda kanan, received = putih kiri.
 * getItemViewType untuk performa, DiffUtil tanpa notifyDataSetChanged, 60fps.
 */
data class BubbleItem(
    val id: String,
    val text: String,
    val time: String,
    val isMe: Boolean,
    val status: String = "SENT" // untuk tick biru/abu
)

class MessageAdapter : ListAdapter<BubbleItem, RecyclerView.ViewHolder>(DIFF) {
    companion object {
        const val TYPE_SENT = 1
        const val TYPE_RECEIVED = 0
        val DIFF = object : DiffUtil.ItemCallback<BubbleItem>() {
            override fun areItemsTheSame(a: BubbleItem, b: BubbleItem) = a.id == b.id
            override fun areContentsTheSame(a: BubbleItem, b: BubbleItem) = a == b
        }
    }

    class SentHolder(v: View) : RecyclerView.ViewHolder(v) {
        val msg: TextView = v.findViewById(R.id.msgText)
        val time: TextView = v.findViewById(R.id.msgTime)
        val tick: TextView = v.findViewById(R.id.msgTick)
    }
    class ReceivedHolder(v: View) : RecyclerView.ViewHolder(v) {
        val msg: TextView = v.findViewById(R.id.msgText)
        val time: TextView = v.findViewById(R.id.msgTime)
    }

    override fun getItemViewType(position: Int) = if (getItem(position).isMe) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) SentHolder(inf.inflate(R.layout.item_bubble_sent, parent, false))
        else ReceivedHolder(inf.inflate(R.layout.item_bubble_received, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is SentHolder) {
            holder.msg.text = item.text
            holder.time.text = item.time
            holder.tick.text = when (item.status) {
                "READ" -> " ✓✓"
                "SENT" -> " ✓✓"
                "PENDING" -> " ✓"
                else -> ""
            }
            holder.tick.setTextColor(
                if (item.status == "READ") holder.itemView.context.getColor(R.color.blue_tick)
                else holder.itemView.context.getColor(R.color.text_time)
            )
        } else if (holder is ReceivedHolder) {
            holder.msg.text = item.text
            holder.time.text = item.time
        }
    }
}
