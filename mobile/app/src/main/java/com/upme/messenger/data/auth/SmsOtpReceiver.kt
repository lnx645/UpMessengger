package com.upme.messenger.data.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
/** WA-style SMS auto-fill: tangkap SMS UpMessenger, ekstrak 6 digit, broadcast lokal — anti inject via setPackage */
class SmsOtpReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return
    try {
      val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
      val body = messages.joinToString("") { it.messageBody ?: "" }
      // Strict: hanya UpMessenger, tidak fallback generic "OTP" (mencegah SMS bank inject)
      val otp = Regex("""\b\d{6}\b""").find(body)?.value
      if (otp != null && body.contains("UpMessenger", ignoreCase = true)) {
        val i = Intent(ACTION_OTP).apply {
          putExtra(EXTRA_OTP, otp)
          // anti-inject: broadcast hanya ke package sendiri
          setPackage(context.packageName)
        }
        context.sendBroadcast(i)
      }
    } catch (_: Exception) {
    }
  }
  companion object {
    const val ACTION_OTP = "com.upme.messenger.OTP_SMS"
    const val EXTRA_OTP = "otp"
  }
}
