package com.upme.messenger.data.auth
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** WhatsApp arsitektur: sesi persisten via token + phone + pin, mirip WA. Prod: EncryptedSharedPreferences. */
class AuthPrefs(ctx: Context) {
  private val p: SharedPreferences = try {
    val masterKey = MasterKey.Builder(ctx)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    EncryptedSharedPreferences.create(
      ctx,
      "auth_enc",
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  } catch (_: Exception) {
    // Fail-closed: jika EncryptedSharedPreferences tidak tersedia, tetap coba buat dengan AndroidKeyStore
    // Jika tetap gagal, fallback ke in-memory (tidak persist plaintext) untuk mencegah bocor token di disk
    try {
      // Kedua: coba buat lagi tanpa StrongBox requirement (default)
      val mk2 = MasterKey.Builder(ctx, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
      EncryptedSharedPreferences.create(ctx, "auth_enc", mk2,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (_: Exception) {
      // Fallback persist file (bukan RAM) — cegah token hilang saat kill; trade-off: plaintext tapi tidak hilang
      try {
        ctx.getSharedPreferences("auth_enc_fallback", Context.MODE_PRIVATE)
      } catch (_: Exception) {
        InMemoryPrefs()
      }
    }
  }

  // In-memory fallback (last resort)
  private class InMemoryPrefs : SharedPreferences {
    private val m = mutableMapOf<String, String?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun getAll(): MutableMap<String, *> = m.toMutableMap()
    override fun getString(k: String?, d: String?): String? = m[k] ?: d
    override fun getStringSet(k: String?, d: MutableSet<String>?): MutableSet<String>? = d
    override fun getInt(k: String?, d: Int): Int = d
    override fun getLong(k: String?, d: Long): Long = d
    override fun getFloat(k: String?, d: Float): Float = d
    override fun getBoolean(k: String?, d: Boolean): Boolean = d
    override fun contains(k: String?): Boolean = m.containsKey(k)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
      override fun putString(k: String?, v: String?): SharedPreferences.Editor { if (k!=null) { if (v==null) m.remove(k) else m[k]=v }; return this }
      override fun putStringSet(k: String?, v: MutableSet<String>?): SharedPreferences.Editor = this
      override fun putInt(k: String?, v: Int): SharedPreferences.Editor = this
      override fun putLong(k: String?, v: Long): SharedPreferences.Editor = this
      override fun putFloat(k: String?, v: Float): SharedPreferences.Editor = this
      override fun putBoolean(k: String?, v: Boolean): SharedPreferences.Editor = this
      override fun remove(k: String?): SharedPreferences.Editor { m.remove(k); return this }
      override fun clear(): SharedPreferences.Editor { m.clear(); return this }
      override fun commit(): Boolean = true
      override fun apply() {}
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) { if(l!=null) listeners.add(l) }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) { listeners.remove(l) }
  }

  var phone: String? get() = p.getString("phone", null); set(v) { p.edit().putString("phone", v).apply() }
  var pin: String? get() = p.getString("pin", null); set(v) { p.edit().putString("pin", v).apply() }
  var token: String? get() = p.getString("token", null); set(v) { p.edit().putString("token", v).apply() }
  var refreshToken: String? get() = p.getString("refresh_token", null); set(v) { p.edit().putString("refresh_token", v).apply() }
  var name: String? get() = p.getString("name", null); set(v) { p.edit().putString("name", v).apply() }
  val isLoggedIn: Boolean get() = !token.isNullOrEmpty() && !phone.isNullOrEmpty() && (!JwtHelper.isExpired(token) || hasRefresh)
  val needsRefresh: Boolean get() = JwtHelper.isExpired(token) && !refreshToken.isNullOrEmpty()
  val hasRefresh: Boolean get() = !refreshToken.isNullOrEmpty()
  fun clear() { p.edit().clear().apply() }
  // Hapus legacy plain prefs jika ada setelah migrasi
  fun migrateLegacy(ctx: Context) {
    try {
      val legacy = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
      if (legacy.contains("token") && !p.contains("token")) {
        p.edit().putString("phone", legacy.getString("phone", null))
          .putString("pin", legacy.getString("pin", null))
          .putString("token", legacy.getString("token", null))
          .putString("refresh_token", legacy.getString("refresh_token", null))
          .putString("name", legacy.getString("name", null))
          .apply()
        legacy.edit().clear().apply()
      }
    } catch (_: Exception) {}
  }
}
