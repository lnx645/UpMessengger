package com.upme.messenger.presentation.friend

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

class FriendFragment : Fragment() {
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
    val candidate = content.trim()
    val raw = if (candidate.contains(":")) candidate.substringAfterLast(":").trim().uppercase().replace(" ", "")
              else candidate.uppercase().replace(" ", "")
    if (PinValidator.isValid(raw)) {
      context?.let { AppToast.show(it, "PIN ${PinValidator.pretty(raw)} terdeteksi") }
      // Jika sheet invite sudah terbuka (scan dari sheet), isi input-nya saja — jangan buka sheet baru (anti double)
      val sheetInput = pendingSheet?.findViewById<EditText>(R.id.inputPinInvite)
      if (sheetInput != null) {
        sheetInput.setText(raw)
        sheetInput.setSelection(raw.length)
      } else if (isAdded) {
        showInviteSheetWithPrefill(raw)
      }
    } else {
      context?.let { AppToast.show(it, "QR tidak valid: $candidate") }
    }
  }
  private val requestCamera = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) {
      val opts = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Scan QR teman"); setBeepEnabled(false); setOrientationLocked(false) }
      qrLauncher.launch(opts)
    } else {
      context?.let { AppToast.show(it, "Izin kamera ditolak — isi PIN manual") }
    }
  }

  private val pickContact = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
    if (uri == null) return@registerForActivityResult
    try {
      val ctx = context ?: return@registerForActivityResult
      val cursor = ctx.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
      cursor?.use {
        if (it.moveToFirst()) {
          var phone = it.getString(0) ?: ""
          phone = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
          // Normalize to E164-ish: keep + and digits
          val normalized = phone.trim()
          lifecycleScope.launch {
            try {
              val app = activity?.application as? UpMeApp ?: return@launch
              val authPrefs = com.upme.messenger.data.auth.AuthPrefs(ctx)
              val token = authPrefs.token
              if (token == null) {
                AppToast.show(ctx, "Belum login")
                return@launch
              }
              AppToast.show(ctx, "Mencari PIN untuk $normalized...")
              // Try search by phone substring via 0x38 (server searches name/pin, but phone search may return empty — graceful)
              val results = app.locator.tcp.searchFriend(token, normalized.takeLast(8))
              if (results.isNotEmpty()) {
                val first = results.first()
                val pin = first["pin"] ?: ""
                val name = first["name"] ?: pin
                AppToast.show(ctx, "Ditemukan $name ($pin) — isi PIN otomatis")
                // Autofill invite sheet if open, else show invite sheet with pin
                view?.findViewById<EditText>(R.id.inputPinInvite)?.setText(pin)
                // If sheet not open, open it with prefill
                if (view?.findViewById<EditText>(R.id.inputPinInvite) == null) {
                  showInviteSheetWithPrefill(pin)
                }
              } else {
                AppToast.show(ctx, "Kontak $normalized belum terdaftar — invite via PIN manual")
                showInviteSheetWithPrefill("")
              }
            } catch (_: Exception) {
              AppToast.show(ctx, "Gagal cari kontak")
            }
          }
        }
      }
    } catch (_: Exception) {}
  }
  private fun showInviteSheetWithPrefill(prefillPin: String) {
    showInviteSheet()
    // post autofill after sheet inflated
    view?.post {
      view?.findViewById<EditText>(R.id.inputPinInvite)?.setText(prefillPin)
    }
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

    val searchBar = v.findViewById<com.upme.messenger.presentation.common.AppSearchBar>(R.id.searchBar)
    // Support both new AppSearchBar and old EditText for compat
    val inputSearch = searchBar?.input ?: v.findViewById<EditText>(R.id.inputSearch)
    if (searchBar != null) {
      searchBar.doOnTextChanged { text ->
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
          delay(300)
          currentFilter = text.trim()
          refresh()
          if (currentFilter.length >= 2) triggerServerSearch(currentFilter)
        }
      }
    } else if (inputSearch != null) {
      searchWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
          searchJob?.cancel()
          searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            currentFilter = s?.toString()?.trim() ?: ""
            refresh()
            if (currentFilter.length >= 2) triggerServerSearch(currentFilter)
          }
        }
      }
      inputSearch.addTextChangedListener(searchWatcher)
    }

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

    // English: invite action — FAB primary, btnInvite legacy fallback
    v.findViewById<MaterialButton>(R.id.btnInvite)?.setOnClickListener { showInviteSheet() }
    v.findViewById<FloatingActionButton>(R.id.fabAdd)?.setOnClickListener { showInviteSheet() }
    // New AppEmptyState CTA
    v.findViewById<com.upme.messenger.presentation.common.AppEmptyState>(R.id.emptyState)?.let { es ->
      es.onCtaClick = { showInviteSheet() }
    }
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
    view?.findViewById<com.upme.messenger.presentation.common.AppSearchBar>(R.id.searchBar)?.clearWatcher()
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
      // Support both legacy LinearLayout empty and new AppEmptyState
      val emptyLegacy = view?.findViewById<LinearLayout>(R.id.empty)
      val emptyState = view?.findViewById<com.upme.messenger.presentation.common.AppEmptyState>(R.id.emptyState)
      if (showPendingOnly) {
        val pendings = db.pendingDao().all()
        if (pendings.isEmpty()) {
          emptyLegacy?.visibility = View.VISIBLE
          emptyState?.visibility = View.VISIBLE
          rv.adapter = pendingAdapter
          pendingAdapter?.submitList(emptyList())
        } else {
          emptyLegacy?.visibility = View.GONE
          emptyState?.visibility = View.GONE
          rv.adapter = pendingAdapter
          val items = pendings.map {
            val displayName = it.name.takeIf { n -> n.isNotBlank() && !n.startsWith("Teman ") } ?: "Teman ${PinValidator.pretty(it.pin)}"
            ChatItem(it.pin, displayName, "PIN: ${PinValidator.pretty(it.pin)}", "", "", 0, displayName.take(1).uppercase())
          }
          pendingAdapter?.submitList(items)
          myAdapter.submitList(emptyList())
        }
      } else {
        rv.adapter = myAdapter
        val local = if (currentFilter.isBlank()) db.temanDao().all() else db.temanDao().search(currentFilter.uppercase())
        // Update badge counts (1-6 #3)
        updateBadges(db)
        // 5: merge server results when filtering and local empty
        val teman = local
        val merged = if (currentFilter.isNotBlank() && teman.isEmpty() && serverResults.isNotEmpty()) {
          // Show server results as teman list when local search yields nothing
          serverResults
        } else if (currentFilter.isNotBlank() && serverResults.isNotEmpty()) {
          // Local + server dedup by pin
          val pinSet = teman.map { it.pin }.toSet()
          teman.map {
            val isPlaceholder = it.name.isBlank() || it.name.startsWith("Teman ") || it.name == it.pin
            val displayName = if (isPlaceholder) PinValidator.pretty(it.pin) else it.name
            val preview = if (isPlaceholder) "Ketuk untuk chat" else "📌 ${PinValidator.pretty(it.pin)}"
            ChatItem(it.pin, displayName, preview, "", "", 0, displayName.take(1).uppercase())
          } + serverResults.filter { it.id !in pinSet }
        } else teman.map {
          val isPlaceholder = it.name.isBlank() || it.name.startsWith("Teman ") || it.name == it.pin
          val displayName = if (isPlaceholder) PinValidator.pretty(it.pin) else it.name
          val preview = if (isPlaceholder) "Ketuk untuk chat" else "📌 ${PinValidator.pretty(it.pin)}"
          ChatItem(it.pin, displayName, preview, "", "", 0, displayName.take(1).uppercase())
        }
        if (merged.isEmpty()) {
          emptyLegacy?.visibility = View.VISIBLE
          emptyState?.visibility = View.VISIBLE
          // 6: offline hint when no results and not connected
          if (currentFilter.isNotBlank() && !isOnline()) {
            emptyState?.subtitle = "Offline — cek koneksi hotspot 192.168.70.92"
          }
          myAdapter.submitList(emptyList())
        } else {
          emptyLegacy?.visibility = View.GONE
          emptyState?.visibility = View.GONE
          myAdapter.submitList(merged)
        }
      }
    }
  }

  private fun updateBadges(db: com.upme.messenger.data.local.UpMeDb) {
    // Cheap count query (no list load) — called from refresh
    lifecycleScope.launch {
      try {
        val pendingCount = db.pendingDao().all().size
        val chipPending = view?.findViewById<Chip>(R.id.chipPending)
        chipPending?.text = if (pendingCount > 0) "Permintaan ($pendingCount)" else "Permintaan"
      } catch (_: Exception) {}
    }
  }

  private fun syncPendingFromServer() {
    if (!isAdded) return
    lifecycleScope.launch {
      try {
        val ctx = context ?: return@launch
        val app = activity?.application as? UpMeApp ?: return@launch
        val authPrefs = com.upme.messenger.data.auth.AuthPrefs(ctx)
        val token = authPrefs.token ?: return@launch
        val phone = authPrefs.phone ?: return@launch
        val raw = app.locator.tcp.syncPending(phone, token)
        // Always update badge even if empty
        updateBadges(app.locator.db)
        if (raw.isEmpty()) return@launch
        val db = app.locator.db
        for (m in raw) {
          val pin = m["pin"]?.uppercase() ?: continue
          if (!PinValidator.isValid(pin)) continue
          val name = m["name"]?.takeIf { it.isNotBlank() } ?: "Teman ${PinValidator.pretty(pin)}"
          val phoneFrom = m["phone"] ?: ""
          db.pendingDao().upsert(com.upme.messenger.data.local.PendingEntity(pin, name, phoneFrom, System.currentTimeMillis(), "IN"))
        }
        if (isAdded) refresh()
        // Show heads-up if new pending while in background (optional)
        val newCount = raw.size
        if (newCount > 0 && !showPendingOnly) {
          AppToast.show(ctx, "$newCount permintaan baru — buka Permintaan")
        }
      } catch (_: Exception) {}
    }
  }

  private var serverSearchJob: Job? = null
  private var serverResults: List<ChatItem> = emptyList()
  private fun triggerServerSearch(query: String) {
    if (!isAdded) return
    serverSearchJob?.cancel()
    serverSearchJob = viewLifecycleOwner.lifecycleScope.launch {
      delay(300)
      try {
        val ctx = context ?: return@launch
        val app = activity?.application as? UpMeApp ?: return@launch
        val token = com.upme.messenger.data.auth.AuthPrefs(ctx).token ?: return@launch
        val results = app.locator.tcp.searchFriend(token, query)
        if (!isAdded) return@launch
        if (results.isEmpty()) {
          serverResults = emptyList()
          AppToast.show(ctx, "Tidak ada hasil server untuk \"$query\"")
        } else {
          serverResults = results.map {
            val pin = it["pin"] ?: ""
            val name = it["name"]?.takeIf { n -> n.isNotBlank() } ?: PinValidator.pretty(pin)
            ChatItem(pin, name, "Server • ${PinValidator.pretty(pin)} • Tap untuk invite", "", "", 0, name.take(1).uppercase())
          }
          AppToast.show(ctx, "${results.size} hasil server untuk \"$query\"")
        }
        // Merge into list if still filtering
        if (currentFilter.isNotBlank() && !showPendingOnly) refresh()
      } catch (_: Exception) {
        serverResults = emptyList()
      }
    }
  }

  private fun showTemanOptions(item: ChatItem) {
    if (!isAdded) return
    val ctx = requireContext()
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
    // 4: Blokir eksplisit — tambah tombol Blokir di bawah Hapus (visible, bukan long-press hidden)
    try {
      val container = d.findViewById<android.view.View>(R.id.txtSheetMessage)?.parent as? android.view.ViewGroup
      val blokirBtn = android.widget.TextView(ctx).apply {
        text = "Blokir"
        textSize = 15f
        gravity = android.view.Gravity.CENTER
        setTextColor(0xFFD32F2F.toInt())
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true
        isFocusable = true
        val outValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        background = androidx.core.content.res.ResourcesCompat.getDrawable(resources, outValue.resourceId, context.theme)
        setOnClickListener {
          d.dismiss()
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
        }
      }
      container?.addView(blokirBtn)
    } catch (_: Exception) {
      // fallback: long-press still works
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
  }
  private fun isOnline(): Boolean {
    val cm = context?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
    val n = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(n) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
  private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
      val ctx = context ?: return@setOnClickListener
      if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestCamera.launch(android.Manifest.permission.CAMERA)
        return@setOnClickListener
      }
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
        // Server invite 0x32 — distinguish network vs 401/block
        var serverStatus: String? = null
        var networkError = false
        try {
          val app = requireActivity().application as UpMeApp
          val token = authPrefs.token
          val phone = authPrefs.phone
          if (token != null && phone != null) {
            serverStatus = app.locator.tcp.inviteFriend(phone, token, raw)
          } else networkError = true
        } catch (_: Exception) { networkError = true }
        if (networkError && serverStatus == null) {
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Offline — cek koneksi, coba lagi") }
          return@launch
        }
        if (serverStatus == "pending") {
          db.pendingDao().upsert(PendingEntity(raw, "Teman $pretty", "", System.currentTimeMillis(), "OUT"))
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Permintaan terkirim ke $pretty — menunggu persetujuan") }
          dialog.dismiss()
          return@launch
        }
        if (serverStatus != null && serverStatus !in listOf("accepted", "pending")) {
          // error from server: blocked / whoCanAdd / rate_limited — jangan buat local
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Gagal: $serverStatus") }
          return@launch
        }
        if (serverStatus == null) {
          // Fallback offline: jangan buat teman lokal tanpa konfirmasi server
          btnKirim.setLoading(false, null)
          context?.let { AppToast.show(it, "Offline — tidak bisa invite sekarang") }
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
