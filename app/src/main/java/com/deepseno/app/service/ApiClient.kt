package com.enmooy.deepseno.service

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.enmooy.deepseno.data.remote.api.DeepSenoApi
import com.enmooy.deepseno.data.remote.model.UploadResponse
import com.enmooy.deepseno.service.relay.RelayCrypto
import com.enmooy.deepseno.service.relay.RelayTunnel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApiClient"

class ApiException(
    val statusCode: Int,
    val responseBody: String?,
    message: String = "HTTP $statusCode",
) : IOException(message)

@Singleton
class ApiClient @Inject constructor(
    private val okHttpClientBase: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val authInterceptor = Interceptor { chain ->
        val t = token
        val req = if (t.isNotEmpty()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $t")
                .build()
        } else chain.request()
        chain.proceed(req)
    }

    @Volatile private var relayMode = false
    private var relayServerUrl: String = ""
    private var relayMachineId: String = ""
    private var relayAesKey: ByteArray? = null
    @Volatile private var relayTunnel: RelayTunnel? = null

    private val relayInterceptor = Interceptor { chain ->
        if (!relayMode) return@Interceptor chain.proceed(chain.request())
        val key = relayAesKey ?: throw IOException("relay AES key not set")
        val tunnel = relayTunnel ?: throw IOException("relay tunnel not connected")
        val original = chain.request()

        val method = original.method
        val path = original.url.encodedPath + (original.url.encodedQuery?.let { "?$it" } ?: "")
        // Real HTTP servers normalize header names case-insensitively. Our encrypted
        // proxy dispatches directly into Express, so preserve that behavior explicitly;
        // otherwise X-Filename is missed and the desktop falls back to a .wav name.
        val headers = mutableMapOf<String, String>()
        original.headers.forEach { (k, v) -> headers[k.lowercase(Locale.ROOT)] = v }
        if (token.isNotEmpty()) headers["authorization"] = "Bearer $token"

        val body: ByteArray? = if (original.body != null) {
            val buffer = okio.Buffer(); original.body!!.writeTo(buffer); buffer.readByteArray()
        } else null

        val frames = RelayCrypto.encryptRequest(key, method, path, headers, body)

        // Send through WebSocket tunnel instead of HTTP POST
        val proxyResp = runBlocking { tunnel.sendProxyRequest(frames) }
        if (proxyResp.error != null) {
            throw ApiException(0, null, "Relay proxy error: ${proxyResp.error}")
        }
        if (proxyResp.frames.isEmpty()) {
            return@Interceptor Response.Builder()
                .request(original).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(null, ByteArray(0))).build()
        }

        val decrypted = RelayCrypto.decryptResponse(key, proxyResp.frames)
        val respBody = decrypted.body ?: ByteArray(0)
        val ct = decrypted.headers["content-type"] ?: "application/octet-stream"

        Response.Builder()
            .request(original).protocol(Protocol.HTTP_1_1)
            .code(decrypted.status).message(if (decrypted.status in 200..299) "OK" else "Error")
            .header("Content-Type", ct)
            .body(ResponseBody.create(ct.toMediaType(), respBody))
            .build()
    }

    fun configureRelay(
        serverBaseUrl: String,
        machineId: String,
        aesKey: ByteArray,
        lanToken: String,
        tunnel: RelayTunnel? = null,
    ) {
        this.token = lanToken
        this.relayMode = true
        this.relayServerUrl = serverBaseUrl
        this.relayMachineId = machineId
        this.relayAesKey = aesKey
        this.relayTunnel = tunnel
        this.baseUrl = serverBaseUrl.replace("/api/v1", "/")

        authClient = okHttpClientBase.newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(relayInterceptor)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(DeepSenoApi::class.java)

    }

    private fun buildAuthClient(secure: Boolean, host: String, fingerprint: String?): OkHttpClient {
        val base = if (secure && fingerprint != null) {
            TlsPinning.pinnedClient(okHttpClientBase, host, fingerprint)
        } else okHttpClientBase
        return base.newBuilder().addInterceptor(authInterceptor).build()
    }

    private var authClient: OkHttpClient = buildAuthClient(secure = false, host = "", fingerprint = null)

    @Volatile var api: DeepSenoApi? = null
        private set
    var baseUrl: String = ""
        private set
    @Volatile var token: String = ""
        private set

    fun configure(host: String, port: Int, token: String, secure: Boolean = false, fingerprint: String? = null) {
        relayMode = false
        Log.d(TAG, "Configure LAN: $host:$port")
        this.token = token
        this.baseUrl = "http${if (secure) "s" else ""}://$host:$port/"
        authClient = buildAuthClient(secure, host, fingerprint)
        api = Retrofit.Builder().baseUrl(baseUrl).client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(DeepSenoApi::class.java)
    }

    /** Configure LAN mode using WebSocket proxy-req/resp (unified protocol with relay). */
    fun configureLan(tunnel: RelayTunnel, aesKey: ByteArray, host: String, port: Int, token: String) {
        this.token = token
        this.relayMode = true
        this.relayAesKey = aesKey
        this.relayTunnel = tunnel
        this.baseUrl = "http://$host:$port/"

        authClient = okHttpClientBase.newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(relayInterceptor)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(DeepSenoApi::class.java)
    }

    fun clear() { relayMode = false; api = null; baseUrl = ""; token = ""; relayTunnel = null }

    private fun Response.successBodyString(): String {
        if (!isSuccessful) {
            val snippet = try { body?.string()?.take(500) } catch (_: Throwable) { null }
            throw ApiException(code, snippet, "HTTP $code ${message.ifEmpty { "" }}".trim())
        }
        return body?.string() ?: throw ApiException(code, null, "Empty response body")
    }

    fun imageUrl(id: Int, index: Int = 0): String = "${baseUrl}api/recordings/$id/image/$index"
    fun mediaUrl(id: Int): String = "${baseUrl}api/recordings/$id/media?token=$token"

    suspend fun upload(file: File, fileName: String, bookmarksJson: String? = null): UploadResponse {
        val url = "${baseUrl}api/upload"
        val body = file.asRequestBody(mimeType(fileName).toMediaType())
        val builder = Request.Builder().url(url).addHeader("X-Filename", URLEncoder.encode(fileName, "UTF-8")).post(body)
        if (bookmarksJson != null) builder.addHeader("X-Bookmarks", bookmarksJson)
        val resp = authClient.newCall(builder.build()).await()
        return json.decodeFromString(resp.successBodyString())
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "mp3" -> "audio/mpeg"
        "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "heic" -> "image/heic"
        "txt" -> "text/plain"; "doc", "docx" -> "application/msword"
        else -> "application/octet-stream"
    }

    suspend fun uploadImages(files: List<File>, fileNames: List<String>, groupName: String): UploadResponse {
        val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
        files.forEachIndexed { i, f -> mp.addFormDataPart("files", if (i < fileNames.size) fileNames[i] else f.name, f.asRequestBody("image/jpeg".toMediaType())) }
        val req = Request.Builder().url("${baseUrl}api/upload-multi").addHeader("X-Group-Name", groupName).post(mp.build()).build()
        return json.decodeFromString(authClient.newCall(req).await().successBodyString())
    }
}

suspend fun Call.await(): Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        override fun onResponse(call: Call, resp: Response) { cont.resumeWith(Result.success(resp)) }
    })
}
