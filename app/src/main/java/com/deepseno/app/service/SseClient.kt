package com.enmooy.deepseno.service

import com.enmooy.deepseno.data.remote.model.Source
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun queryStream(
        host: String, port: Int, token: String,
        question: String, sessionId: Int?,
        onChunk: (String) -> Unit,
        onStatus: (String) -> Unit,
        secure: Boolean = false,
        fingerprint: String? = null,
    ): List<Source> {
        val bodyJson = buildJsonObject {
            put("question", question)
            sessionId?.let { put("sessionId", it) }
        }
        val scheme = if (secure) "https" else "http"
        val request = Request.Builder()
            .url("$scheme://$host:$port/api/query-stream")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), bodyJson).toRequestBody("application/json".toMediaType()))
            .build()

        // Public endpoint: pin the desktop's self-signed cert. LAN keeps plain HTTP.
        val client = if (secure && fingerprint != null) {
            TlsPinning.pinnedClient(okHttpClient, host, fingerprint)
        } else {
            okHttpClient
        }
        val response = client.newCall(request).await()
        val body = response.body ?: throw Exception("Empty response")
        var sources = listOf<Source>()

        body.source().use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val jsonStr = line.removePrefix("data: ")
                try {
                    val obj = json.parseToJsonElement(jsonStr).jsonObject
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "chunk" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let(onChunk)
                        "status" -> obj["status"]?.jsonPrimitive?.contentOrNull?.let(onStatus)
                        "done" -> {
                            obj["sources"]?.let { s ->
                                sources = json.decodeFromJsonElement(s)
                            }
                        }
                        "error" -> {
                            val msg = obj["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                            throw Exception(msg)
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.startsWith("Unknown error") != true &&
                        e !is kotlinx.serialization.SerializationException) throw e
                }
            }
        }
        return sources
    }
}
