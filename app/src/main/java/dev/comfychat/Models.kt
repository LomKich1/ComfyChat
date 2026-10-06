package dev.comfychat

import android.graphics.Bitmap
import java.io.File

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class SizeOption(val label: String, val w: Int, val h: Int) {
    WIDE("2:1 · 2048×1024", 2048, 1024),
    SQUARE("1:1 · 1024×1024", 1024, 1024),
    PORTRAIT("2:3 · 832×1216", 832, 1216),
    LANDSCAPE("3:2 · 1216×832", 1216, 832)
}

/** ID нод в workflow.json (API-формат). Поменяешь workflow — поправь тут. */
object Wf {
    const val TRANSLATOR = "1"   // RuDanbooruTags
    const val PROMPT = "2"       // PrimitiveStringMultiline: сюда идёт русский текст
    const val TAGS_PREVIEW = "4" // PreviewAny с итоговыми тегами
    const val SAMPLER = "7"      // KSampler
    const val OUTPUT = "8"       // PreviewImage: финальная картинка
    const val CHECKPOINT = "6"   // CheckpointLoaderSimple: модель
    const val LATENT = "9"       // EmptyLatentImage: размер
    const val DECODE = "11"      // VAEDecode
}

data class ImageRef(val filename: String, val subfolder: String, val type: String)

sealed interface GenEvent {
    data class Stage(val text: String) : GenEvent
    data class Progress(val value: Int, val max: Int) : GenEvent
    class Preview(val bitmap: Bitmap) : GenEvent
    data class Tags(val text: String) : GenEvent
    data class Done(val images: List<ImageRef>) : GenEvent
    data class Failed(val message: String) : GenEvent
}

data class Turn(
    val id: Long,
    val prompt: String,
    val size: SizeOption,
    val stage: String = "Отправляю…",
    val percent: Int? = null,
    val preview: Bitmap? = null,
    val tags: String? = null,
    val file: File? = null,   // сохранённая картинка (история)
    val error: String? = null,
    val running: Boolean = true
)
