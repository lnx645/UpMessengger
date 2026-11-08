package com.upme.messenger.presentation.profil
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import coil.load
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.data.local.TemanEntity
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading
import com.upme.messenger.util.PinValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfilFragment : Fragment() {
  private var qrDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_profil, c, false)

  override fun onViewCreated(v: View, s: Bundle?) {
    val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
    val prefs = requireContext().getSharedPreferences("upme", 0)
    var pin = authPrefs.pin ?: prefs.getString("pin", null)
    var name = authPrefs.name ?: "hendra"
    if (pin == null) {
      pin = PinValidator.generate()
      prefs.edit().putString("pin", pin).apply()
      authPrefs.pin = pin
    }
    val pretty = PinValidator.pretty(pin)
    v.findViewById<TextView>(R.id.myPin).text = pretty
    v.findViewById<TextView>(R.id.profilInitial).text = name.take(2).uppercase().ifEmpty { "HE" }
    v.findViewById<TextView>(R.id.profilName).text = name
    val phone = authPrefs.phone ?: "-"
    // Load avatar if exists
    val avatarFile = File(requireContext().filesDir, "avatar.jpg")
    val imgAvatar = v.findViewById<ImageView>(R.id.imgProfilAvatar)
    val txtInitial = v.findViewById<TextView>(R.id.profilInitial)
    if (avatarFile.exists()) {
      imgAvatar.visibility = View.VISIBLE
      txtInitial.visibility = View.GONE
      imgAvatar.load(avatarFile) { crossfade(true) }
    } else {
      imgAvatar.visibility = View.GONE
      txtInitial.visibility = View.VISIBLE
    }

    fun showComingSoon(title: String) {
      AlertDialog.Builder(requireContext()).setTitle(title).setMessage("Fitur $title segera hadir").setPositiveButton("OK", null).show()
    }

    v.findViewById<View>(R.id.rowQr).setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      showQrDialog(pin, pretty, name, avatarFile)
    }
    v.findViewById<View>(R.id.rowProfilDetail).setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val input = EditText(requireContext()).apply { setText(name); hint = "Nama baru" }
      AlertDialog.Builder(requireContext()).setTitle("Edit Profil").setView(input).setPositiveButton("Simpan") { _, _ ->
        val n = input.text.toString().trim()
        if (n.length >= 2) {
          authPrefs.name = n
          v.findViewById<TextView>(R.id.profilName).text = n
          v.findViewById<TextView>(R.id.profilInitial).text = n.take(2).uppercase()
          context?.let { AppToast.show(it, "Nama diubah ke $n") }
        }
      }.setNegativeButton("Batal", null).show()
    }

    val repo = com.upme.messenger.data.auth.AuthRepository()
    fun showTwoFaSetup() {
      val input = EditText(requireContext()).apply { hint = "PIN 6 digit"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
      val dlg = AlertDialog.Builder(requireContext()).setTitle("Aktifkan 2FA").setMessage("PIN 6 digit untuk kunci registrasi ulang").setView(input)
        .setPositiveButton("Aktifkan", null).setNegativeButton("Batal", null).create()
      dlg.setOnShowListener {
        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.setOnClickListener {
          val p = input.text.toString().trim()
          if (p.length != 6) { context?.let { AppToast.show(it, "PIN harus 6 digit") }; return@setOnClickListener }
          if (!ClickGuard.canClick()) return@setOnClickListener
          btn.isEnabled = false; btn.alpha = 0.5f
          viewLifecycleOwner.lifecycleScope.launch {
            val ph = authPrefs.phone ?: return@launch
            val tok = authPrefs.token ?: return@launch
            val r = repo.setTwoFa(ph, p, tok)
            btn.isEnabled = true; btn.alpha = 1f
            r.onSuccess { context?.let { AppToast.show(it, "2FA aktif ✓") }; dlg.dismiss() }
             .onFailure { e -> context?.let { AppToast.show(it, "Gagal: ${e.message}") } }
          }
        }
      }
      dlg.show()
    }
    fun showTwoFaDisable() {
      val input = EditText(requireContext()).apply { hint = "PIN 2FA saat ini"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
      val dlg = AlertDialog.Builder(requireContext()).setTitle("Matikan 2FA").setView(input)
        .setPositiveButton("Matikan", null).setNegativeButton("Batal", null).create()
      dlg.setOnShowListener {
        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.setOnClickListener {
          val p = input.text.toString().trim()
          if (!ClickGuard.canClick()) return@setOnClickListener
          btn.isEnabled = false; btn.alpha = 0.5f
          viewLifecycleOwner.lifecycleScope.launch {
            val ph = authPrefs.phone ?: return@launch
            val tok = authPrefs.token ?: return@launch
            val r = repo.disableTwoFa(ph, p, tok)
            btn.isEnabled = true; btn.alpha = 1f
            r.onSuccess { context?.let { AppToast.show(it, "2FA dimatikan") }; dlg.dismiss() }
             .onFailure { e -> context?.let { AppToast.show(it, "Gagal: ${e.message}") } }
          }
        }
      }
      dlg.show()
    }

    val settings = listOf("Privasi & Keamanan", "Perangkat tertaut", "Notifikasi", "Tampilan", "Bahasa")
    v.post {
      fun collect(view: View) {
        if (view is ViewGroup) for (i in 0 until view.childCount) {
          val c = view.getChildAt(i)
          if (c is LinearLayout && c.childCount >= 2) {
            val tv = c.getChildAt(1) as? TextView
            if (tv != null && tv.text.toString() in settings + listOf("Manajemen Penyimpanan")) {
              c.setOnClickListener {
                if (!ClickGuard.canClick(400)) return@setOnClickListener
                when (tv.text.toString()) {
                  "Privasi & Keamanan" -> {
                    parentFragmentManager.beginTransaction()
                      .replace(R.id.container, com.upme.messenger.presentation.profil.PrivasiKeamananFragment())
                      .addToBackStack("privasi")
                      .commit()
                  }
                  else -> showComingSoon(tv.text.toString())
                }
              }
            }
          }
          collect(c)
        }
      }
      collect(v)
      v.findViewById<View>(R.id.rowStorage)?.setOnClickListener {
        if (!ClickGuard.canClick()) return@setOnClickListener
        showComingSoon("Manajemen Penyimpanan")
      }
    }
    v.findViewById<View>(R.id.profilName).setOnLongClickListener {
      AlertDialog.Builder(requireContext()).setTitle("Keluar?").setMessage("Keluar dari ${authPrefs.phone}?").setPositiveButton("Keluar") { _, _ ->
        authPrefs.clear(); requireContext().getSharedPreferences("upme", 0).edit().clear().apply()
        context?.let { AppToast.show(it, "Keluar") }
        requireActivity().recreate()
      }.setNegativeButton("Batal", null).show()
      true
    }
    // Long press avatar to change
    v.findViewById<View>(R.id.imgProfilAvatar)?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      parentFragmentManager.beginTransaction().replace(R.id.container, com.upme.messenger.presentation.avatar.UserAvatarCroppingFragment.newInstance(android.net.Uri.fromFile(avatarFile))).addToBackStack("crop").commit()
    }
    v.findViewById<TextView>(R.id.profilInitial)?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      parentFragmentManager.beginTransaction().replace(R.id.container, com.upme.messenger.presentation.avatar.UserAvatarCroppingFragment.newInstance(android.net.Uri.fromFile(avatarFile))).addToBackStack("crop").commit()
    }

    val btnCopy = v.findViewById<MaterialButton>(R.id.btnCopy)
    val progressCopy = v.findViewById<ProgressBar>(R.id.progressCopy)
    val btnShare = v.findViewById<MaterialButton>(R.id.btnShare)
    val progressShare = v.findViewById<ProgressBar>(R.id.progressShare)
    val btnInvite = v.findViewById<MaterialButton>(R.id.invite)
    val progressInvite = v.findViewById<ProgressBar>(R.id.progressInvite)

    btnCopy.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      btnCopy.setLoading(true, progressCopy)
      val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      cm.setPrimaryClip(ClipData.newPlainText("PIN", pin))
      context?.let { AppToast.show(it, "PIN $pretty disalin") }
      viewLifecycleOwner.lifecycleScope.launch {
        kotlinx.coroutines.delay(600)
        if (isAdded) btnCopy.setLoading(false, progressCopy)
      }
    }
    btnShare.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      btnShare.setLoading(true, progressShare)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "PIN UpMessenger saya: $pretty — invite aku ya!")
      }
      startActivity(Intent.createChooser(intent, "Bagikan PIN"))
      viewLifecycleOwner.lifecycleScope.launch {
        kotlinx.coroutines.delay(800)
        if (isAdded) btnShare.setLoading(false, progressShare)
      }
    }

    btnInvite.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val t = PinValidator.normalize(v.findViewById<EditText>(R.id.pinInput).text.toString())
      if (!PinValidator.isValid(t)) {
        context?.let { AppToast.show(it, "PIN harus 8 karakter hex (0-9, A-F)") }
        return@setOnClickListener
      }
      val myPin = authPrefs.pin ?: prefs.getString("pin", null)
      if (myPin != null && t.equals(myPin, ignoreCase = true)) {
        context?.let { AppToast.show(it, "Tidak bisa invite PIN sendiri") }
        return@setOnClickListener
      }
      val prettyT = PinValidator.pretty(t)
      btnInvite.setLoading(true, progressInvite)
      viewLifecycleOwner.lifecycleScope.launch {
        val db = (requireActivity().application as UpMeApp).locator.db
        val existing = db.temanDao().find(t)
        if (existing != null) {
          btnInvite.setLoading(false, progressInvite)
          context?.let { AppToast.show(it, "$prettyT sudah jadi teman") }
        } else {
          db.temanDao().upsert(TemanEntity(t, "Teman $prettyT", System.currentTimeMillis()))
          try {
            val dao = db.messageDao()
            val crypto = com.upme.messenger.data.crypto.CryptoProvider()
            val cipher = crypto.encrypt(t, "Halo! Kita sekarang teman".toByteArray())
            dao.upsert(com.upme.messenger.data.local.MessageEntity(java.util.UUID.randomUUID().toString(), t, cipher, "SENT", System.currentTimeMillis()))
          } catch (_: Exception) {}
          try {
            val token = authPrefs.token
            if (token != null) (requireActivity().application as UpMeApp).locator.tcp.send(t, java.util.UUID.randomUUID().toString(), android.util.Base64.encodeToString("PREKEY_REQ:$pretty".toByteArray(), android.util.Base64.NO_WRAP), token)
          } catch (_: Exception) {}
          btnInvite.setLoading(false, progressInvite)
          context?.let { AppToast.show(it, "Invite terkirim ke $prettyT ✓") }
          v.findViewById<EditText>(R.id.pinInput).text.clear()
        }
      }
    }
  }

  private fun showQrDialog(pin: String, pretty: String, name: String, avatarFile: File) {
    if (!isAdded) return
    val ctx = requireContext()
    val dialogView = layoutInflater.inflate(R.layout.dialog_qr, null)
    val imgQr = dialogView.findViewById<ImageView>(R.id.imgQr)
    val imgAvatar = dialogView.findViewById<ImageView>(R.id.imgQrAvatar)
    val txtPin = dialogView.findViewById<TextView>(R.id.txtQrPin)
    val txtName = dialogView.findViewById<TextView>(R.id.txtQrName)
    txtPin.text = pretty
    txtName.text = name
    val hashed = hashPin(pin)
    val qrText = "upme:$hashed:$pin"
    var bmpCache: Bitmap? = null
    viewLifecycleOwner.lifecycleScope.launch {
      val bmp = withContext(Dispatchers.Default) { generateQrBitmap(qrText, 600, null) }
      bmpCache = bmp
      if (isAdded && bmp != null) imgQr.setImageBitmap(bmp)
    }
    dialogView.findViewById<View>(R.id.txtQrCenterPin).visibility = View.GONE
    if (avatarFile.exists()) {
      imgAvatar.visibility = View.VISIBLE
      imgAvatar.load(avatarFile) { crossfade(true) }
    }
    val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx, R.style.ThemeOverlay_App_BottomSheet)
    qrDialog = dialog
    dialog.setOnDismissListener { imgQr.setImageDrawable(null); qrDialog = null }
    dialog.setContentView(dialogView)
    dialogView.findViewById<View>(R.id.btnQrClose).setOnClickListener { dialog.dismiss() }
    dialogView.findViewById<View>(R.id.btnQrShare).setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val cur = bmpCache ?: return@setOnClickListener
      viewLifecycleOwner.lifecycleScope.launch {
        try {
          val bmpToShare = withContext(Dispatchers.Default) { generateFramedQr(cur, pretty) }
          val cacheFile = File(ctx.cacheDir, "qr_${pin}.png")
          withContext(Dispatchers.IO) { FileOutputStream(cacheFile).use { out -> bmpToShare.compress(Bitmap.CompressFormat.PNG, 100, out) } }
          val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", cacheFile)
          val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "PIN UpMessenger saya: $pretty")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          startActivity(Intent.createChooser(intent, "Bagikan QR"))
        } catch (e: Exception) {
          val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "PIN UpMessenger saya: $pretty — $qrText")
          }
          startActivity(Intent.createChooser(intent, "Bagikan QR"))
        }
      }
    }
    dialogView.findViewById<View>(R.id.btnQrDownload).setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val cur = bmpCache ?: return@setOnClickListener
      viewLifecycleOwner.lifecycleScope.launch {
        try {
          val bmpToSave = withContext(Dispatchers.Default) { generateFramedQr(cur, pretty) }
          val dir = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "UpMessenger")
          dir.mkdirs()
          val file = File(dir, "qr_${pin}.png")
          withContext(Dispatchers.IO) { FileOutputStream(file).use { out -> bmpToSave.compress(Bitmap.CompressFormat.PNG, 100, out) } }
          context?.let { AppToast.show(it, "QR disimpan: ${file.absolutePath}") }
          val mediaIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply { data = android.net.Uri.fromFile(file) }
          ctx.sendBroadcast(mediaIntent)
        } catch (e: Exception) {
          context?.let { AppToast.show(it, "Gagal simpan: ${e.message}") }
        }
      }
    }
    dialog.behavior.isDraggable = true
    dialog.show()
  }

  override fun onDestroyView() {
    qrDialog?.dismiss(); qrDialog = null
    super.onDestroyView()
  }

  private fun generateQrBitmap(text: String, size: Int, pinPretty: String? = null): Bitmap? {
    return try {
      val writer = QRCodeWriter()
      val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
      val pixels = IntArray(size * size)
      for (y in 0 until size) for (x in 0 until size) pixels[y * size + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
      var bmp: Bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
      // Tanam PIN di tengah QR (kaya BBM) — white rounded bg + PIN text
      if (pinPretty != null) {
        bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)!!
        val canvas = android.graphics.Canvas(bmp)
        val rectSize = (size * 0.32).toInt()
        val left = (size - rectSize) / 2f
        val top = (size - rectSize) / 2f
        val paintBg = android.graphics.Paint().apply {
          color = Color.WHITE
          style = android.graphics.Paint.Style.FILL
          isAntiAlias = true
        }
        val rect = android.graphics.RectF(left, top, left + rectSize, top + rectSize)
        canvas.drawRoundRect(rect, 16f, 16f, paintBg)
        // border tipis
        val paintBorder = android.graphics.Paint().apply {
          color = Color.parseColor("#E0E0E0")
          style = android.graphics.Paint.Style.STROKE
          strokeWidth = 3f
          isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 16f, 16f, paintBorder)
        val paintText = android.graphics.Paint().apply {
          color = Color.parseColor("#111B21")
          textSize = size * 0.045f
          typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
          textAlign = android.graphics.Paint.Align.CENTER
          isAntiAlias = true
        }
        val xPos = size / 2f
        val yPos = size / 2f - (paintText.descent() + paintText.ascent()) / 2
        canvas.drawText(pinPretty, xPos, yPos, paintText)
      }
      bmp
    } catch (_: Exception) { null }
  }

  private fun hashPin(pin: String): String {
    return try {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
      digest.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { pin }
  }

  private fun generateFramedQr(qr: Bitmap, pinPretty: String): Bitmap {
    // Frame BBM 100%: padding merata mengelilingi semua isi (QR + #UpMessenger + PIN)
    val pad = 24
    val headerH = 40
    val footerH = 40
    val w = qr.width + pad * 2
    val h = qr.height + headerH + footerH + pad * 2
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(Color.WHITE)
    val paintHeader = android.graphics.Paint().apply {
      color = Color.parseColor("#075E54")
      textSize = 26f
      typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
      textAlign = android.graphics.Paint.Align.CENTER
      isAntiAlias = true
    }
    // #UpMessenger di dalam padding atas
    canvas.drawText("#UpMessenger", w / 2f, pad + headerH - 10f, paintHeader)
    // QR di tengah dengan padding merata
    canvas.drawBitmap(qr, pad.toFloat(), (pad + headerH).toFloat(), null)
    val paintPin = android.graphics.Paint().apply {
      color = Color.parseColor("#111B21")
      textSize = 24f
      typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
      textAlign = android.graphics.Paint.Align.CENTER
      isAntiAlias = true
    }
    val pinLabel = "PIN: $pinPretty"
    canvas.drawText(pinLabel, w / 2f, h - pad - 12f, paintPin)
    val paintBorder = android.graphics.Paint().apply {
      color = Color.parseColor("#E0E0E0")
      style = android.graphics.Paint.Style.STROKE
      strokeWidth = 3f
      isAntiAlias = true
    }
    canvas.drawRoundRect(android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f, 16f, paintBorder)
    return out
  }
}
