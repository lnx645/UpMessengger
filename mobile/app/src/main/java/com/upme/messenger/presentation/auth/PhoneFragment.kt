package com.upme.messenger.presentation.auth
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ProgressBar
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.data.auth.AuthRepository
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading
import kotlinx.coroutines.launch

class PhoneFragment : Fragment() {
  private val repo by lazy { AuthRepository() }
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_phone, c, false)
  override fun onViewCreated(v: View, s: Bundle?) {
    val input = v.findViewById<EditText>(R.id.inputPhone)
    val btn = v.findViewById<MaterialButton>(R.id.btnKirimOtp)
    val progress = v.findViewById<ProgressBar>(R.id.progressKirimOtp)
    btn.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val raw = input.text.toString().trim()
      if (!PhoneValidator.isValid(raw, "ID")) { context?.let { AppToast.show(it, "Nomor tidak valid (pakai format +62...)") }; return@setOnClickListener }
      val phone = PhoneValidator.formatE164(raw, "ID") ?: raw
      btn.setLoading(true, progress)
      viewLifecycleOwner.lifecycleScope.launch {
        val r = repo.requestOtp(phone)
        btn.setLoading(false, progress)
        r.onSuccess {
          context?.let { AppToast.show(it, "OTP dikirim ke $phone") }
          val f = OtpFragment().apply { arguments = Bundle().apply { putString("phone", phone) } }
          parentFragmentManager.beginTransaction().replace(R.id.container, f).addToBackStack(null).commit()
        }.onFailure { e ->
          val msg = when (e.message) {
            "rate_limited" -> "Terlalu sering, tunggu 10 menit"
            "rate_limited_ip" -> "Terlalu banyak permintaan dari IP ini, coba lagi nanti"
            "invalid_phone" -> "Nomor tidak valid"
            else -> "Gagal: ${e.message} (${e.javaClass.simpleName})"
          }
          context?.let { AppToast.show(it, msg) }
        }
      }
    }
    val btnDev = v.findViewById<View>(R.id.btnDevSkip)
    btnDev?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      context?.let { AppToast.show(it, "Dev login nonaktif di rilis publik") }
    }
  }
}
