package com.upme.messenger.presentation.teman
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.presentation.chats.ChatAdapter
import com.upme.messenger.presentation.chats.ChatItem
import kotlinx.coroutines.launch

/** Daftar Teman: real dari DB, invite via PIN masuk sini. */
class TemanFragment : Fragment() {
  private lateinit var myAdapter: ChatAdapter
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_teman, c, false)
  override fun onViewCreated(v: View, s: Bundle?) {
    myAdapter = ChatAdapter { item ->
      (requireActivity() as com.upme.messenger.presentation.MainActivity).openDetail(item.id, item.name)
    }
    val list = v.findViewById<RecyclerView>(R.id.list)
    val empty = v.findViewById<LinearLayout>(R.id.empty)
    list.apply { layoutManager = LinearLayoutManager(context); adapter = myAdapter; setHasFixedSize(true); setItemViewCacheSize(16) }

    fun refresh() {
      viewLifecycleOwner.lifecycleScope.launch {
        val db = (requireActivity().application as UpMeApp).locator.db
        val teman = db.temanDao().all()
        if (teman.isEmpty()) {
          empty.visibility = View.VISIBLE
          myAdapter.submitList(emptyList())
        } else {
          empty.visibility = View.GONE
          val items = teman.map {
            ChatItem(it.pin, it.name, "PIN: ${it.pin.chunked(4).joinToString(" ")}", "Baru", "READ", 0, it.name.take(1).uppercase())
          }
          myAdapter.submitList(items)
        }
      }
    }
    refresh()
    // refresh saat kembali dari Profil
    parentFragmentManager.addOnBackStackChangedListener { refresh() }

    v.findViewById<MaterialButton>(R.id.btnInvite)?.setOnClickListener {
      parentFragmentManager.beginTransaction().replace(R.id.container, com.upme.messenger.presentation.profil.ProfilFragment()).commit()
      requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_profil
      requireActivity().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = "Profil"
    }
    v.findViewById<FloatingActionButton>(R.id.fabAdd)?.setOnClickListener {
      parentFragmentManager.beginTransaction().replace(R.id.container, com.upme.messenger.presentation.profil.ProfilFragment()).commit()
      requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_profil
    }
  }
}
