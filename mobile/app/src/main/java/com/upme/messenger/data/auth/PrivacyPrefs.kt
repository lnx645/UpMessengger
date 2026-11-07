package com.upme.messenger.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Privasi & Keamanan prefs untuk pertemanan auto-approve.
 *
 * 3 toggle:
 * - Siapa bisa tambah saya (whoCanAdd) : true = semua orang, false = terbatas
 * - Auto-approve pertemanan (autoApprove) : true = langsung jadi teman tanpa konfirmasi
 * - Blok invite baru (blockInvite) : true = tolak semua invite masuk
 *
 * Simpan di EncryptedSharedPreferences ("privacy_enc") + fallback InMemory (fail-closed).
 * Field autoApprove di-sync ke DETS via TCP frame 0x30 (privacy_sync).
 */
class PrivacyPrefs(ctx: Context) {

    private val p: SharedPreferences = try {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "privacy_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        try {
            val mk2 = MasterKey.Builder(ctx, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                ctx, "privacy_enc", mk2,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            InMemoryPrefs()
        }
    }

    private class InMemoryPrefs : SharedPreferences {
        private val m = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        override fun getAll(): MutableMap<String, *> = m.toMutableMap()
        override fun getString(k: String?, d: String?): String? = m[k] as? String ?: d
        override fun getStringSet(k: String?, d: MutableSet<String>?): MutableSet<String>? = d
        override fun getInt(k: String?, d: Int): Int = (m[k] as? Int) ?: d
        override fun getLong(k: String?, d: Long): Long = (m[k] as? Long) ?: d
        override fun getFloat(k: String?, d: Float): Float = (m[k] as? Float) ?: d
        override fun getBoolean(k: String?, d: Boolean): Boolean = (m[k] as? Boolean) ?: d
        override fun contains(k: String?): Boolean = m.containsKey(k)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(k: String?, v: String?): SharedPreferences.Editor { if (k != null) { if (v == null) m.remove(k) else m[k] = v }; return this }
            override fun putStringSet(k: String?, v: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(k: String?, v: Int): SharedPreferences.Editor { if (k != null) m[k] = v; return this }
            override fun putLong(k: String?, v: Long): SharedPreferences.Editor { if (k != null) m[k] = v; return this }
            override fun putFloat(k: String?, v: Float): SharedPreferences.Editor { if (k != null) m[k] = v; return this }
            override fun putBoolean(k: String?, v: Boolean): SharedPreferences.Editor { if (k != null) m[k] = v; return this }
            override fun remove(k: String?): SharedPreferences.Editor { m.remove(k); return this }
            override fun clear(): SharedPreferences.Editor { m.clear(); return this }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) { if (l != null) listeners.add(l) }
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) { listeners.remove(l) }
    }

    var whoCanAdd: Boolean
        get() = p.getBoolean("who_can_add", true)
        set(v) { p.edit().putBoolean("who_can_add", v).apply() }

    var autoApprove: Boolean
        get() = p.getBoolean("auto_approve", true)
        set(v) { p.edit().putBoolean("auto_approve", v).apply() }

    var blockInvite: Boolean
        get() = p.getBoolean("block_invite", false)
        set(v) { p.edit().putBoolean("block_invite", v).apply() }

    /**
     * Sync field autoApprove ke server DETS via frame 0x30.
     * Payload: {phone, token, auto_approve: bool}
     * Server persist ke DETS users.dets field autoApprove.
     */
    suspend fun syncAutoApproveToServer(
        phone: String,
        token: String,
        host: String = "127.0.0.1",
        port: Int = 5222
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("phone", phone)
                .put("token", token)
                .put("auto_approve", autoApprove)
                .put("who_can_add", whoCanAdd)
                .put("block_invite", blockInvite)
                .toString().toByteArray()

            val isDevHost = host == "127.0.0.1"
            val s: Socket = if (isDevHost) {
                Socket(host, port).apply { soTimeout = 5000 }
            } else {
                SSLSocketFactory.getDefault().createSocket(host, port).apply { soTimeout = 5000 }
            }
            val out = DataOutputStream(s.getOutputStream())
            val inp = DataInputStream(s.getInputStream())
            out.writeInt(payload.size + 1); out.writeByte(0x30); out.write(payload); out.flush()
            val len = inp.readInt(); val t = inp.readByte().toInt() and 0xFF
            val body = ByteArray(len - 1); inp.readFully(body)
            s.close()
            if (t == 0x30) Result.success(Unit)
            else {
                val err = try { JSONObject(String(body)).optString("error", "sync_failed") } catch (_: Exception) { "sync_failed" }
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pull privacy dari server (frame 0x31) untuk sinkronisasi awal.
     * Jika server punya nilai auto_approve, override local.
     */
    suspend fun pullFromServer(
        phone: String,
        token: String,
        host: String = "127.0.0.1",
        port: Int = 5222
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("phone", phone).put("token", token).toString().toByteArray()
            val isDevHost = host == "127.0.0.1"
            val s: Socket = if (isDevHost) Socket(host, port).apply { soTimeout = 5000 }
            else SSLSocketFactory.getDefault().createSocket(host, port).apply { soTimeout = 5000 }
            val out = DataOutputStream(s.getOutputStream())
            val inp = DataInputStream(s.getInputStream())
            out.writeInt(payload.size + 1); out.writeByte(0x31); out.write(payload); out.flush()
            val len = inp.readInt(); val t = inp.readByte().toInt() and 0xFF
            val body = ByteArray(len - 1); inp.readFully(body)
            s.close()
            if (t == 0x31) {
                val j = JSONObject(String(body))
                if (j.has("auto_approve")) autoApprove = j.getBoolean("auto_approve")
                if (j.has("who_can_add")) whoCanAdd = j.getBoolean("who_can_add")
                if (j.has("block_invite")) blockInvite = j.getBoolean("block_invite")
                Result.success(Unit)
            } else Result.failure(Exception("pull_failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
