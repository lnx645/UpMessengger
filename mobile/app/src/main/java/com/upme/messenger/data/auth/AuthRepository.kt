package com.upme.messenger.data.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import javax.net.ssl.SSLSocketFactory
import com.upme.messenger.util.HostUtil

/** WhatsApp flow: requestOtp (0x10) -> verifyOtp (0x11) -> [0x14 2FA jika aktif] -> auth (0x02). JWT RS256. */
class AuthRepository(private val host: String = "127.0.0.1", private val port: Int = 5222) {

  class NetworkError(msg: String, cause: Throwable? = null) : java.io.IOException(msg, cause)

  private suspend fun frame(type: Int, payload: ByteArray): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
    try {
      val isDevHost = HostUtil.isDevHost(host)
      val s = if (isDevHost) {
        // Hotspot: debuggable fallback ke 192.168.* jika 127.0.0.1 gagal (HP hotspot tidak punya reverse)
        val hosts = HostUtil.resolveHosts(host)
        var last: Socket? = null
        var err: Exception? = null
        for (h in hosts) {
          try {
            last = Socket(h, port).apply { soTimeout = 8000 }
            break
          } catch (ex: Exception) { err = ex }
        }
        last ?: throw NetworkError("network_error", err)
      } else {
        val hosts = HostUtil.resolveHosts(host)
        var last: Socket? = null
        var err: Exception? = null
        for (h in hosts) {
          try {
            last = SSLSocketFactory.getDefault().createSocket(h, port)
            break
          } catch (ex: Exception) { err = ex }
        }
        last?.apply { soTimeout = 8000 } ?: throw NetworkError("network_error", err)
      }
      val out = DataOutputStream(s.getOutputStream())
      val inp = DataInputStream(s.getInputStream())
      try {
        out.writeInt(payload.size + 1); out.writeByte(type); out.write(payload); out.flush()
        val len = inp.readInt(); val t = inp.readByte().toInt() and 0xFF
        val body = ByteArray(len - 1); inp.readFully(body)
        s.close()
        t to body
      } catch (e: java.io.IOException) {
        try { s.close() } catch (_: Exception) {}
        throw NetworkError("network_error", e)
      } catch (e: Exception) {
        try { s.close() } catch (_: Exception) {}
        if (e is java.io.IOException) throw NetworkError("network_error", e) else throw e
      }
    } catch (e: NetworkError) { throw e }
    catch (e: java.io.IOException) { throw NetworkError("network_error", e) }
    catch (e: java.net.SocketException) { throw NetworkError("network_error", e) }
  }

  private fun isNetwork(e: Throwable): Boolean = e is NetworkError || e is java.io.IOException || e is java.net.SocketException || e.message == "network_error"

  suspend fun requestOtp(phone: String): Result<Unit> {
    return try {
      val payload = JSONObject().put("phone", phone).toString().toByteArray()
      val (t, body) = frame(0x10, payload)
      if (t == 0x12) Result.success(Unit) else {
        val err = try { JSONObject(String(body)).optString("error", "unknown") } catch (_: Exception) { "unknown" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      if (isNetwork(e)) Result.failure(NetworkError("network_error", e)) else Result.failure(e)
    }
  }

  data class AuthOk(val phone: String, val pin: String, val token: String, val name: String, val refreshToken: String? = null)
  sealed class VerifyResult {
    data class Success(val ok: AuthOk) : VerifyResult()
    data class NeedTwoFa(val phone: String) : VerifyResult()
    data class Error(val msg: String) : VerifyResult()
  }

  suspend fun verifyOtp(phone: String, otp: String): VerifyResult {
    return try {
      val payload = JSONObject().put("phone", phone).put("otp", otp).toString().toByteArray()
      val (t, body) = frame(0x11, payload)
      when (t) {
        0x13 -> {
          val j = JSONObject(String(body))
          VerifyResult.Success(AuthOk(j.getString("phone"), j.getString("pin"), j.getString("token"), j.getString("name"), j.optString("refresh_token", "").takeIf { it.isNotEmpty() }))
        }
        0x14 -> VerifyResult.NeedTwoFa(phone)
        else -> {
          val err = try { JSONObject(String(body)).optString("error", "invalid") } catch (_: Exception) { "invalid" }
          VerifyResult.Error(err)
        }
      }
    } catch (e: Exception) {
      if (isNetwork(e)) VerifyResult.Error("network_error") else VerifyResult.Error(e.message ?: "unknown")
    }
  }

  // kompatibilitas lama
  suspend fun verifyOtpLegacy(phone: String, otp: String): Result<AuthOk> {
    return when (val r = verifyOtp(phone, otp)) {
      is VerifyResult.Success -> Result.success(r.ok)
      is VerifyResult.NeedTwoFa -> Result.failure(Exception("need_two_fa"))
      is VerifyResult.Error -> Result.failure(Exception(r.msg))
    }
  }

  suspend fun verifyTwoFa(phone: String, pin: String): Result<AuthOk> {
    return try {
      val payload = JSONObject().put("phone", phone).put("pin", pin).toString().toByteArray()
      val (t, body) = frame(0x14, payload)
      if (t == 0x13) {
        val j = JSONObject(String(body))
        Result.success(AuthOk(j.getString("phone"), j.getString("pin"), j.getString("token"), j.optString("name",""), j.optString("refresh_token","").takeIf{it.isNotEmpty()}))
      } else {
        val err = try { JSONObject(String(body)).optString("error", "invalid_two_fa") } catch (_: Exception) { "invalid_two_fa" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      if (isNetwork(e)) Result.failure(NetworkError("network_error", e)) else Result.failure(e)
    }
  }

  suspend fun setTwoFa(phone: String, pin: String, token: String): Result<Unit> {
    return try {
      val payload = JSONObject().put("phone", phone).put("pin", pin).put("token", token).toString().toByteArray()
      val (t, body) = frame(0x15, payload)
      if (t == 0x15) Result.success(Unit) else {
        val err = try { JSONObject(String(body)).optString("error", "failed") } catch (_: Exception) { "failed" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) { Result.failure(e) }
  }

  suspend fun disableTwoFa(phone: String, pin: String, token: String): Result<Unit> {
    return try {
      val payload = JSONObject().put("phone", phone).put("pin", pin).put("token", token).toString().toByteArray()
      val (t, body) = frame(0x16, payload)
      if (t == 0x16) Result.success(Unit) else {
        val err = try { JSONObject(String(body)).optString("error", "failed") } catch (_: Exception) { "failed" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) { Result.failure(e) }
  }

  suspend fun authenticate(token: String): Result<AuthOk> {
    return try {
      val payload = JSONObject().put("token", token).toString().toByteArray()
      val (t, body) = frame(0x02, payload)
      if (t == 0x02) {
        val j = JSONObject(String(body))
        Result.success(AuthOk(j.getString("phone"), j.getString("pin"), token, j.getString("name"), j.optString("refresh_token", token)))
      } else {
        val err = try { JSONObject(String(body)).optString("error", "unauthenticated") } catch (_: Exception) { "unauthenticated" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      if (isNetwork(e)) Result.failure(NetworkError("network_error", e)) else Result.failure(e)
    }
  }

  suspend fun refresh(refreshToken: String): Result<AuthOk> {
    return try {
      val payload = JSONObject().put("refresh_token", refreshToken).toString().toByteArray()
      val (t, body) = frame(0x17, payload)
      if (t == 0x17) {
        val j = JSONObject(String(body))
        Result.success(AuthOk(j.getString("phone"), j.getString("pin"), j.getString("token"), j.getString("name"), j.getString("refresh_token")))
      } else {
        val err = try { JSONObject(String(body)).optString("error", "invalid_token") } catch (_: Exception) { "invalid_token" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      if (isNetwork(e)) Result.failure(NetworkError("network_error", e)) else Result.failure(e)
    }
  }

  // X25519 identity publish
  suspend fun publishIdentity(phone: String, token: String, identityPubB64: String): Result<Unit> {
    return try {
      val payload = JSONObject().put("phone", phone).put("token", token).put("identity_pub", identityPubB64).put("prekey_b64", "").toString().toByteArray()
      val (t, body) = frame(0x20, payload)
      if (t == 0x20) Result.success(Unit) else {
        val err = try { JSONObject(String(body)).optString("error", "failed") } catch (_: Exception) { "failed" }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) { Result.failure(e) }
  }
}
