package com.upme.messenger.presentation.pin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading

/** Layar PIN /BBM: card PIN besar + salin/bagikan + invite card. WA-style loading + debounce. */
class PinFragment : Fragment() {
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_pin, c, false)

  override fun onViewCreated(v: View, s: Bundle?) {
    val prefs = requireContext().getSharedPreferences("upme", 0)
    var pin = prefs.getString("pin", null)
    if (pin == null) {
      pin = (1..8).map { "0123456789ABCDEF".random() }.joinToString("")
      prefs.edit().putString("pin", pin).apply()
    }
    val pretty = pin.chunked(4).joinToString(" ")
    v.findViewById<TextView>(R.id.myPin).text = pretty

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
      btnInvite.setLoading(true, progressInvite)
      // Simulasi async invite (WA-style: spinner lalu toast)
      btnInvite.postDelayed({
        btnInvite.setLoading(false, progressInvite)
        context?.let { AppToast.show(it, "Invite terkirim ke ${t.chunked(4).joinToString(" ")}") }
        v.findViewById<EditText>(R.id.pinInput).text.clear()
      }, 700)
    }
  }
}
