package com.upme.messenger.presentation.profil
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.UpMeApp
import com.upme.messenger.data.local.TemanEntity
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading
import kotlinx.coroutines.launch

class ProfilFragment : Fragment() {
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_profil, c, false)

  override fun onViewCreated(v: View, s: Bundle?) {
    val authPrefs = com.upme.messenger.data.auth.AuthPrefs(requireContext())
    val prefs = requireContext().getSharedPreferences("upme", 0)
    var pin = authPrefs.pin ?: prefs.getString("pin", null)
    var name = authPrefs.name ?: "hendra"
    if (pin == null) {
      pin = (1..8).map { "0123456789ABCDEF".random() }.joinToString("")
      prefs.edit().putString("pin", pin).apply()
      authPrefs.pin = pin
    }
    val pretty = pin.chunked(4).joinToString(" ")
    v.findViewById<TextView>(R.id.myPin).text = pretty
    v.findViewById<TextView>(R.id.profilInitial).text = name.take(2).uppercase().ifEmpty { "HE" }
    v.findViewById<TextView>(R.id.profilName).text = name
    val phone = authPrefs.phone ?: "-"

    fun showComingSoon(title: String) {
      AlertDialog.Builder(requireContext()).setTitle(title).setMessage("Fitur $title segera hadir").setPositiveButton("OK", null).show()
    }

    v.findViewById<View>(R.id.rowQr).setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      AlertDialog.Builder(requireContext()).setTitle("QR Code Saya").setMessage("PIN: $pretty\nPhone: $phone\n\nQR berisi PIN untuk di-scan teman").setPositiveButton("Salin") { _, _ ->
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PIN", pin))
        context?.let { AppToast.show(it, "PIN disalin") }
      }.setNegativeButton("Tutup", null).show()
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
      btnCopy.postDelayed({ btnCopy.setLoading(false, progressCopy) }, 600)
    }
    btnShare.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      btnShare.setLoading(true, progressShare)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "PIN UpMessenger saya: $pretty — invite aku ya!")
      }
      startActivity(Intent.createChooser(intent, "Bagikan PIN"))
      btnShare.postDelayed({ btnShare.setLoading(false, progressShare) }, 800)
    }

    btnInvite.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val t = v.findViewById<EditText>(R.id.pinInput).text.toString().trim().uppercase().replace(" ", "")
      if (t.length != 8 || !t.all { it in "0123456789ABCDEF" }) {
        context?.let { AppToast.show(it, "PIN harus 8 karakter hex (0-9, A-F)") }
        return@setOnClickListener
      }
      val myPin = authPrefs.pin ?: prefs.getString("pin", null)
      if (myPin != null && t.equals(myPin, ignoreCase = true)) {
        context?.let { AppToast.show(it, "Tidak bisa invite PIN sendiri") }
        return@setOnClickListener
      }
      val prettyT = t.chunked(4).joinToString(" ")
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
}
