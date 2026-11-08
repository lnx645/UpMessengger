package com.upme.messenger.presentation.teman

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.data.local.PendingEntity
import com.upme.messenger.data.local.TemanEntity
import com.upme.messenger.presentation.chats.ChatAdapter
import com.upme.messenger.presentation.chats.ChatItem
import com.upme.messenger.presentation.common.AppSheet
import com.upme.messenger.presentation.common.AppToast
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.ConfirmDialog
import com.upme.messenger.presentation.common.debounceClick
import com.upme.messenger.presentation.common.setLoading
import com.upme.messenger.util.PinValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.repeatOnLifecycle

class TemanFragment : Fragment() {
  private lateinit var myAdapter: ChatAdapter
  private var pendingSheet: BottomSheetDialog? = null
  private var searchJob: Job? = null
  private var currentFilter: String = ""
  private var showPendingOnly: Boolean = false
  private var pendingAdapter: ChatAdapter? = null
  private var autoRefreshJob: Job? = null
  private var searchWatcher: TextWatcher? = null
  private val trackedDialogs = mutableSetOf<BottomSheetDialog>()
  private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
    val content = result.contents ?: return@registerForActivityResult
    // QR contains upme:<hash>:<pin> -> extract pin 8 hex
    val raw = content.substringAfterLast(":").trim().uppercase().replace(" ", "")
    if (PinValidator.isValid(raw)) {
      view?.findViewById<EditText>(R.id.inputPinInvite)?.setText(raw)
      context?.let { AppToast.show(it, "PIN ${PinValidator.pretty(raw)} terdeteksi") }
    } else {
      context?.let { AppToast.show(it, "QR tidak valid") }
    }
  }

  private val pickContact = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
    if (uri == null) return@registerForActivityResult
    try {
      val cursor = requireContext().contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
      cursor?.use {
        if (it.moveToFirst()) {
          var phone = it.getString(0) ?: ""
          phone = phone.replace(" ", "").replace("-", "")
          context?.let { ctx ->
            lifecycleScope.launch {
              try {
                val app = requireActivity().application as UpMeApp
                val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
                val token = authPrefs.token
                if (token == null) {
                  AppToast.show(ctx, "Belum login")
                  return@launch
                }
                // Try server phone->PIN lookup via 0x39, fallback to search
                AppToast.show(ctx, "Mencari PIN untuk $phone...")
                // For now, do local search by phone not available, show hint
                // Future: call 0x39 phone_for_pin via TcpClient raw
                AppToast.show(ctx, "Kontak $phone — PIN lookup via server segera")
              } catch (_: Exception) {
                AppToast.show(ctx, "Gagal cari kontak")
              }
            }
          }
        }
      }
    } catch (_: Exception) {}
  }

  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_teman, c, false)

  override fun onViewCreated(v: View, s: Bundle?) {
    myAdapter = ChatAdapter { item ->
      showTemanOptions(item)
    }
    pendingAdapter = ChatAdapter { item ->
      // pending item click -> show Terima/Tolak
      showRequestSheet(item.id)
    }
    val list = v.findViewById<RecyclerView>(R.id.list)
    val empty = v.findViewById<LinearLayout>(R.id.empty)
    list.apply { layoutManager = LinearLayoutManager(context); adapter = myAdapter; setHasFixedSize(true); setItemViewCacheSize(16) }

    refresh()
    // Auto-refresh aman: polling 5s hanya saat RESUMED, auto-cancel saat pause (no leak via repeatOnLifecycle)
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
        while (isActive) {
          delay(5000)
          if (isAdded) refresh()
        }
      }
    }

    val inputSearch = v.findViewById<EditText>(R.id.inputSearch)
    searchWatcher = object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
          delay(300)
          currentFilter = s?.toString()?.trim() ?: ""
          refresh()
        }
      }
    }
    inputSearch.addTextChangedListener(searchWatcher)

    val chipSemua = v.findViewById<Chip>(R.id.chipSemua)
    val chipPending = v.findViewById<Chip>(R.id.chipPending)
    chipSemua?.isChecked = true
    chipSemua?.setOnClickListener {
      showPendingOnly = false
      chipPending?.isChecked = false
      refresh()
    }
    chipPending?.setOnClickListener {
      showPendingOnly = !showPendingOnly
      chipSemua?.isChecked = !showPendingOnly
      chipPending.isChecked = showPendingOnly
      refresh()
      if (showPendingOnly) syncPendingFromServer()
    }

    v.findViewById<MaterialButton>(R.id.btnInvite)?.setOnClickListener { showInviteSheet() }
    v.findViewById<FloatingActionButton>(R.id.fabAdd)?.setOnClickListener { showInviteSheet() }
  }

  override fun onResume() {
    super.onResume()
    refresh()
  }

  override fun onPause() {
    super.onPause()
    autoRefreshJob?.cancel()
  }

  override fun onDestroyView() {
    view?.findViewById<EditText>(R.id.inputSearch)?.removeTextChangedListener(searchWatcher)
    searchWatcher = null
    view?.findViewById<RecyclerView>(R.id.list)?.adapter = null
    pendingSheet?.dismiss(); pendingSheet = null
    trackedDialogs.forEach { try { it.dismiss() } catch (_: Exception) {} }
    trackedDialogs.clear()
    autoRefreshJob?.cancel()
    searchJob?.cancel()
    super.onDestroyView()
  }

  private fun refresh() {
    if (!isAdded || view == null) return
    lifecycleScope.launch {
      val db = try { (requireActivity().application as UpMeApp).locator.db } catch (_: Exception) { return@launch }
      val rv = view?.findViewById<RecyclerView>(R.id.list) ?: return@launch
      val emptyLayout = view?.findViewById<LinearLayout>(R.id.empty) ?: return@launch
      if (showPendingOnly) {
        val pendings = db.pendingDao().all()
        if (pendings.isEmpty()) {
          emptyLayout.visibility = View.VISIBLE
          rv.adapter = pendingAdapter
          pendingAdapter?.submitList(emptyList())
        } else {
          emptyLayout.visibility = View.GONE
          rv.adapter = pendingAdapter
          val items = pendings.map {
            // Use real name if available, else pin
            val displayName = it.name.takeIf { n -> n.isNotBlank() && !n.startsWith("Teman ") } ?: "Teman ${PinValidator.pretty(it.pin)}"
            ChatItem(it.pin, displayName, "PIN: ${PinValidator.pretty(it.pin)}", "", "", 0, displayName.take(1).uppercase())
          }
          pendingAdapter?.submitList(items)
          myAdapter.submitList(emptyList())
        }
      } else {
        rv.adapter = myAdapter
        val teman = if (currentFilter.isBlank()) db.temanDao().all() else db.temanDao().search(currentFilter.uppercase())
        if (teman.isEmpty()) {
          emptyLayout.visibility = View.VISIBLE
          myAdapter.submitList(emptyList())
        } else {
          emptyLayout.visibility = View.GONE
          val items = teman.map {
            val displayName = it.name.takeIf { n -> n.isNotBlank() && !n.startsWith("Teman ") } ?: it.name
            // For teman, show name and PIN without chat tick/time
            ChatItem(it.pin, displayName, "📌 PIN: ${PinValidator.pretty(it.pin)}", "", "", 0, displayName.take(1).uppercase())
          }
          myAdapter.submitList(items)
        }
      }
    }
  }

  private fun syncPendingFromServer() {
    lifecycleScope.launch {
      try {
        val app = requireActivity().application as UpMeApp
        val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
        val token = authPrefs.token ?: return@launch
        val phone = authPrefs.phone ?: return@launch
        val raw = app.locator.tcp.syncPending(phone, token) // will be added to TcpClient
        // raw is list of maps
      } catch (_: Exception) {}
    }
  }

  private fun showTemanOptions(item: ChatItem) {
    if (!isAdded) return
    val ctx = requireContext()
    // U1: migrasi ke AppSheet.show(Config) — konsisten WA-style
    val d = AppSheet.show(ctx, AppSheet.Config(
      title = item.name,
      message = "PIN ${PinValidator.pretty(item.id)}",
      primaryText = "Chat",
      secondaryText = "Hapus",
      onPrimary = { (requireActivity() as com.upme.messenger.presentation.MainActivity).openDetail(item.id, item.name) },
      onSecondary = {
        ConfirmDialog(ctx, "Hapus teman?", "Hapus ${item.name} (${PinValidator.pretty(item.id)}) dari daftar teman?", "Hapus", "Batal", onPositive = {
          lifecycleScope.launch {
            val db = (requireActivity().application as UpMeApp).locator.db
            db.temanDao().delete(item.id)
            context?.let { AppToast.show(it, "Teman dihapus") }
            try {
              val app = requireActivity().application as UpMeApp
              val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
              val token = authPrefs.token
              val phone = authPrefs.phone
              if (token != null && phone != null) app.locator.tcp.deleteTeman(phone, token, item.id, false)
            } catch (_: Exception) {}
            view?.findViewById<RecyclerView>(R.id.list)?.let { _ ->
              val all = db.temanDao().all()
              myAdapter.submitList(all.map { ChatItem(it.pin, it.name, "PIN: ${PinValidator.pretty(it.pin)}", "Baru", "READ", 0, it.name.take(1).uppercase()) })
              view?.findViewById<LinearLayout>(R.id.empty)?.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
            }
          }
        }).show()
      }
    ))
    trackedDialogs.add(d)
    // Blokir via long-press message (retain behavior via custom view lookup)
    d.findViewById<TextView>(R.id.txtSheetMessage)?.setOnLongClickListener {
      ConfirmDialog(ctx, "Blokir PIN?", "Blokir ${item.id} agar tidak bisa invite lagi?", "Blokir", "Batal", onPositive = {
        lifecycleScope.launch {
          val db = (requireActivity().application as UpMeApp).locator.db
          db.temanDao().delete(item.id)
          try {
            val app = requireActivity().application as UpMeApp
            val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
            if (authPrefs.token != null && authPrefs.phone != null) app.locator.tcp.deleteTeman(authPrefs.phone!!, authPrefs.token!!, item.id, true)
          } catch (_: Exception) {}
          context?.let { AppToast.show(it, "PIN diblokir") }
          view?.findViewById<RecyclerView>(R.id.list)?.let {
            val all = db.temanDao().all()
            myAdapter.submitList(all.map { ChatItem(it.pin, it.name, "PIN: ${PinValidator.pretty(it.pin)}", "Baru", "READ", 0, it.name.take(1).uppercase()) })
          }
        }
      }).show()
      true
    }
  }

  private fun showInviteSheet() {
    if (!isAdded) return
    val ctx = requireContext()
    val dialog = BottomSheetDialog(ctx, R.style.ThemeOverlay_App_BottomSheet)
    val sheet = layoutInflater.inflate(R.layout.layout_sheet_invite_teman, null)
    val input = sheet.findViewById<EditText>(R.id.inputPinInvite)
    val btnKirim = sheet.findViewById<MaterialButton>(R.id.btnKirimInvite)
    val btnBatal = sheet.findViewById<MaterialButton>(R.id.btnBatalInvite)
    val btnQr = sheet.findViewById<MaterialButton>(R.id.btnQrInvite)
    val btnKontak = sheet.findViewById<MaterialButton>(R.id.btnKontakInvite)

    btnQr.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val opts = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Scan QR teman"); setBeepEnabled(false); setOrientationLocked(false) }
      qrLauncher.launch(opts)
    }
    btnKontak.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      pickContact.launch(null)
    }
    btnBatal.setOnClickListener { dialog.dismiss() }

    btnKirim.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val raw = PinValidator.normalize(input.text.toString())
      if (!PinValidator.isValid(raw)) {
        context?.let { AppToast.show(it, "PIN harus 8 karakter hex (0-9, A-F)") }
        return@setOnClickListener
      }
      val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
      val myPin = authPrefs.pin ?: requireContext().getSharedPreferences("upme", 0).getString("pin", null)
      if (myPin != null && raw.equals(myPin, ignoreCase = true)) {
        context?.let { AppToast.show(it, "Tidak bisa invite PIN sendiri") }
        return@setOnClickListener
      }
      val pretty = PinValidator.pretty(raw)
      btnKirim.setLoading(true, null)
      lifecycleScope.launch {
        val db = (requireActivity().application as UpMeApp).locator.db
        val existing = db.temanDao().find(raw)
        if (existing != null) {
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "$pretty sudah jadi teman") }
          return@launch
        }
        val pendingExisting = db.pendingDao().find(raw)
        if (pendingExisting != null) {
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Invite ke $pretty sudah pending") }
          return@launch
        }
        // Server invite 0x32
        var serverStatus: String? = null
        try {
          val app = requireActivity().application as UpMeApp
          val token = authPrefs.token
          val phone = authPrefs.phone
          if (token != null && phone != null) {
            serverStatus = app.locator.tcp.inviteTeman(phone, token, raw)
          }
        } catch (_: Exception) {}
        if (serverStatus == "pending") {
          db.pendingDao().upsert(PendingEntity(raw, "Teman $pretty", "", System.currentTimeMillis(), "OUT"))
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Permintaan terkirim ke $pretty — menunggu persetujuan") }
          dialog.dismiss()
          return@launch
        }
        if (serverStatus != null && serverStatus !in listOf("accepted", "pending")) {
          // error from server
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Gagal: $serverStatus") }
          return@launch
        }
        db.temanDao().upsert(TemanEntity(raw, "Teman $pretty", System.currentTimeMillis()))
        try {
          val dao = db.messageDao()
          val crypto = com.upme.messenger.data.crypto.CryptoProvider()
          val cipher = crypto.encrypt(raw, "Halo! Kita sekarang teman".toByteArray())
          dao.upsert(com.upme.messenger.data.local.MessageEntity(java.util.UUID.randomUUID().toString(), raw, cipher, "SENT", System.currentTimeMillis()))
        } catch (_: Exception) {}
        try {
          val app = requireActivity().application as UpMeApp
          val token = authPrefs.token
          if (token != null) app.locator.tcp.send(raw, java.util.UUID.randomUUID().toString(), android.util.Base64.encodeToString("PREKEY_REQ:$pretty".toByteArray(), android.util.Base64.NO_WRAP), token)
        } catch (_: Exception) {}
        btnKirim.setLoading(false, null)
        context?.let { AppToast.show(it, "Invite terkirim ke $pretty ✓") }
        dialog.dismiss()
        view?.findViewById<RecyclerView>(R.id.list)?.let { _ ->
          lifecycleScope.launch {
            val all = db.temanDao().all()
            myAdapter.submitList(all.map { ChatItem(it.pin, it.name, "PIN: ${it.pin.chunked(4).joinToString(" ")}", "Baru", "READ", 0, it.name.take(1).uppercase()) })
            view?.findViewById<LinearLayout>(R.id.empty)?.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
          }
        }
        if (raw == "AAAAAAAA") {
          showRequestSheet(raw)
        }
      }
    }

    dialog.setContentView(sheet)
    dialog.behavior.isDraggable = true
    dialog.show()
    pendingSheet = dialog
  }

  private fun showRequestSheet(pin: String) {
    if (!isAdded) return
    val ctx = requireContext()
    lateinit var d: BottomSheetDialog
    d = AppSheet.show(ctx, AppSheet.Config(
      title = "Permintaan pertemanan",
      message = "PIN ${PinValidator.pretty(pin)} ingin berteman. Terima?",
      primaryText = "Terima",
      secondaryText = "Tolak",
      onPrimary = {
        val btn = d.findViewById<MaterialButton>(R.id.btnSheetPrimary)
        btn?.setLoading(true, null)
        lifecycleScope.launch {
          try {
            val app = requireActivity().application as UpMeApp
            val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
            val token = authPrefs.token
            val phone = authPrefs.phone
            if (token != null && phone != null) app.locator.tcp.acceptInvite(phone, token, pin)
            val db = app.locator.db
            db.pendingDao().delete(pin)
            db.temanDao().upsert(TemanEntity(pin, "Teman ${PinValidator.pretty(pin)}", System.currentTimeMillis()))
          } catch (_: Exception) {}
          btn?.setLoading(false, null)
          d.dismiss()
          context?.let { AppToast.show(it, "Teman diterima") }
          view?.findViewById<RecyclerView>(R.id.list)?.let { _ ->
            lifecycleScope.launch {
              val db = (requireActivity().application as UpMeApp).locator.db
              val all = db.pendingDao().all()
              if (all.isEmpty()) {
                val teman = db.temanDao().all()
                myAdapter.submitList(teman.map { ChatItem(it.pin, it.name, "PIN: ${PinValidator.pretty(it.pin)}", "Baru", "READ", 0, it.name.take(1).uppercase()) })
              }
            }
          }
        }
      },
      onSecondary = {
        lifecycleScope.launch {
          try {
            val app = requireActivity().application as UpMeApp
            val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
            if (authPrefs.token != null && authPrefs.phone != null) app.locator.tcp.rejectInvite(authPrefs.phone!!, authPrefs.token!!, pin)
            app.locator.db.pendingDao().delete(pin)
          } catch (_: Exception) {}
          context?.let { AppToast.show(it, "Ditolak") }
        }
      }
    ))
    trackedDialogs.add(d)
  }
}
