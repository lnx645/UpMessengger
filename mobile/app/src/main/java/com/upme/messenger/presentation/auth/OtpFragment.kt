package com.upme.messenger.presentation.auth
import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.data.auth.AuthRepository
import com.upme.messenger.data.auth.AuthPrefs
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.AppSheet
import com.upme.messenger.presentation.common.ConfirmDialog
import com.upme.messenger.presentation.common.setLoading
import kotlinx.coroutines.launch

class OtpFragment : Fragment() {
  private val repo by lazy { AuthRepository() }
  private var resendTimer: CountDownTimer? = null
  private var expiryTimer: CountDownTimer? = null
  private var remainingResendMs: Long = 60_000L
  private var remainingExpiryMs: Long = 300_000L
  private var isResendCooling = true
  private lateinit var otpBoxes: List<EditText>
  private var smsReceiver: android.content.BroadcastReceiver? = null
  private var loadingDialog: AlertDialog? = null
  // WA-style long-press delete berantai
  private val deleteHandler = Handler(Looper.getMainLooper())
  private var deleteRunnable: Runnable? = null
  private var isChainDeleting = false

  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_otp, c, false)
  override fun onViewCreated(v: View, s: Bundle?) {
    val phone = arguments?.getString("phone") ?: ""
    v.findViewById<TextView>(R.id.txtPhone).text = "Kode dikirim ke $phone"
    v.findViewById<TextView>(R.id.txtOtpHint).visibility = View.GONE
    val btnVerif = v.findViewById<MaterialButton>(R.id.btnVerif)
    val progressVerif = v.findViewById<ProgressBar>(R.id.progressVerif)
    val btnResend = v.findViewById<TextView>(R.id.btnResend)
    val progressResend = v.findViewById<ProgressBar>(R.id.progressResend)
    val txtCountdown = v.findViewById<TextView>(R.id.txtCountdown)
    val txtExpiry = v.findViewById<TextView>(R.id.txtExpiry)
    val btnGantiNomor = v.findViewById<TextView>(R.id.btnGantiNomor)

    // 6 kotak WA-style
    otpBoxes = listOf(
      v.findViewById(R.id.otp1), v.findViewById(R.id.otp2), v.findViewById(R.id.otp3),
      v.findViewById(R.id.otp4), v.findViewById(R.id.otp5), v.findViewById(R.id.otp6)
    )
    setupOtpBoxes()

    if (s != null) {
      remainingResendMs = s.getLong("resendMs", 60_000L)
      remainingExpiryMs = s.getLong("expiryMs", 300_000L)
      isResendCooling = s.getBoolean("isCooling", true)
      val savedOtp = s.getString("otp6", "")
      savedOtp.forEachIndexed { idx, c -> if (idx < 6) otpBoxes[idx].setText(c.toString()) }
    }

    fun formatExpiry(ms: Long): String {
      val totalSec = (ms / 1000).coerceAtLeast(0)
      return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
    }
    fun startResendCountdown(ms: Long) {
      resendTimer?.cancel()
      if (ms <= 0) {
        isResendCooling = false
        btnResend.isEnabled = true; btnResend.alpha = 1f
        btnResend.text = "Kirim ulang OTP"
        txtCountdown.visibility = View.GONE
        return
      }
      isResendCooling = true
      btnResend.isEnabled = false; btnResend.alpha = 0.5f
      txtCountdown.visibility = View.VISIBLE
      resendTimer = object : CountDownTimer(ms, 1000) {
        override fun onTick(m: Long) {
          remainingResendMs = m
          val sec = (m / 1000).toInt()
          txtCountdown.text = "Kirim ulang dalam ${sec}s"
          btnResend.text = "Kirim ulang dalam ${sec}s"
        }
        override fun onFinish() {
          remainingResendMs = 0; isResendCooling = false
          btnResend.isEnabled = true; btnResend.alpha = 1f
          btnResend.text = "Kirim ulang OTP"
          txtCountdown.visibility = View.GONE
        }
      }.start()
    }
    fun startExpiryCountdown(ms: Long) {
      expiryTimer?.cancel()
      if (ms <= 0) {
        txtExpiry.text = "OTP kedaluwarsa, minta kode baru"
        txtExpiry.setTextColor(0xFFD32F2F.toInt())
        return
      }
      expiryTimer = object : CountDownTimer(ms, 1000) {
        override fun onTick(m: Long) {
          remainingExpiryMs = m
          txtExpiry.text = "OTP berlaku ${formatExpiry(m)}"
          txtExpiry.setTextColor(0xFFE65100.toInt())
        }
        override fun onFinish() {
          remainingExpiryMs = 0
          txtExpiry.text = "OTP kedaluwarsa, minta kode baru"
          txtExpiry.setTextColor(0xFFD32F2F.toInt())
        }
      }.start()
    }
    startResendCountdown(remainingResendMs)
    startExpiryCountdown(remainingExpiryMs)

    // Minta izin SMS untuk auto-fill (WA-style)
    requestSmsPermissionIfNeeded()
    registerSmsReceiver()

    btnVerif.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val otp = collectOtp()
      if (otp.length != 6) { context?.let { AppToast.show(it, "Lengkapi 6 digit OTP") }; return@setOnClickListener }
      if (remainingExpiryMs <= 0) { context?.let { AppToast.show(it, "OTP kedaluwarsa, silakan kirim ulang") }; return@setOnClickListener }
      btnVerif.setLoading(true, progressVerif)
      showLoadingDialog("Memverifikasi...")
      viewLifecycleOwner.lifecycleScope.launch {
        when (val r = repo.verifyOtp(phone, otp)) {
          is AuthRepository.VerifyResult.Success -> {
            hideLoadingDialog()
            btnVerif.setLoading(false, progressVerif)
            val ok = r.ok
            val prefs = AuthPrefs(requireContext())
            prefs.phone = ok.phone; prefs.pin = ok.pin; prefs.token = ok.token; prefs.name = ok.name
            try {
              // Publish real X25519 identity (fix HIGH pepper: publish proper keypair)
              val kp = com.upme.messenger.data.crypto.CryptoProvider.ensureIdentity(requireContext())
              val pubB64 = android.util.Base64.encodeToString(kp.first, android.util.Base64.NO_WRAP)
              repo.publishIdentity(ok.phone, ok.token, pubB64)
            } catch (_: Exception) {}
            resendTimer?.cancel(); expiryTimer?.cancel()
            val f = SetupProfilFragment().apply { arguments = Bundle().apply { putString("pin", ok.pin); putString("name", ok.name) } }
            parentFragmentManager.beginTransaction().replace(R.id.container, f).commit()
          }
          is AuthRepository.VerifyResult.NeedTwoFa -> {
            hideLoadingDialog()
            btnVerif.setLoading(false, progressVerif)
            showTwoFaDialog(phone)
          }
          is AuthRepository.VerifyResult.Error -> {
            hideLoadingDialog()
            btnVerif.setLoading(false, progressVerif)
            val raw = r.msg
            val isLocked = raw.contains("too_many_attempts")
            // coba parse retry_after_ms dari "retry_after_ms:3600000" atau "86400000"
            val retryMs = Regex("retry_after[^0-9]*([0-9]+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()
            val msg = when {
              isLocked && retryMs != null -> {
                val hours = (retryMs / 3600000).toInt()
                val mins = ((retryMs % 3600000) / 60000).toInt()
                when {
                  hours >= 23 -> "Terlalu banyak percobaan. Coba lagi dalam 24 jam"
                  hours >= 5 -> "Terlalu banyak percobaan. Coba lagi dalam $hours jam"
                  hours >= 1 -> "Terlalu banyak percobaan. Coba lagi dalam ${if (mins>0) "$hours jam $mins menit" else "$hours jam"}"
                  mins > 0 -> "Terlalu banyak percobaan. Coba lagi dalam $mins menit"
                  else -> "Terlalu banyak percobaan, coba lagi nanti"
                }
              }
              isLocked -> "Terlalu banyak percobaan. Coba lagi dalam 1 jam"
              raw.contains("rate_limited") -> "Terlalu sering minta OTP, tunggu 10 menit"
              raw.contains("expired") -> "OTP kedaluwarsa"
              else -> "OTP salah: $raw"
            }
            context?.let { AppToast.show(it, msg) }
            // shake & clear WA-style
            otpBoxes.forEach { it.text.clear() }
            otpBoxes[0].requestFocus()
          }
        }
      }
    }

    // WA-style: Kirim ulang via global Sheet — muncul sheet dulu baru kirim
    btnResend.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      AppSheet.showResendOtp(requireContext(), phone, remainingResendMs, isResendCooling) {
        // konfirmasi di sheet -> baru kirim
        btnResend.isEnabled = false; btnResend.alpha = 0.5f
        progressResend.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
          val r = repo.requestOtp(phone)
          progressResend.visibility = View.GONE
          r.onSuccess {
            context?.let { AppToast.show(it, "OTP dikirim ulang ke $phone") }
            remainingResendMs = 60_000L; remainingExpiryMs = 300_000L
            startResendCountdown(60_000L); startExpiryCountdown(300_000L)
            otpBoxes.forEach { it.text.clear() }
            otpBoxes[0].requestFocus()
          }.onFailure { e ->
            val isRate = e.message == "rate_limited" || e.message == "rate_limited_ip"
            if (isRate) {
              context?.let { AppToast.show(it, "Terlalu sering, tunggu sebelum minta lagi") }
              startResendCountdown(60_000L)
            } else {
              btnResend.isEnabled = true; btnResend.alpha = 1f
              context?.let { AppToast.show(it, "Gagal: ${e.message}") }
            }
          }
        }
      }
    }

    // WA-style: Konfirmasi custom reusable (bukan bawaan AlertDialog)
    v.findViewById<TextView>(R.id.btnGantiNomor).setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      ConfirmDialog.showGantiNomor(requireContext(), phone) {
        resendTimer?.cancel()
        expiryTimer?.cancel()
        otpBoxes.forEach { it.text.clear() }
        parentFragmentManager.popBackStack()
        context?.let { AppToast.show(it, "Silakan masukkan nomor baru") }
      }
    }
  }

  private fun collectOtp(): String = otpBoxes.joinToString("") { it.text.toString().trim() }

  private fun showLoadingDialog(msg: String = "Memverifikasi...") {
    if (loadingDialog?.isShowing == true) return
    val view = layoutInflater.inflate(R.layout.dialog_loading, null)
    view.findViewById<TextView>(R.id.txtLoading).text = msg
    loadingDialog = AlertDialog.Builder(requireContext())
      .setView(view)
      .setCancelable(false)
      .create()
    loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    loadingDialog?.show()
  }

  private fun hideLoadingDialog() {
    loadingDialog?.dismiss()
    loadingDialog = null
  }

  private fun startChainDelete() {
    if (isChainDeleting) return
    isChainDeleting = true
    deleteRunnable = object : Runnable {
      override fun run() {
        if (!isChainDeleting) return
        // cari kotak terakhir yang terisi
        var targetIdx = -1
        for (i in 5 downTo 0) {
          if (otpBoxes[i].text.isNotEmpty()) { targetIdx = i; break }
        }
        if (targetIdx == -1) { stopChainDelete(); return }
        otpBoxes[targetIdx].text.clear()
        otpBoxes[targetIdx.coerceAtMost(5)].requestFocus()
        // interval berantai 90ms biar kelihatan WA-style hapus berurutan
        deleteHandler.postDelayed(this, 90)
      }
    }
    // mulai langsung tanpa delay, biar responsif
    deleteHandler.post(deleteRunnable!!)
  }

  private fun stopChainDelete() {
    isChainDeleting = false
    deleteRunnable?.let { deleteHandler.removeCallbacks(it) }
    deleteRunnable = null
  }

  private fun setupOtpBoxes() {
    otpBoxes.forEachIndexed { idx, edit ->
      edit.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
          val txt = s?.toString() ?: ""
          // handle paste 6 digits
          if (txt.length > 1) {
            val digits = txt.filter { it.isDigit() }.take(6)
            digits.forEachIndexed { i, ch ->
              val pos = idx + i
              if (pos < 6) otpBoxes[pos].setText(ch.toString())
            }
            if (digits.length == 6) otpBoxes[5].requestFocus()
            else if (idx + digits.length < 6) otpBoxes[idx + digits.length].requestFocus()
            return
          }
          if (txt.isNotEmpty() && idx < 5) {
            otpBoxes[idx + 1].requestFocus()
            otpBoxes[idx + 1].setSelection(otpBoxes[idx + 1].text.length)
          }
        }
      })
      // WA-style: single delete via KeyEvent (mundur 1 kotak)
      edit.setOnKeyListener { _, keyCode, event ->
        if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DEL) {
          // repeatCount >0 = tahan tombol fisik -> berantai (jangan clear semua sekaligus)
          if (event.repeatCount > 0) {
            if (!isChainDeleting) startChainDelete()
            return@setOnKeyListener true
          }
          if (edit.text.isEmpty() && idx > 0) {
            otpBoxes[idx - 1].requestFocus()
            otpBoxes[idx - 1].text.clear()
            return@setOnKeyListener true
          }
        }
        if (event.action == android.view.KeyEvent.ACTION_UP && keyCode == android.view.KeyEvent.KEYCODE_DEL) {
          stopChainDelete()
        }
        false
      }
      // WA-style long-press pada kotak OTP: hapus berantai 1 per 1 sampai habis
      edit.setOnLongClickListener {
        startChainDelete()
        true
      }
      // sentuh lepas -> hentikan chain delete
      edit.setOnTouchListener { _, ev ->
        if (ev.action == android.view.MotionEvent.ACTION_UP || ev.action == android.view.MotionEvent.ACTION_CANCEL) {
          stopChainDelete()
        }
        false
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putLong("resendMs", remainingResendMs)
    outState.putLong("expiryMs", remainingExpiryMs)
    outState.putBoolean("isCooling", isResendCooling)
    // JANGAN simpan OTP di Bundle (aman: tidak bocor via adb backup)
  }

  private fun requestSmsPermissionIfNeeded() {
    val perms = arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS)
    val missing = perms.filter { androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
    if (missing.isNotEmpty()) {
      requestPermissions(missing.toTypedArray(), 1001)
    }
  }

  private fun registerSmsReceiver() {
    smsReceiver = object : android.content.BroadcastReceiver() {
      override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
        val otp = i?.getStringExtra(com.upme.messenger.data.auth.SmsOtpReceiver.EXTRA_OTP) ?: return
        if (otp.length == 6 && otp.all { it.isDigit() }) {
          otpBoxes.forEachIndexed { idx, e -> e.setText(otp[idx].toString()) }
          context?.let { AppToast.show(it, "OTP terisi otomatis") }
          // WA-style: auto-tap Verifikasi setelah 300ms jika 6 digit terisi
          view?.postDelayed({
            if (isAdded && collectOtp().length == 6) {
              view?.findViewById<android.view.View>(R.id.btnVerif)?.performClick()
            }
          }, 300)
        }
      }
    }
    val filter = android.content.IntentFilter(com.upme.messenger.data.auth.SmsOtpReceiver.ACTION_OTP)
    // Anti-inject: NOT_EXPORTED (Android 13+) — hanya broadcast via setPackage dari app sendiri bisa diterima
    androidx.core.content.ContextCompat.registerReceiver(requireContext(), smsReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
  }

  override fun onDestroyView() {
    resendTimer?.cancel()
    expiryTimer?.cancel()
    stopChainDelete()
    try { smsReceiver?.let { requireContext().unregisterReceiver(it) } } catch (_: Exception) {}
    hideLoadingDialog()
    super.onDestroyView()
  }

  @Deprecated("Deprecated in Java")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (requestCode == 1001) {
      val granted = grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
      if (!granted) {
        context?.let { AppToast.show(it, "Izin SMS ditolak — isi OTP manual") }
      }
    }
  }

  private fun showTwoFaDialog(phone: String) {
    val input = EditText(requireContext()).apply { hint = "PIN 2FA 6 digit"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
    var dialog: AlertDialog? = null
    var isLoading = false
    dialog = AlertDialog.Builder(requireContext())
      .setTitle("Registration Lock aktif")
      .setMessage("Akun ini terkunci 2FA. Masukkan PIN 6 digit.")
      .setView(input)
      .setPositiveButton("Verifikasi", null)
      .setNegativeButton("Batal", null)
      .create()
    dialog.setOnShowListener {
      val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
      btn.setOnClickListener {
        if (isLoading) return@setOnClickListener
        val pin = input.text.toString().trim()
        if (pin.length != 6) { context?.let { AppToast.show(it, "PIN 6 digit") }; return@setOnClickListener }
        isLoading = true
        btn.isEnabled = false; btn.alpha = 0.5f
        viewLifecycleOwner.lifecycleScope.launch {
          val r = repo.verifyTwoFa(phone, pin)
          isLoading = false
          btn.isEnabled = true; btn.alpha = 1f
          r.onSuccess { ok ->
            val prefs = AuthPrefs(requireContext())
            prefs.phone = ok.phone; prefs.pin = ok.pin; prefs.token = ok.token; prefs.name = ok.name
            context?.let { AppToast.show(it, "2FA sukses") }
            dialog.dismiss()
            val f = SetupProfilFragment().apply { arguments = Bundle().apply { putString("pin", ok.pin); putString("name", ok.name) } }
            parentFragmentManager.beginTransaction().replace(R.id.container, f).commit()
          }.onFailure { e -> context?.let { AppToast.show(it, "2FA salah: ${e.message}") } }
        }
      }
    }
    dialog.show()
  }
}
