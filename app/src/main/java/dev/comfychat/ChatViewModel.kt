package dev.comfychat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

private fun JsonObject.patch(node: String, input: String, value: JsonElement): JsonObject {
    val n = this[node]?.jsonObject ?: error("В workflow нет ноды $node")
    val inputs = n["inputs"]?.jsonObject ?: error("У ноды $node нет inputs")
    val newNode = JsonObject(n + ("inputs" to JsonObject(inputs + (input to value))))
    return JsonObject(this + (node to newNode))
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)

    val serverUrl = settings.url.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val themeMode = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)

    val turns = mutableStateListOf<Turn>()
    var size by mutableStateOf(SizeOption.WIDE)
        private set

    private var nextId = 0L
    private var job: Job? = null
    private var activeClient: ComfyClient? = null

    private val template: JsonObject by lazy {
        getApplication<Application>().assets.open("workflow.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
    }

    fun cycleSize() {
        val all = SizeOption.entries
        size = all[(size.ordinal + 1) % all.size]
    }

    fun saveSettings(url: String, mode: ThemeMode) {
        viewModelScope.launch {
            settings.setUrl(url)
            settings.setTheme(mode)
        }
    }

    fun stop() {
        val c = activeClient ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { c.interrupt() } }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || job?.isActive == true) return
        val id = nextId++
        val sizeOpt = size
        turns.add(Turn(id = id, prompt = text, size = sizeOpt))

        job = viewModelScope.launch {
            val client = ComfyClient(settings.url.first())
            activeClient = client
            try {
                val wf = template
                    .patch(Wf.PROMPT, "value", JsonPrimitive(text))
                    .patch(Wf.SAMPLER, "seed", JsonPrimitive(Random.nextLong() ushr 14))
                    .patch(Wf.LATENT, "width", JsonPrimitive(sizeOpt.w))
                    .patch(Wf.LATENT, "height", JsonPrimitive(sizeOpt.h))

                client.generate(wf).collect { ev ->
                    when (ev) {
                        is GenEvent.Stage -> update(id) { it.copy(stage = ev.text) }
                        is GenEvent.Progress -> update(id) { it.copy(percent = ev.value * 100 / ev.max) }
                        is GenEvent.Preview -> update(id) { it.copy(preview = ev.bitmap) }
                        is GenEvent.Tags -> update(id) { it.copy(tags = ev.text) }
                        is GenEvent.Done -> {
                            val bmp = ev.images.firstOrNull()?.let { client.fetchImage(it) }
                            update(id) {
                                it.copy(
                                    result = bmp,
                                    preview = null,
                                    running = false,
                                    error = if (bmp == null) "Картинка не пришла" else null
                                )
                            }
                        }
                        is GenEvent.Failed -> update(id) { it.copy(error = ev.message, running = false) }
                    }
                }
            } catch (e: CancellationException) {
                update(id) { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                update(id) { it.copy(error = e.message ?: e.toString(), running = false) }
            } finally {
                update(id) { if (it.running) it.copy(running = false) else it }
            }
        }
    }

    private fun update(id: Long, f: (Turn) -> Turn) {
        val i = turns.indexOfFirst { it.id == id }
        if (i >= 0) turns[i] = f(turns[i])
    }
}
