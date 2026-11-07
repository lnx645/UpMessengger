package com.upme.messenger.data.net

import java.security.MessageDigest
import java.security.cert.Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore

object CertPinning {
    // SHA-256 pins base64 (without prefix) for api.sms-gate.app
    private val PINS = setOf(
        "YLh1dUR9y6Kja30RrAn7Fcb9ZGD/6mL8K+5Y6bR2kSk=",
        "C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wGlR8Q=",
        "diGV5VD4M2Gmza/R5qSK/FUG2lw9FOLMCYFuDXFprxo="
    )

    fun isPinned(cert: Certificate): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            val b64 = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
            b64 in PINS
        } catch (_: Exception) { false }
    }

    fun tlsSocketFactory(): SSLSocketFactory {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val ctx = SSLContext.getInstance("TLSv1.3")
        ctx.init(null, tmf.trustManagers, null)
        return ctx.socketFactory
    }
}
