package com.enmooy.deepseno.service

import android.util.Base64
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object TlsPinning {
    /** hex "AA:BB:.." -> base64 of the 32 raw bytes (the OkHttp SPKI pin form). */
    private fun hexToBase64(hex: String): String {
        val bytes = hex.split(":").map { it.trim().toInt(16).toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Returns a client that trusts the desktop's self-signed cert solely by its
     * SPKI SHA-256 pin. Only used for the public HTTPS endpoint (a VPS IP relay);
     * LAN HTTP keeps the base client.
     *
     * Why a custom trust manager instead of OkHttp's CertificatePinner:
     * the desktop cert is self-signed with no CA and no SAN, so the platform
     * X509TrustManager rejects it (SunCertPathBuilderException) BEFORE
     * CertificatePinner runs. A trust-all manager would let the handshake proceed
     * but then OkHttp cleans the chain to empty, so CertificatePinner sees no certs
     * and still fails (SSLPeerUnverifiedException). The robust fix — matching the
     * iOS client — is to bypass default trust evaluation and verify the SPKI pin
     * directly inside the trust manager. hostnameVerifier is bypassed because we
     * connect to a VPS IP (cert CN won't match) — the SPKI pin is what proves
     * identity.
     */
    fun pinnedClient(base: OkHttpClient, host: String, fingerprintHex: String): OkHttpClient {
        val expectedPin = hexToBase64(fingerprintHex)
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                    ?: throw CertificateException("No server certificate presented")
                val spki = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
                val actualPin = Base64.encodeToString(spki, Base64.NO_WRAP)
                if (actualPin != expectedPin) {
                    throw CertificateException("SPKI pin mismatch (host=$host)")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), SecureRandom())
        }
        return base.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
