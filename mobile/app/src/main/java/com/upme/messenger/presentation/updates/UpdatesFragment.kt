package com.upme.messenger.presentation.updates
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.presentation.chats.ChatAdapter
import com.upme.messenger.presentation.chats.ChatItem
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading
import kotlinx.coroutines.launch

/** Timeline Updates: real dari teman + pesan terbaru, bukan dummy statis. WA-style loading. */
class UpdatesFragment : Fragment() {
  private lateinit var myAdapter: ChatAdapter
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_updates, c, false)
  override fun onViewCreated(v: View, s: Bundle?) {
    val list = v.findViewById<RecyclerView>(R.id.list)
    val empty = v.findViewById<LinearLayout>(R.id.empty)
    myAdapter = ChatAdapter()
    list.apply { layoutManager = LinearLayoutManager(context); adapter = myAdapter; setHasFixedSize(true) }

    fun refresh() {
      viewLifecycleOwner.lifecycleScope.launch {
        val db = (requireActivity().application as UpMeApp).locator.db
        val teman = db.temanDao().all()
        val msgs = db.messageDao().latestAll().take(5)
        val items = mutableListOf<ChatItem>()
        teman.forEach { t ->
          items.add(ChatItem("u_${t.pin}", t.name, "Update terbaru • tap untuk lihat", "Hari ini", "READ", 0, t.name.take(1).uppercase()))
        }
        msgs.forEach { m ->
          val name = m.conversationId.take(12)
          items.add(ChatItem(m.id, name, "Pesan terbaru", android.text.format.DateFormat.format("HH:mm", m.createdAt).toString(), m.status, 0, name.take(1).uppercase()))
        }
        if (items.isEmpty()) {
          empty.visibility = View.VISIBLE; list.visibility = View.GONE
        } else {
          empty.visibility = View.GONE; list.visibility = View.VISIBLE
          myAdapter.submitList(items.take(20))
        }
      }
    }
    refresh()
    val btn = v.findViewById<MaterialButton>(R.id.btnBuatUpdate)
    val progress = v.findViewById<ProgressBar>(R.id.progressBuatUpdate)
    btn?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      btn.setLoading(true, progress)
      btn.postDelayed({
        btn.setLoading(false, progress)
        context?.let { AppToast.show(it, "Pilih foto untuk buat Update") }
      }, 400)
    }
  }
}
