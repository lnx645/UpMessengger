package com.upme.messenger.presentation.detail
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ProgressBar
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.data.auth.AuthPrefs
import com.upme.messenger.data.crypto.CryptoProvider
import com.upme.messenger.data.local.MessageEntity
import com.upme.messenger.domain.usecase.SendMessageUseCase
import com.upme.messenger.presentation.common.ClickGuard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Layar bubble : wallpaper + bubble sent/received + input bar rounded, stackFromEnd 60fps. WA-style FAB loading. */
class ChatDetailFragment : Fragment() {
  private val convId get() = arguments?.getString("conv") ?: "general"
  private lateinit var adapter: MessageAdapter
  private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_detail, c, false)

  override fun onViewCreated(v: View, s: Bundle?) {
    adapter = MessageAdapter()
    val list = v.findViewById<RecyclerView>(R.id.list)
    list.apply {
      layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
      adapter = this@ChatDetailFragment.adapter
      setHasFixedSize(true)
      setItemViewCacheSize(20)
    }
    val app = requireActivity().application as UpMeApp
    val dao = app.locator.db.messageDao()
    val crypto = CryptoProvider()
    val authPrefs = AuthPrefs(requireContext())
    val useCase = SendMessageUseCase(dao, app.locator.tcp, crypto, authPrefs)
    val input = v.findViewById<EditText>(R.id.input)

    if (!authPrefs.isLoggedIn) {
      context?.let { AppToast.show(it, "Sesi habis, silakan login ulang") }
    }

    fun refresh() {
      viewLifecycleOwner.lifecycleScope.launch {
        val raw = dao.latest(convId)
        val items = if (raw.isEmpty()) {
          listOf(
            BubbleItem("1", "Halo! Selamat datang di UpMessenger", "09:00", false),
            BubbleItem("2", "Pesanmu aman dengan E2EE. Coba kirim pesan!", "09:01", false),
            BubbleItem("3", "Hai, ini bubble terkirim ", "09:02", true, "READ"),
            BubbleItem("4", "Ketik di bawah lalu tekan kirim", "09:02", true, "SENT")
          )
        } else {
          raw.reversed().map { e: MessageEntity ->
            val isMe = e.status == "PENDING" || e.status == "SENT" || e.status == "READ"
            val plainText = try { String(crypto.decrypt(e.conversationId, e.cipher)) } catch (_: Exception) { "• pesan terenkripsi •" }
            BubbleItem(
              id = e.id,
              text = plainText,
              time = try { timeFmt.format(Date(e.createdAt)) } catch (_: Exception) { timeFmt.format(Date()) },
              isMe = isMe,
              status = e.status
            )
          }
        }
        adapter.submitList(items) { list.scrollToPosition(items.size - 1) }
      }
    }

    val fab = v.findViewById<FloatingActionButton>(R.id.send)
    val progressSend = v.findViewById<ProgressBar>(R.id.progressSend)
    fab.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val text = input.text.toString()
      if (text.isBlank()) return@setOnClickListener
      if (text.toByteArray().size > 256 * 1024) {
        context?.let { AppToast.show(it, "Pesan terlalu besar (max 256KB)") }
        return@setOnClickListener
      }
      if (!authPrefs.isLoggedIn) {
        context?.let { AppToast.show(it, "Belum login") }
        return@setOnClickListener
      }
      fab.isEnabled = false; fab.alpha = 0.6f; progressSend.visibility = View.VISIBLE
      viewLifecycleOwner.lifecycleScope.launch {
        try {
          useCase.execute(convId, text.toByteArray())
          input.text.clear()
          val current = adapter.currentList.toMutableList()
          current.add(BubbleItem(UUID.randomUUID().toString(), text, timeFmt.format(Date()), true, "PENDING"))
          adapter.submitList(current) { list.scrollToPosition(current.size - 1) }
          refresh()
        } catch (e: Exception) {
          context?.let { AppToast.show(it, "Gagal kirim: ${e.message}") }
        } finally {
          fab.isEnabled = true; fab.alpha = 1f; progressSend.visibility = View.GONE
        }
      }
    }
    refresh()
  }
}
