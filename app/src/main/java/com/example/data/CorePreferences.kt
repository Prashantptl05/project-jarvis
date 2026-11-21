package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.coreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_core_config"
)

data class CoreSettings(
    val overclockEnabled: Boolean = false,
    val voiceLanguage: String = "en-IN",
    val ttsEnabled: Boolean = true,
    val ttsSpeechRate: Float = 1.02f,
    val ttsPitch: Float = 0.92f,
    val commanderTitle: String = "Sir",
    val autoMemoryExtraction: Boolean = true
) {
    val activeModelId: String
        get() = if (overclockEnabled) MODEL_OVERCLOCK else MODEL_FAST_TACTICAL

    val activeModelLabel: String
        get() = if (overclockEnabled) "OVERCLOCK // GEMINI 4" else "TACTICAL // FLASH 3.8"

    companion object {
        const val MODEL_FAST_TACTICAL = "gemini-3.8-flash"
        const val MODEL_OVERCLOCK = "gemini-4-argon"
    }
}

class CorePreferences(private val context: Context) {

    private object Keys {
        val OVERCLOCK_ENABLED = booleanPreferencesKey("overclock_enabled")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val COMMANDER_TITLE = stringPreferencesKey("commander_title")
        val AUTO_MEMORY_EXTRACTION = booleanPreferencesKey("auto_memory_extraction")
    }

    val settingsFlow: Flow<CoreSettings> = context.coreDataStore.data.map { prefs ->
        CoreSettings(
            overclockEnabled = prefs[Keys.OVERCLOCK_ENABLED] ?: false,
            voiceLanguage = prefs[Keys.VOICE_LANGUAGE] ?: "en-IN",
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: 1.02f,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 0.92f,
            commanderTitle = prefs[Keys.COMMANDER_TITLE]?.ifBlank { "Sir" } ?: "Sir",
            autoMemoryExtraction = prefs[Keys.AUTO_MEMORY_EXTRACTION] ?: true
        )
    }

    suspend fun setOverclockEnabled(enabled: Boolean) {
        context.coreDataStore.edit { it[Keys.OVERCLOCK_ENABLED] = enabled }
    }

    suspend fun setVoiceLanguage(languageTag: String) {
        context.coreDataStore.edit { it[Keys.VOICE_LANGUAGE] = languageTag }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.coreDataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        context.coreDataStore.edit { it[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setTtsPitch(pitch: Float) {
        context.coreDataStore.edit { it[Keys.TTS_PITCH] = pitch.coerceIn(0.6f, 1.4f) }
    }

    suspend fun setCommanderTitle(title: String) {
        context.coreDataStore.edit { it[Keys.COMMANDER_TITLE] = title.trim().ifBlank { "Sir" } }
    }

    suspend fun setAutoMemoryExtraction(enabled: Boolean) {
        context.coreDataStore.edit { it[Keys.AUTO_MEMORY_EXTRACTION] = enabled }
    }
}
