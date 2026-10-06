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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

private fun JsonObject.patch(node: String, input: String, value: JsonElement): JsonObject {
    val n = this[node]?.jsonObject ?: error("В workflow нет ноды $node")
    val inputs = n["inputs"]?.jsonObject ?: error("У ноды $node нет inputs")
    val newNode = JsonObject(n + ("inputs" to JsonObject(inputs + (input to value))))
    return JsonObject(this + (node to newNode))
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val store = HistoryStore(app)
    private val saveLock = Mutex()

    val serverUrl = settings.url.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val themeMode = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)
    val ckpt = settings.ckpt.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val seedFixed = settings.seedFixed.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val seedValue = settings.seed.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val turns = mutableStateListOf<Turn>()
    var size by mutableStateOf(Size.DEFAULT)
        private set
    val recentSizes = settings.recentSizes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ключи уже проигранных анимаций появления (чтобы не повторять при скролле). */
    val animated: MutableSet<String> = mutableSetOf()

    // id по времени: уникальны и не пересекаются с восстановленными из истории
    private var nextId = System.currentTimeMillis()
    private var job: Job? = null
    private var activeClient: ComfyClient? = null

    private val template: JsonObject by lazy {
        getApplication<Application>().assets.open("workflow.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
    }

    /** Модель, прописанная в workflow.json: пока ничего не выбрано, берётся она. */
    val defaultCkpt: String by lazy {
        runCatching {
            template[Wf.CHECKPOINT]!!.jsonObject["inputs"]!!.jsonObject["ckpt_name"]!!.jsonPrimitive.content
        }.getOrDefault("")
    }

    init {
        viewModelScope.launch { size = settings.size.first() }
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { store.load() }
            val restored = items.mapNotNull { s ->
                val f = store.fileOf(s.file)
                if (!f.exists()) null else Turn(
                    id = s.id,
                    prompt = s.prompt,
                    size = Size.parse(s.size) ?: Size.DEFAULT,
                    stage = "",
                    tags = s.tags,
                    file = f,
                    running = false
                )
            }
            restored.forEach {
                animated.add("u${it.id}")
                animated.add("b${it.id}")
            }
            turns.addAll(0, restored)
            nextId = maxOf(nextId, (restored.maxOfOrNull { it.id } ?: 0L) + 1)
        }
    }

    fun selectSize(s: Size) {
        size = s
        viewModelScope.launch {
            settings.setSize(s)
            if (Size.PRESETS.none { it.size == s }) settings.rememberCustomSize(s)
        }
    }

    fun setCkpt(name: String) {
        viewModelScope.launch { settings.setCkpt(name) }
    }

    /** Список чекпоинтов с сервера по указанному адресу; null, если не достучались. */
    suspend fun scanCheckpoints(url: String): List<String>? = withContext(Dispatchers.IO) {
        runCatching { ComfyClient(url).listCheckpoints().sortedBy { it.lowercase() } }.getOrNull()
    }

    fun saveSettings(url: String, mode: ThemeMode, seedFixed: Boolean, seed: String) {
        viewModelScope.launch {
            settings.setUrl(url)
            settings.setTheme(mode)
            settings.setSeed(seedFixed, seed)
        }
    }

    /** Останавливает локально и сразу (не ждём сервер), а interrupt шлём в фоне «по возможности». */
    fun stop() {
        val c = activeClient
        job?.cancel()
        if (c != null) viewModelScope.launch(Dispatchers.IO) { runCatching { c.interrupt() } }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || job?.isActive == true) return
        val id = nextId++
        val sizeOpt = size
        turns.add(Turn(id = id, prompt = text, size = sizeOpt))
        startGeneration(id, text, sizeOpt)
    }

    /** Повтор неудавшейся генерации в том же сообщении. */
    fun retry(id: Long) {
        if (job?.isActive == true) return
        val t = turns.firstOrNull { it.id == id } ?: return
        update(id) { Turn(id = it.id, prompt = it.prompt, size = it.size) }
        startGeneration(id, t.prompt, t.size)
    }

    /** Удаляет сообщение вместе с картинкой (и из галереи, и с диска). */
    fun delete(id: Long) {
        if (removeTurn(id)) persist()
    }

    fun deleteMany(ids: Set<Long>) {
        var changed = false
        ids.forEach { if (removeTurn(it)) changed = true }
        if (changed) persist()
    }

    private fun removeTurn(id: Long): Boolean {
        val i = turns.indexOfFirst { it.id == id }
        if (i < 0) return false
        val t = turns[i]
        if (t.running) stop()
        turns.removeAt(i)
        animated.remove("u$id")
        animated.remove("b$id")
        t.file?.let { f ->
            Thumbs.evict(f.name)
            viewModelScope.launch(Dispatchers.IO) { f.delete() }
        }
        return true
    }

    private fun persist() {
        val snapshot = turns.mapNotNull { t ->
            t.file?.let { Saved(t.id, t.prompt, t.tags, it.name, t.size.key) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { runCatching { store.save(snapshot) } }
        }
    }

    private fun startGeneration(id: Long, text: String, sizeOpt: Size) {
        job = viewModelScope.launch {
            activeClient = null
            try {
                var url = settings.url.first()
                // адрес в локалке мог поменяться (хотспот выдал другой IP): ищем ПК заново
                if (LanDiscovery.isLanUrl(url) && !LanDiscovery.ping(url)) {
                    update(id) { it.copy(stage = "Ищу ПК в сети…") }
                    LanDiscovery.find(LanDiscovery.portOf(url))?.let { found ->
                        url = found
                        settings.setUrl(found)
                    }
                    update(id) { it.copy(stage = "Отправляю…") }
                }
                val client = ComfyClient(url)
                activeClient = client
                val fixed = settings.seedFixed.first()
                val fixedSeed = settings.seed.first().toLongOrNull()
                val seed = if (fixed && fixedSeed != null) fixedSeed else Random.nextLong() ushr 14
                var wf = template
                    .patch(Wf.PROMPT, "value", JsonPrimitive(text))
                    .patch(Wf.SAMPLER, "seed", JsonPrimitive(seed))
                    .patch(Wf.LATENT, "width", JsonPrimitive(sizeOpt.w))
                    .patch(Wf.LATENT, "height", JsonPrimitive(sizeOpt.h))
                val ck = settings.ckpt.first()
                if (ck.isNotBlank()) wf = wf.patch(Wf.CHECKPOINT, "ckpt_name", JsonPrimitive(ck))

                client.generate(wf).collect { ev ->
                    when (ev) {
                        is GenEvent.Stage -> update(id) { it.copy(stage = ev.text) }
                        is GenEvent.Progress -> update(id) { it.copy(percent = ev.value * 100 / ev.max) }
                        is GenEvent.Preview -> update(id) { it.copy(preview = ev.bitmap) }
                        is GenEvent.Tags -> update(id) { it.copy(tags = ev.text) }
                        is GenEvent.Done -> {
                            update(id) { it.copy(stage = "Загружаю картинку…") }
                            val bytes = ev.images.firstOrNull()?.let { client.fetchImageBytes(it) }
                            val file = bytes?.let {
                                withContext(Dispatchers.IO) {
                                    // сохраняем оригинал и сразу прогреваем кэш, чтобы не мигало
                                    store.write(it).also { f -> Thumbs.load(f, 1280) }
                                }
                            }
                            update(id) {
                                it.copy(
                                    file = file,
                                    preview = if (file == null) it.preview else null,
                                    running = false,
                                    error = if (file == null) "Не удалось получить картинку" else null
                                )
                            }
                            if (file != null) persist()
                        }
                        is GenEvent.Failed -> update(id) { it.copy(error = ev.message, running = false) }
                    }
                }
            } catch (e: CancellationException) {
                update(id) { it.copy(running = false, error = it.error ?: "Остановлено") }
                throw e
            } catch (e: Exception) {
                update(id) { it.copy(error = e.message ?: e.toString(), running = false) }
            } finally {
                // что бы ни случилось, интерфейс не должен остаться «в процессе»
                update(id) { if (it.running) it.copy(running = false) else it }
            }
        }
    }

    private fun update(id: Long, f: (Turn) -> Turn) {
        val i = turns.indexOfFirst { it.id == id }
        if (i >= 0) turns[i] = f(turns[i])
    }
}
