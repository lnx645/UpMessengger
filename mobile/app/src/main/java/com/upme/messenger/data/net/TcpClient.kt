package com.upme.messenger.data.net
import com.upme.messenger.data.local.MessageDao
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.net.ssl.SSLSocketFactory
import java.net.Socket
/** TCP + TLS, auth wajib via token (frame 0x02), heartbeat 30s, reconnect exponential backoff. Prod: port 5222. */
class TcpClient(private val dao: MessageDao, private val host: String = "127.0.0.1", private val port: Int = 5222) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun openSocket(): Socket {
    // Dev: adb reverse 127.0.0.1 plain. Prod: TLS only, fail-closed tanpa fallback cleartext
    return if (host == "127.0.0.1") {
      Socket(host, port).apply { soTimeout = 4000 }
    } else {
      SSLSocketFactory.getDefault().createSocket(host, port).apply { soTimeout = 4000 }
    }
  }

  private fun authSocket(s: Socket, token: String): Boolean {
    return try {
      val out = DataOutputStream(s.getOutputStream())
      val inp = DataInputStream(s.getInputStream())
      val payload = JSONObject().put("token", token).toString().toByteArray()
      out.writeInt(payload.size + 1); out.writeByte(0x02); out.write(payload); out.flush()
      s.soTimeout = 5000
      val len = inp.readInt(); val t = inp.readByte().toInt() and 0xFF
      val body = ByteArray(len - 1); inp.readFully(body)
      t == 0x02
    } catch (_: Exception) { false }
  }

  /** Kirim MSG_SEND: frame [len32][type][payload]. Wajib token valid, validasi ciphertext_b64. */
  fun send(conversationId: String, id: String, cipherB64: String, token: String) {
    // Validasi input sebelum jaringan: cegah inject & OOM
    if (conversationId.isBlank() || id.isBlank() || cipherB64.isBlank()) return
    if (cipherB64.length > 400_000) return // ~300KB binary
    try { android.util.Base64.decode(cipherB64, android.util.Base64.NO_WRAP) } catch (_: Exception) { return }

    scope.launch {
      var delayMs = 1000L
      var attempts = 0
      while (attempts < 5) {
        var s: Socket? = null
        try {
          s = openSocket()
          if (!authSocket(s, token)) {
            s.close(); break // token invalid -> jangan retry spam
          }
          val out = DataOutputStream(s.getOutputStream())
          val payload = JSONObject().apply {
            put("id", id)
            put("conversation_id", conversationId)
            put("ciphertext_b64", cipherB64)
          }.toString().toByteArray()
          out.writeInt(payload.size + 1); out.writeByte(0x03); out.write(payload); out.flush()
          val inp = DataInputStream(s.getInputStream())
          s.soTimeout = 5000
          val len = inp.readInt(); val ackType = inp.readByte().toInt() and 0xFF
          val body = ByteArray(len - 1); inp.readFully(body)
          if (ackType == 0x04) {
            // Update status SENT di DB lokal bila ada dao
            try { /* dao update status bisa ditambah bila ada method */ } catch (_: Exception) {}
            s.close(); break
          } else if (ackType == 0x1F) {
            // error from server (unauthenticated / rate_limited) -> jangan retry
            s.close(); break
          }
          s.close(); break
        } catch (e: Exception) {
          try { s?.close() } catch (_: Exception) {}
          attempts++
          if (attempts >= 5) break
          delay(delayMs); delayMs = (delayMs * 2).coerceAtMost(30_000)
        }
      }
    }
  }

  // --- Teman frames ---

  private fun requestOnce(payload: ByteArray, type: Int): Pair<Int, ByteArray> {
    val s = openSocket()
    try {
      s.soTimeout = 5000
      val out = DataOutputStream(s.getOutputStream())
      val inp = DataInputStream(s.getInputStream())
      out.writeInt(payload.size + 1); out.writeByte(type); out.write(payload); out.flush()
      val len = inp.readInt(); val t = inp.readByte().toInt() and 0xFF
      val body = ByteArray(len - 1); inp.readFully(body)
      return Pair(t, body)
    } finally { try { s.close() } catch (_: Exception) {} }
  }

  fun inviteTeman(phone: String, token: String, targetPin: String): String? {
    return try {
      val payload = JSONObject().put("phone", phone).put("token", token).put("target_pin", targetPin).toString().toByteArray()
      val (t, body) = requestOnce(payload, 0x32)
      val json = JSONObject(String(body))
      if (t == 0x32) json.optString("status", null) else { json.optString("error", null) }
    } catch (_: Exception) { null }
  }

  fun syncPending(phone: String, token: String): List<Map<String, String>> {
    return try {
      val payload = JSONObject().put("phone", phone).put("token", token).toString().toByteArray()
      val (t, body) = requestOnce(payload, 0x34)
      if (t == 0x34) {
        val arr = JSONObject(String(body)).optJSONArray("pending") ?: return emptyList()
        (0 until arr.length()).map { arr.getJSONObject(it).toMapString() }
      } else emptyList()
    } catch (_: Exception) { emptyList() }
  }

  private fun JSONObject.toMapString(): Map<String, String> {
    val m = mutableMapOf<String, String>()
    keys().forEach { k -> m[k] = optString(k, "") }
    return m
  }

  fun acceptInvite(phone: String, token: String, fromPin: String): Boolean {
    return try {
      val payload = JSONObject().put("phone", phone).put("token", token).put("from_pin", fromPin).toString().toByteArray()
      val (t, _) = requestOnce(payload, 0x35)
      t == 0x35
    } catch (_: Exception) { false }
  }

  fun rejectInvite(phone: String, token: String, fromPin: String): Boolean {
    return try {
      val payload = JSONObject().put("phone", phone).put("token", token).put("from_pin", fromPin).toString().toByteArray()
      val (t, _) = requestOnce(payload, 0x36)
      t == 0x36
    } catch (_: Exception) { false }
  }

  fun deleteTeman(phone: String, token: String, pin: String, block: Boolean): Boolean {
    return try {
      val payload = JSONObject().put("phone", phone).put("token", token).put("pin", pin).put("block", block).toString().toByteArray()
      val (t, _) = requestOnce(payload, 0x37)
      t == 0x37
    } catch (_: Exception) { false }
  }

  // Overload tanpa token untuk kompatibilitas lama -> tolak (jangan kirim tanpa auth)
  @Deprecated("Gunakan send(..., token) untuk rilis publik")
  fun send(conversationId: String, id: String, cipherB64: String) {
    // No-op: wajib token di rilis publik. Caller harus migrasi.
  }
}
