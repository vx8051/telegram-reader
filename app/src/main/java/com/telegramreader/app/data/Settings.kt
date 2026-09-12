package com.telegramreader.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** Everything the user configures. Persisted in SharedPreferences, exposed as a StateFlow snapshot. */
data class Prefs(
    val apiId: Int = 0,
    val apiHash: String = "",
    /** Chat ids of the channels to read aloud. */
    val channelIds: Set<Long> = emptySet(),
    /** TTS engine package name (e.g. com.github.olga_yakovleva.rhvoice.android); empty = system default. */
    val ttsEngine: String = "",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    /** Comma-separated BCP-47 tags in preference order; empty = device default. With several, each post's language is detected. */
    val voiceLanguages: String = "",
    val announceChannelName: Boolean = true,
    /** Silence queued before each message so Bluetooth/car audio links wake up before speech starts. */
    val leadInMs: Int = 700,
    /** Apply the lead-in only when a Bluetooth / USB / car output is connected. */
    val leadInOnlyExternal: Boolean = true,
    /** Play a two-way-radio squelch/chirp before each post. */
    val radioCue: Boolean = false,
    /** Speak posts that arrived while the app was offline (TDLib delivers them in a burst on reconnect). */
    val readMissedMessages: Boolean = false,
    /** Mark a post as read in Telegram once it has been spoken in full. */
    val markAsRead: Boolean = false,
    /** Restart the reader after device reboot. */
    val autoStartOnBoot: Boolean = true,
) {
    val hasCredentials get() = apiId != 0 && apiHash.isNotBlank()

    val voiceLocales: List<Locale>
        get() = voiceLanguages.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            .map { Locale.forLanguageTag(it) }.filter { it.language.isNotEmpty() }
}

class Settings(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    private fun load() = Prefs(
        apiId = sp.getInt(K_API_ID, 0),
        apiHash = sp.getString(K_API_HASH, "") ?: "",
        channelIds = (sp.getStringSet(K_CHANNELS, emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }.toSet(),
        ttsEngine = sp.getString(K_ENGINE, "") ?: "",
        speechRate = sp.getFloat(K_RATE, 1.0f),
        speechPitch = sp.getFloat(K_PITCH, 1.0f),
        voiceLanguages = sp.getString(K_LANG, "") ?: "",
        announceChannelName = sp.getBoolean(K_ANNOUNCE, true),
        leadInMs = sp.getInt(K_LEAD_IN, 700),
        leadInOnlyExternal = sp.getBoolean(K_LEAD_IN_EXT, true),
        radioCue = sp.getBoolean(K_RADIO_CUE, false),
        readMissedMessages = sp.getBoolean(K_MISSED, false),
        autoStartOnBoot = sp.getBoolean(K_BOOT, true),
        markAsRead = sp.getBoolean(K_MARK_READ, false),
    )

    fun edit(block: Prefs.() -> Prefs) {
        val next = _prefs.value.block()
        sp.edit()
            .putInt(K_API_ID, next.apiId)
            .putString(K_API_HASH, next.apiHash)
            .putStringSet(K_CHANNELS, next.channelIds.map { it.toString() }.toSet())
            .putString(K_ENGINE, next.ttsEngine)
            .putFloat(K_RATE, next.speechRate)
            .putFloat(K_PITCH, next.speechPitch)
            .putString(K_LANG, next.voiceLanguages)
            .putBoolean(K_ANNOUNCE, next.announceChannelName)
            .putInt(K_LEAD_IN, next.leadInMs)
            .putBoolean(K_LEAD_IN_EXT, next.leadInOnlyExternal)
            .putBoolean(K_RADIO_CUE, next.radioCue)
            .putBoolean(K_MISSED, next.readMissedMessages)
            .putBoolean(K_BOOT, next.autoStartOnBoot)
            .putBoolean(K_MARK_READ, next.markAsRead)
            .apply()
        _prefs.update { next }
    }

    private companion object {
        const val K_API_ID = "api_id"
        const val K_API_HASH = "api_hash"
        const val K_CHANNELS = "channel_ids"
        const val K_ENGINE = "tts_engine"
        const val K_RATE = "speech_rate"
        const val K_PITCH = "speech_pitch"
        const val K_LANG = "voice_language"
        const val K_ANNOUNCE = "announce_channel"
        const val K_LEAD_IN = "lead_in_ms"
        const val K_LEAD_IN_EXT = "lead_in_only_external"
        const val K_RADIO_CUE = "radio_cue"
        const val K_MISSED = "read_missed"
        const val K_BOOT = "auto_start_on_boot"
        const val K_MARK_READ = "mark_as_read"
    }
}
