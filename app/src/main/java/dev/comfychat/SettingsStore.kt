package dev.comfychat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Приводит введённый адрес к виду схема://хост[:порт]: добавляет схему (https для trycloudflare,
 * иначе http), порт 8188 для голого IP и выкидывает путь, если вставили ссылку целиком.
 */
fun normalizeUrl(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return s
    if (!s.contains("://")) {
        s = (if (s.substringBefore('/').endsWith(".trycloudflare.com")) "https://" else "http://") + s
    }
    val scheme = s.substringBefore("://").lowercase()
    val host = s.substringAfter("://").substringBefore('/').substringBefore('?').trim()
    if (host.isEmpty()) return s.trimEnd('/')
    val withPort = if (scheme == "http" && !host.contains(':')) "$host:8188" else host
    return "$scheme://$withPort"
}

class SettingsStore(private val ctx: Context) {
    private val urlKey = stringPreferencesKey("url")
    private val themeKey = stringPreferencesKey("theme")
    private val seedFixedKey = booleanPreferencesKey("seed_fixed")
    private val seedKey = stringPreferencesKey("seed")

    val url: Flow<String> = ctx.dataStore.data.map { it[urlKey] ?: "http://192.168.0.10:8188" }

    val theme: Flow<ThemeMode> = ctx.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[themeKey] ?: "") }.getOrDefault(ThemeMode.AUTO)
    }

    val seedFixed: Flow<Boolean> = ctx.dataStore.data.map { it[seedFixedKey] ?: false }
    val seed: Flow<String> = ctx.dataStore.data.map { it[seedKey] ?: "" }

    suspend fun setUrl(v: String) {
        ctx.dataStore.edit { it[urlKey] = normalizeUrl(v) }
    }

    suspend fun setTheme(m: ThemeMode) {
        ctx.dataStore.edit { it[themeKey] = m.name }
    }

    suspend fun setSeed(fixed: Boolean, value: String) {
        ctx.dataStore.edit {
            it[seedFixedKey] = fixed
            it[seedKey] = value
        }
    }
}
