package dev.comfychat

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val ctx: Context) {
    private val urlKey = stringPreferencesKey("url")
    private val themeKey = stringPreferencesKey("theme")

    val url: Flow<String> = ctx.dataStore.data.map { it[urlKey] ?: "http://192.168.0.10:8188" }

    val theme: Flow<ThemeMode> = ctx.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[themeKey] ?: "") }.getOrDefault(ThemeMode.AUTO)
    }

    suspend fun setUrl(v: String) {
        ctx.dataStore.edit { it[urlKey] = v.trim().trimEnd('/') }
    }

    suspend fun setTheme(m: ThemeMode) {
        ctx.dataStore.edit { it[themeKey] = m.name }
    }
}
