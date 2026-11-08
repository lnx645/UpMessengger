package com.upme.messenger
import android.app.Application
/** Application minimal agar startup cepat: tidak init berat di sini, semua lazy via Startup. */
class UpMeApp : Application() {
  val locator by lazy { ServiceLocator(this) }
  override fun onCreate() {
    super.onCreate()
    com.upme.messenger.util.HostUtil.init(this)
    // Single-source PIN/phone migration (Fix 3): upme -> auth_enc once
    try {
      val auth = com.upme.messenger.data.auth.AuthPrefs(this)
      // migrate legacy "auth" prefs already in AuthPrefs.migrateLegacy
      auth.migrateLegacy(this)
      // migrate old "upme" plain pin
      val upme = getSharedPreferences("upme", MODE_PRIVATE)
      val legacyPin = upme.getString("pin", null)
      if (!legacyPin.isNullOrBlank() && auth.pin.isNullOrBlank()) {
        auth.pin = legacyPin
      }
      if (upme.contains("pin")) upme.edit().remove("pin").apply()
    } catch (_: Exception) {}
  }
}
