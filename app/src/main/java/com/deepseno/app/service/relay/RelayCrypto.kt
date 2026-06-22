package com.enmooy.deepseno.service.relay

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ECDH P-256 key agreement + AES-256-GCM encryption for the relay tunnel.
 * Mirrors the desktop's relay-crypto.ts — same frame format, same HKDF params.
 *
 * Frame layout (binary):
 *   [4 bytes: length (uint32 BE)] [12 bytes: nonce] [N bytes: ciphertext] [16 bytes: GCM tag]
 */
object RelayCrypto {

    private const val NONCE_SIZE = 12
    private const val TAG_SIZE = 16

    /** Generate an ECDH P-256 key pair. Returns the private key and the SPKI DER public key (base64). */
    fun generateKeyPair(): Pair<PrivateKey, String> {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        return kp.private to pubB64
    }

    /**
     * Derive the shared AES-256 key from our private key and the peer's public key.
     * Uses ECDH + HKDF-SHA256 with the pairing nonce as salt.
     */
    fun deriveSharedKey(
        ourPrivateKey: PrivateKey,
        peerPublicKeyBase64: String,
        nonceBase64: String,
    ): ByteArray {
        val pubKeyBytes = Base64.getDecoder().decode(peerPublicKeyBase64)
        val kf = KeyFactory.getInstance("EC")
        val pubSpec = java.security.spec.X509EncodedKeySpec(pubKeyBytes)
        val peerPubKey = kf.generatePublic(pubSpec)

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ourPrivateKey)
        ka.doPhase(peerPubKey, true)
        val sharedSecret = ka.generateSecret()

