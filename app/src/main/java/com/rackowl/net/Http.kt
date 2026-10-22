package com.rackowl.net

import com.rackowl.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpResponse(val code: Int, val body: String, val headers: Map<String, List<String>>, val latencyMs: Long) {
    val ok get() = code in 200..299
    fun json(): JsonElement = AppJson.parseToJsonElement(body)
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
}

class HttpException(val code: Int, message: String) : IOException(message)

val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
val FORM_MEDIA = "application/x-www-form-urlencoded".toMediaType()

/** Un échange enregistré pour la vue de diagnostic, secrets masqués. */
data class Exchange(
    val method: String, val url: String, val requestHeaders: Map<String, String>,
    val code: Int?, val ms: Long, val responseHeaders: Map<String, String>, val body: String, val error: String?,
)

private val SECRET_PARAM = Regex("([?&](?:api_?key|apikey|token|key|passwd|password|secret|auth|sid|_sid)=)[^&]*", RegexOption.IGNORE_CASE)
private val SECRET_HEADER = Regex("authorization|cookie|set-cookie|.*api[-_]?key.*|.*token.*|.*secret.*|.*sid.*|password|key", RegexOption.IGNORE_CASE)

/** Masque les secrets d'une URL (paramètres de requête) et d'un corps JSON (champs « password », « token »…). */
fun maskSecrets(text: String): String =
    text.replace(SECRET_PARAM, "$1•••")
        .replace(Regex("(\"(?:password|passwd|token|apiKey|api_key|secret|access_token|sid)\"\\s*:\\s*\")[^\"]*", RegexOption.IGNORE_CASE), "$1•••")

fun maskHeaders(h: Map<String, String>) = h.mapValues { (k, v) -> if (SECRET_HEADER.matches(k)) "•••" else v }

/** Client HTTP partagé. [insecure] accepte les certificats auto-signés (réglage explicite). */
class Http(insecure: Boolean) {
    /** Si défini, chaque requête y est rapportée (vue de diagnostic). */
    var recorder: ((Exchange) -> Unit)? = null

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .apply {
            if (insecure) {
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
                sslSocketFactory(ssl.socketFactory, trustAll)
                hostnameVerifier { _, _ -> true }
            }
        }
        .build()

    /** Client sans délai de lecture, pour les flux (SSE Dozzle). */
    val streaming: OkHttpClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            method(method, body ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(null) else null)
        }.build()
        val start = System.nanoTime()
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ms = (System.nanoTime() - start) / 1_000_000
                recorder?.invoke(
                    Exchange(
                        method, maskSecrets(url), maskHeaders(headers), resp.code, ms,
                        maskHeaders(resp.headers.toMultimap().mapValues { it.value.joinToString(", ") }), maskSecrets(text.take(4000)), null,
                    ),
                )
                HttpResponse(resp.code, text, resp.headers.toMultimap(), ms)
            }
        } catch (e: IOException) {
            recorder?.invoke(Exchange(method, maskSecrets(url), maskHeaders(headers), null, (System.nanoTime() - start) / 1_000_000, emptyMap(), "", e.toString()))
            throw e
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()) = request(url, headers = headers)

    /** GET qui exige une réponse 2xx et renvoie le JSON. */
    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val r = get(url, headers)
        if (!r.ok) throw HttpException(r.code, "HTTP ${r.code}")
        return r.json()
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        request(url, "POST", headers, json.toRequestBody(JSON_MEDIA))

    companion object {
        fun basic(user: String, password: String) = Credentials.basic(user, password)
    }
}

/** Joint une URL de base et un chemin sans doubler les « / ». */
fun join(base: String, path: String): String = base.trimEnd('/') + "/" + path.trimStart('/')

// ---- Navigation JSON tolérante ----

operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()
val JsonElement?.obj: Map<String, JsonElement> get() = (this as? JsonObject) ?: emptyMap()
val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
val JsonElement?.long: Long? get() = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.content.toLongOrNull() }
val JsonElement?.double: Double? get() = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.booleanOrNull
