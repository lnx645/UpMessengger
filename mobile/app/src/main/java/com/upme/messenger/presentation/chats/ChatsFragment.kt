package com.upme.messenger.presentation.chats
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Daftar chat : avatar bulat + preview + jam + badge, DiffUtil 60fps, empty state card. WA-style FAB loading. */
class ChatsFragment : Fragment() {
  private lateinit var adapter: ChatAdapter
  private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_chats, c, false)

  override fun onViewCreated(v: View, s: Bundle?) {
    adapter = ChatAdapter { item ->
      if (!ClickGuard.canClick(400)) return@ChatAdapter
      (requireActivity() as com.upme.messenger.presentation.MainActivity).openDetail(item.id, item.name)
    }
    val list = v.findViewById<RecyclerView>(R.id.list)
    val empty = v.findViewById<LinearLayout>(R.id.empty)
    list.apply { layoutManager = LinearLayoutManager(context); adapter = this@ChatsFragment.adapter; setHasFixedSize(true); setItemViewCacheSize(16) }

    val fab = v.findViewById<FloatingActionButton>(R.id.fabNewChat)
    val progressFab = v.findViewById<ProgressBar>(R.id.progressFab)
    v.findViewById<MaterialButton>(R.id.emptyAction)?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      parentFragmentManager.beginTransaction().replace(R.id.container, com.upme.messenger.presentation.profil.ProfilFragment()).commit()
      requireActivity().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = "Profil"
      requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_profil
    }
    fab?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      fab.setLoading(true, progressFab)
      fab.postDelayed({
        fab.setLoading(false, progressFab)
        parentFragmentManager.beginTransaction().replace(R.id.container, com.upme.messenger.presentation.profil.ProfilFragment()).commit()
        requireActivity().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = "Profil"
      }, 300)
    }

    val dao = (requireActivity().application as UpMeApp).locator.db.messageDao()
    viewLifecycleOwner.lifecycleScope.launch {
      val raw = dao.latest("general")
      val items = if (raw.isEmpty()) {
        listOf(
          ChatItem("general", "Teman UpMessenger", "Ketuk untuk mulai chat", timeFmt.format(Date()), "READ", 0),
          ChatItem("group1", "Grup Keluarga", "Budi: Sampai jumpa besok!", "09:12", "SENT", 2),
          ChatItem("pin_demo", "PIN Invite", "Bagikan PIN mu ke teman", "Kemarin", "PENDING", 1)
        )
      } else {
        raw.map {
          ChatItem(
            id = it.conversationId,
            name = it.conversationId.take(18),
            preview = it.status + " • " + it.id.take(8),
            time = timeFmt.format(Date(it.createdAt)),
            status = it.status,
            unread = 0
          )
        }
      }
      empty.visibility = if (raw.isEmpty() && items.isEmpty()) View.VISIBLE else View.GONE
      adapter.submitList(items)
      if (raw.isEmpty()) empty.visibility = View.GONE
    }
  }
}
