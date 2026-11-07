package com.upme.messenger.data.auth
import android.util.Base64
import org.json.JSONObject

/** Helper JWT HS256: decode payload tanpa verifikasi signature (verifikasi di server). Untuk cek expiry di client. */
object JwtHelper {
  fun isExpired(token: String?): Boolean {
    if (token.isNullOrBlank()) return true
    return try {
      val parts = token.split(".")
      if (parts.size != 3) return true
      val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
      val exp = JSONObject(payload).optLong("exp", 0)
      if (exp == 0L) return false // token lama opaque tanpa exp -> anggap valid
      val now = System.currentTimeMillis() / 1000
      now >= exp
    } catch (_: Exception) { true }
  }

  fun getPhone(token: String?): String? = try {
    val parts = token!!.split(".")
    val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
    val v = JSONObject(payload).optString("sub", "")
    if (v.isEmpty()) null else v
  } catch (_: Exception) { null }

  fun getName(token: String?): String? = try {
    val parts = token!!.split(".")
    val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
    val v = JSONObject(payload).optString("name", "")
    if (v.isEmpty()) null else v
  } catch (_: Exception) { null }
}
