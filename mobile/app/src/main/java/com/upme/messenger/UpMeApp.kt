package com.upme.messenger
import android.app.Application
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
/** Application minimal agar startup cepat: tidak init berat di sini, semua lazy via Startup. */
class UpMeApp : Application() {
  val locator by lazy { ServiceLocator(this) }
  override fun onCreate() {
    super.onCreate()
    com.upme.messenger.util.HostUtil.init(this)
    // Single-source PIN/phone migration (Fix 3)
    try {
      val auth = com.upme.messenger.data.auth.AuthPrefs(this)
      auth.migrateLegacy(this)
      val upme = getSharedPreferences("upme", MODE_PRIVATE)
      val legacyPin = upme.getString("pin", null)
      if (!legacyPin.isNullOrBlank() && auth.pin.isNullOrBlank()) auth.pin = legacyPin
      if (upme.contains("pin")) upme.edit().remove("pin").apply()
    } catch (_: Exception) {}
    // Silent refresh if token expired but refresh_token exists (Fix 2: no re-login after 15m offline)
    try {
      val auth = com.upme.messenger.data.auth.AuthPrefs(this)
      if (auth.needsRefresh) {
        GlobalScope.launch(Dispatchers.IO) {
          try {
            val rt = auth.refreshToken ?: return@launch
            val repo = com.upme.messenger.data.auth.AuthRepository()
            val res = repo.refresh(rt)
            res.onSuccess { ok ->
              auth.token = ok.token
              if (!ok.refreshToken.isNullOrBlank()) auth.refreshToken = ok.refreshToken
            }.onFailure {
              if (it.message != "network_error") auth.clear()
            }
          } catch (_: Exception) {}
        }
      }
    } catch (_: Exception) {}
  }
}
