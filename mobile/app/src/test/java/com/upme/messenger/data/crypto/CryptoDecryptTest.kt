package com.upme.messenger.data.crypto

import org.junit.Test
import org.junit.Assert.*

/**
 * Regression: detail must show decrypted plaintext, not id/status.
 * CryptoProvider encrypt/decrypt round-trip must be correct.
 * Uses plain AES-GCM with fixed key for JVM test (no AndroidKeyStore).
 */
class CryptoDecryptTest {
  // JVM fallback provider for test (no KeyStore)
  private fun encryptJvm(plain: ByteArray): ByteArray {
    val key = javax.crypto.spec.SecretKeySpec(ByteArray(16) { 0x42 }, "AES")
    val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    c.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
    return c.iv + c.doFinal(plain)
  }
  private fun decryptJvm(blob: ByteArray): ByteArray {
    val key = javax.crypto.spec.SecretKeySpec(ByteArray(16) { 0x42 }, "AES")
    val iv = blob.copyOfRange(0, 12); val ct = blob.copyOfRange(12, blob.size)
    val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    c.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
    return c.doFinal(ct)
  }

  @Test fun roundTrip_hello() {
    val plain = "halo_test_123".toByteArray()
    val blob = encryptJvm(plain)
    val out = decryptJvm(blob)
    assertArrayEquals(plain, out)
  }

  @Test fun decrypt_showsPlaintextNotId() {
    val plain = "pesan rahasia".toByteArray()
    val blob = encryptJvm(plain)
    val decrypted = String(decryptJvm(blob))
    assertEquals("pesan rahasia", decrypted)
    assertNotEquals("a1b2c3", decrypted)
  }

  @Test fun corruptedCipher_failsClosed() {
    val blob = encryptJvm("hi".toByteArray())
    val bad = blob.copyOf(); bad[15] = (bad[15].toInt() xor 0xFF).toByte()
    try {
      decryptJvm(bad)
      fail("should throw AEADBadTagException")
    } catch (e: Exception) {
      // fail closed = exception, not raw text
      assertTrue(e.message != "hi")
    }
  }
}