        // HKDF-SHA256 → 32 bytes
        val nonce = Base64.getDecoder().decode(nonceBase64)
        return hkdfSha256(sharedSecret, nonce, "deepseno-relay-v1".toByteArray(), 32)
    }

    /**
     * Derive an AES-256 key from the LAN bearer token. Used for WebSocket
     * proxy-req/resp encryption on the local network. Mirrors desktop's
     * deriveLanKey() in proxy-dispatcher.ts.
     */
    fun deriveLanKey(token: String): ByteArray {
        return hkdfSha256(
            token.toByteArray(Charsets.UTF_8),
            "deepseno-lan-v1".toByteArray(),  // salt
            "deepseno-lan-proxy".toByteArray(), // info
            32,
        )
    }

    /** Encrypt a single plaintext chunk into a Frame. */
    fun encryptFrame(aesKey: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext)

        val length = ByteArray(4)
        length[0] = ((NONCE_SIZE + ciphertext.size) shr 24).toByte()
        length[1] = ((NONCE_SIZE + ciphertext.size) shr 16).toByte()
        length[2] = ((NONCE_SIZE + ciphertext.size) shr 8).toByte()
        length[3] = (NONCE_SIZE + ciphertext.size).toByte()

        return length + nonce + ciphertext
    }

    /** Decrypt a single Frame. */
    fun decryptFrame(aesKey: ByteArray, frame: ByteArray): ByteArray {
        if (frame.size < 4 + NONCE_SIZE + TAG_SIZE) throw IllegalArgumentException("Frame too short")
        val frameLen = ((frame[0].toInt() and 0xFF) shl 24) or
                ((frame[1].toInt() and 0xFF) shl 16) or
                ((frame[2].toInt() and 0xFF) shl 8) or
                (frame[3].toInt() and 0xFF)
        if (frame.size < 4 + frameLen) throw IllegalArgumentException("Frame incomplete")

        val nonce = frame.copyOfRange(4, 4 + NONCE_SIZE)
        val ciphertext = frame.copyOfRange(4 + NONCE_SIZE, 4 + frameLen - TAG_SIZE)
        val tag = frame.copyOfRange(4 + frameLen - TAG_SIZE, 4 + frameLen)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
        // GCM tag is appended to ciphertext in Java's Cipher API
        return cipher.doFinal(ciphertext + tag)
    }

    /** Encrypt a request header + optional body into frames. */
    fun encryptRequest(
        aesKey: ByteArray,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        chunkSize: Int = 1024 * 1024,
    ): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val headerJson = """{"method":"$method","path":"$path","headers":${toJson(headers)}}"""
        frames.add(encryptFrame(aesKey, headerJson.toByteArray(Charsets.UTF_8)))

        if (body != null && body.isNotEmpty()) {
            var offset = 0
            while (offset < body.size) {
                val end = minOf(offset + chunkSize, body.size)
                frames.add(encryptFrame(aesKey, body.copyOfRange(offset, end)))
                offset = end
            }
        }
        return frames
    }

    /** Encrypt a response header + optional body into frames. */
    fun encryptResponse(
        aesKey: ByteArray,
        status: Int,
        headers: Map<String, String>,
        body: ByteArray? = null,
        chunkSize: Int = 1024 * 1024,
    ): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val headerJson = """{"status":$status,"headers":${toJson(headers)}}"""
        frames.add(encryptFrame(aesKey, headerJson.toByteArray(Charsets.UTF_8)))

        if (body != null && body.isNotEmpty()) {
            var offset = 0
            while (offset < body.size) {
                val end = minOf(offset + chunkSize, body.size)
                frames.add(encryptFrame(aesKey, body.copyOfRange(offset, end)))
                offset = end
            }
        }
        return frames
    }

    data class DecryptedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray?,
    )

    /** Decrypt request frames back into method/path/headers/body. */
    fun decryptRequest(aesKey: ByteArray, frames: List<ByteArray>): DecryptedRequest {
        if (frames.isEmpty()) throw IllegalArgumentException("No frames")
        val headerJson = String(decryptFrame(aesKey, frames[0]), Charsets.UTF_8)
        val method = Regex(""""method"\s*:\s*"([^"]+)"""").find(headerJson)?.groupValues?.get(1) ?: "GET"
        val path = Regex(""""path"\s*:\s*"([^"]+)"""").find(headerJson)?.groupValues?.get(1) ?: "/"
        val headers = parseHeaders(headerJson)

        var body: ByteArray? = null
        if (frames.size > 1) {
            val chunks = (1 until frames.size).map { decryptFrame(aesKey, frames[it]) }
            body = concat(chunks)
        }
        return DecryptedRequest(method, path, headers, body)
    }

    data class DecryptedResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray?)

    fun decryptResponse(aesKey: ByteArray, frames: List<ByteArray>): DecryptedResponse {
        if (frames.isEmpty()) throw IllegalArgumentException("No frames")
        val headerJson = String(decryptFrame(aesKey, frames[0]), Charsets.UTF_8)
        val status = Regex(""""status"\s*:\s*(\d+)""").find(headerJson)?.groupValues?.get(1)?.toIntOrNull() ?: 200
        val headers = parseHeaders(headerJson)
        var body: ByteArray? = null
        if (frames.size > 1) {
            val chunks = (1 until frames.size).map { decryptFrame(aesKey, frames[it]) }
            body = concat(chunks)
        }
        return DecryptedResponse(status, headers, body)
    }

    /** Concatenate multiple byte arrays into one. */
    fun concat(arrays: List<ByteArray>): ByteArray {
        val total = arrays.sumOf { it.size }
        val result = ByteArray(total)
        var pos = 0
        for (a in arrays) {
            a.copyInto(result, pos)
            pos += a.size
        }
        return result
    }

    /** Split concatenated frames by the 4-byte length prefix. */
    fun splitFrames(data: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            if (offset + 4 > data.size) break
            val frameLen = ((data[offset].toInt() and 0xFF) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF) shl 16) or
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF)
            if (offset + 4 + frameLen > data.size) break
            frames.add(data.copyOfRange(offset, offset + 4 + frameLen))
            offset += 4 + frameLen
        }
        return frames
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun toJson(map: Map<String, String>): String =
        map.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }

    private fun parseHeaders(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val headersSection = Regex(""""headers"\s*:\s*\{([^}]*)\}""").find(json)?.groupValues?.get(1) ?: return result
        Regex(""""([^"]+)"\s*:\s*"([^"]*)"""").findAll(headersSection).forEach {
            result[it.groupValues[1]] = it.groupValues[2]
        }
        return result
    }

    /** HKDF-SHA256 (RFC 5869) — extracts and expands to `length` bytes. */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Extract
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Expand
        val result = ByteArray(length)
        val n = (length + 31) / 32
        var t = ByteArray(0)
        var pos = 0
        for (i in 1..n) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - pos)
            t.copyInto(result, pos, 0, copyLen)
            pos += copyLen
        }
        return result
    }
}
