package com.upme.messenger.domain.usecase
import com.upme.messenger.data.auth.AuthPrefs
import com.upme.messenger.data.crypto.CryptoProvider
import com.upme.messenger.data.local.MessageDao
import com.upme.messenger.data.local.MessageEntity
import com.upme.messenger.data.net.TcpClient
import java.util.UUID
/** UseCase kirim pesan local-first: enkripsi -> simpan PENDING -> kirim TCP (auth) -> SENT. */
class SendMessageUseCase(
  private val dao: MessageDao,
  private val tcp: TcpClient,
  private val crypto: CryptoProvider,
  private val authPrefs: AuthPrefs
) {
  suspend fun execute(conversationId: String, plain: ByteArray): String {
    require(conversationId.isNotBlank()) { "conversationId kosong" }
    require(plain.isNotEmpty() && plain.size <= 256 * 1024) { "plain invalid" }
    val token = authPrefs.token ?: throw IllegalStateException("belum login")
    // 1. Enkripsi di client (E2EE), server hanya terima ciphertext.
    val cipher = crypto.encrypt(conversationId, plain)
    // 2. Tulis lokal dulu sebagai PENDING
    val id = UUID.randomUUID().toString()
    dao.upsert(MessageEntity(id, conversationId, cipher, "PENDING", System.currentTimeMillis()))
    // 3. Kirim via TCP dengan auth token
    tcp.send(conversationId, id, android.util.Base64.encodeToString(cipher, android.util.Base64.NO_WRAP), token)
    return id
  }
  // overload tanpa auth untuk migrasi bertahap - tolak
  @Deprecated("Gunakan execute dengan AuthPrefs")
  suspend fun execute(conversationId: String, plain: ByteArray, token: String): String {
    return execute(conversationId, plain)
  }
}
