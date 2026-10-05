package dev.comfychat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ComfyClient(private val baseUrl: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val clientId = UUID.randomUUID().toString()

    fun generate(workflow: JsonObject): Flow<GenEvent> = callbackFlow {
        val opened = CompletableDeferred<Unit>()
        val images = mutableListOf<ImageRef>()
        val finished = AtomicBoolean(false)

        fun finish(ev: GenEvent) {
            if (finished.compareAndSet(false, true)) {
                trySend(ev)
                close()
            }
        }

        fun stageFor(node: String): String? = when (node) {
            Wf.TRANSLATOR -> "Перевожу описание в теги…"
            Wf.SAMPLER -> "Генерирую…"
            Wf.DECODE -> "Декодирую…"
            else -> null
        }

        fun handleText(text: String) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val type = msg["type"]?.jsonPrimitive?.contentOrNull
            val data = msg["data"] as? JsonObject ?: return
            when (type) {
                "executing" -> {
                    val node = data["node"]?.jsonPrimitive?.contentOrNull
                    if (node == null) finish(GenEvent.Done(images.toList()))
                    else stageFor(node)?.let { trySend(GenEvent.Stage(it)) }
                }
                "progress" -> {
                    val node = data["node"]?.jsonPrimitive?.contentOrNull
                    val v = data["value"]?.jsonPrimitive?.intOrNull ?: return
                    val m = data["max"]?.jsonPrimitive?.intOrNull ?: return
                    if (m > 0 && (node == null || node == Wf.SAMPLER)) trySend(GenEvent.Progress(v, m))
                }
                "executed" -> {
                    val node = data["node"]?.jsonPrimitive?.contentOrNull
                    val out = data["output"] as? JsonObject
                    if (node == Wf.TAGS_PREVIEW) {
                        val t = (out?.get("text") as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
                        if (t != null) trySend(GenEvent.Tags(t))
                    }
                    if (node == Wf.OUTPUT) {
                        (out?.get("images") as? JsonArray)?.forEach { el ->
                            val o = el.jsonObject
                            val name = o["filename"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            images += ImageRef(
                                name,
                                o["subfolder"]?.jsonPrimitive?.contentOrNull ?: "",
                                o["type"]?.jsonPrimitive?.contentOrNull ?: "temp"
                            )
                        }
                    }
                }
                "execution_success" -> finish(GenEvent.Done(images.toList()))
                "execution_interrupted" -> finish(GenEvent.Failed("Остановлено"))
                "execution_error" -> {
                    val node = data["node_type"]?.jsonPrimitive?.contentOrNull ?: "?"
                    val m = data["exception_message"]?.jsonPrimitive?.contentOrNull ?: "неизвестная ошибка"
                    finish(GenEvent.Failed("Ошибка в ноде $node: ${m.trim()}"))
                }
            }
        }

        // Бинарные сообщения (big-endian): [1][формат:4][картинка]
        // или [4][длина метаданных:4][json][картинка].
        fun handlePreview(bytes: ByteString) {
            val b = bytes.toByteArray()
            if (b.size < 8) return
            val bb = ByteBuffer.wrap(b)
            val offset = when (bb.getInt(0)) {
                1 -> 8
                4 -> 8 + bb.getInt(4)
                else -> return
            }
            if (offset >= b.size) return
            val bmp = BitmapFactory.decodeByteArray(b, offset, b.size - offset) ?: return
            trySend(GenEvent.Preview(bmp))
        }

        val wsUrl = baseUrl.replaceFirst("http", "ws") + "/ws?clientId=$clientId"
        val ws = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handleText(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handlePreview(bytes)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                finish(GenEvent.Failed("Соединение потеряно: ${t.message}"))
            }
        })

        launch(Dispatchers.IO) {
            try {
                opened.await()
                val body = buildJsonObject {
                    put("prompt", workflow)
                    put("client_id", clientId)
                }.toString().toRequestBody("application/json".toMediaType())
                http.newCall(Request.Builder().url("$baseUrl/prompt").post(body).build()).execute().use { r ->
                    if (!r.isSuccessful) {
                        finish(GenEvent.Failed("ComfyUI отклонил workflow (${r.code}): ${r.body?.string()?.take(400)}"))
                    }
                }
            } catch (e: Exception) {
                finish(GenEvent.Failed("Не достучался до $baseUrl: ${e.message}"))
            }
        }

        awaitClose { ws.cancel() }
    }

    suspend fun fetchImage(ref: ImageRef): Bitmap? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/view".toHttpUrl().newBuilder()
            .addQueryParameter("filename", ref.filename)
            .addQueryParameter("subfolder", ref.subfolder)
            .addQueryParameter("type", ref.type)
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return@use null
            r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }

    fun interrupt() {
        http.newCall(Request.Builder().url("$baseUrl/interrupt").post("".toRequestBody()).build())
            .execute().close()
    }
}
