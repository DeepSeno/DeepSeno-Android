package com.enmooy.deepseno.service

import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams LLM-corrected transcript text from the paired desktop.
 * POST /api/transcript/correct — same SSE envelope as /api/query-stream
 * ({"type":"chunk","text":...} / {"type":"done"} / {"type":"error","error":...})
 * but returns no sources. Mirrors SseClient.
 */
@Singleton
class TranscriptCorrectionClient @Inject constructor(
    okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // 30s read (inactivity) timeout to match the iOS contract — a silently
    // abandoned stream settles as failed quickly rather than hanging on the
    // shared client's 120s default. newBuilder() shares the pool/dispatcher.
    private val client = okHttpClient.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Streams corrected chunks for one segment. Throws on transport/HTTP/server error. */
    suspend fun stream(
        host: String, port: Int, token: String,
        segmentId: String, text: String, locale: String, context: List<String>,
        secure: Boolean = false,
        fingerprint: String? = null,
        onChunk: (String) -> Unit,
    ) {
        val bodyJson = buildJsonObject {
            put("segmentId", segmentId)
            put("text", text)
            put("locale", locale)
            putJsonArray("context") { context.forEach { add(it) } }
        }
        val scheme = if (secure) "https" else "http"
        val request = Request.Builder()
            .url("$scheme://$host:$port/api/transcript/correct")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(
                json.encodeToString(JsonObject.serializer(), bodyJson)
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        // Public endpoint: pin the desktop's self-signed cert (keeps the 30s read
        // timeout via newBuilder). LAN keeps the plain-HTTP 30s client.
        val callClient = if (secure && fingerprint != null) {
            TlsPinning.pinnedClient(client, host, fingerprint)
        } else {
            client
        }
        val response = callClient.newCall(request).await()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val body = response.body ?: throw Exception("Empty response")

        body.source().use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val jsonStr = line.removePrefix("data: ")
                try {
                    val obj = json.parseToJsonElement(jsonStr).jsonObject
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "chunk" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let(onChunk)
                        "done" -> return
                        "error" -> throw Exception(
                            obj["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                        )
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.serialization.SerializationException) throw e
                }
            }
        }
    }
}
