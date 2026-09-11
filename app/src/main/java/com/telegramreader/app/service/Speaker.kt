package com.telegramreader.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.telegramreader.app.telegram.MessageSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Serialises text through Android's [TextToSpeech], one item at a time, taking transient audio focus
 * (ducking music) while something is being read. Safe to call from any thread.
 */
class Speaker(context: Context) {

    data class Item(val id: Long, val text: String, val locale: Locale?, val tag: Any? = null)

    /** Called (on the TTS callback thread) when an item has been spoken to the end — not when skipped or cleared. */
    @Volatile var onItemFinished: ((Item) -> Unit)? = null

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttrs)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
        .build()

    private val lock = Any()
    private val queue = ArrayDeque<Item>()
    private var current: Item? = null
    private var currentChunksLeft = 0
    private var hasFocus = false
    /** Another app (a call, navigation prompt…) took focus; reading resumes when it is handed back. */
    private var focusLost = false
    private var ready = false
    private val ids = AtomicLong(1)

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _speaking = MutableStateFlow<Item?>(null)
    val speaking: StateFlow<Item?> = _speaking.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    /** Set once [TextToSpeech] has initialised; re-applied when settings change. */
    @Volatile var rate = 1.0f
        set(v) { field = v; tts.setSpeechRate(v) }
    @Volatile var pitch = 1.0f
        set(v) { field = v; tts.setPitch(v) }
    /** Silence (ms) queued before each item; 0 disables. */
    @Volatile var leadInMs = 0
    /** Apply the lead-in only when audio is routed to Bluetooth / USB / car. */
    @Volatile var leadInOnlyExternal = true

    /** Locale used when an item has none; null = device default. */
    @Volatile var defaultLocale: Locale? = null
        set(v) { field = v; if (ready) applyLanguage(null) }
    private var appliedLocale: Locale? = null

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status != TextToSpeech.SUCCESS) {
            _engineError.value = "Text-to-speech engine failed to initialise (status $status)"
            Log.e(TAG, _engineError.value!!)
            return@TextToSpeech
        }
        ready = true
        applyLanguage(null)
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        tts.setAudioAttributes(audioAttrs)
        _availableLanguages.value = try {
            tts.availableLanguages.sortedBy { it.displayName }
        } catch (e: Exception) { emptyList() }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = onChunkFinished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onChunkFinished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "TTS error $errorCode for $utteranceId")
                onChunkFinished(utteranceId)
            }
        })
        synchronized(lock) { pumpLocked() }
    }

    private fun applyLanguage(wanted: Locale?) {
        val locale = wanted ?: defaultLocale ?: Locale.getDefault()
        if (locale == appliedLocale) return
        appliedLocale = locale
        val r = tts.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            _engineError.value = "Voice for ${locale.displayName} is not installed; using engine default"
        } else {
            _engineError.value = null
        }
    }

    fun enqueue(text: String, locale: Locale? = null, tag: Any? = null): Item {
        val item = Item(ids.getAndIncrement(), text, locale, tag)
        synchronized(lock) {
            queue.addLast(item)
            pumpLocked()
        }
        return item
    }

    fun pause() {
        synchronized(lock) {
            _paused.value = true
            interruptLocked()
            releaseFocusLocked()
        }
    }

    /** Stops the current item and puts it back at the head of the queue so it is re-read later. */
    private fun interruptLocked() {
        current?.let { queue.addFirst(it) }
        current = null
        currentChunksLeft = 0
        tts.stop()
        _speaking.value = null
    }

    private fun onFocusChange(change: Int) {
        synchronized(lock) {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    focusLost = true
                    hasFocus = false
                    interruptLocked()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusLost = false
                    hasFocus = true
                    pumpLocked()
                }
                // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: keep talking, we're speech.
            }
        }
    }

    fun resume() {
        synchronized(lock) {
            _paused.value = false
            pumpLocked()
        }
    }

    /** Drops the item currently being read and moves on. */
    fun skip() {
        synchronized(lock) {
            current = null
            currentChunksLeft = 0
            tts.stop() // triggers onDone/onError for the flushed chunks; pump guards against double-advance
            _speaking.value = null
            pumpLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            queue.clear()
            current = null
            currentChunksLeft = 0
            tts.stop()
            _speaking.value = null
            releaseFocusLocked()
        }
    }

    fun shutdown() {
        clear()
        tts.shutdown()
    }

    private fun onChunkFinished(utteranceId: String?) {
        synchronized(lock) {
            val cur = current ?: return
            // Callbacks for chunks flushed by stop() (pause/skip) may arrive after a new item started.
            if (utteranceId?.startsWith("${cur.id}-") != true) return
            currentChunksLeft--
            if (currentChunksLeft <= 0) {
                current = null
                _speaking.value = null
                onItemFinished?.invoke(cur)
                pumpLocked()
            }
        }
    }

    private fun pumpLocked() {
        if (!ready || _paused.value || current != null) return
        if (queue.isEmpty()) { releaseFocusLocked(); return }
        if (!hasFocus) {
            when (audioManager.requestAudioFocus(focusRequest)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> { hasFocus = true; focusLost = false }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> { focusLost = true; return } // pumped again on GAIN
                else -> { /* denied (e.g. during a call): speak anyway rather than drop the post */ }
            }
        } else if (focusLost) return
        val next = queue.pollFirst()!!
        applyLanguage(next.locale)
        val chunks = MessageSpeech.chunk(next.text, TextToSpeech.getMaxSpeechInputLength() - 1)
        current = next
        currentChunksLeft = chunks.size
        _speaking.value = next
        // Bluetooth / car audio links drop the first fraction of a second after a stream starts; lead with silence.
        val lead = leadInMs.takeIf { it > 0 && (!leadInOnlyExternal || isExternalOutput()) } ?: 0
        if (lead > 0) {
            if (tts.playSilentUtterance(lead.toLong(), TextToSpeech.QUEUE_ADD, "${next.id}-lead") == TextToSpeech.SUCCESS) {
                currentChunksLeft++
            }
        }
        chunks.forEachIndexed { i, chunk ->
            val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) }
            val r = tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, "${next.id}-$i")
            if (r != TextToSpeech.SUCCESS) {
                Log.w(TAG, "speak() returned $r")
                currentChunksLeft--
            }
        }
        if (currentChunksLeft <= 0) { current = null; _speaking.value = null; pumpLocked() }
    }

    private fun isExternalOutput(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            when (it.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_BUS, AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_DOCK, AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> true
                else -> false
            }
        }

    private fun releaseFocusLocked() {
        if (hasFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
        focusLost = false
    }

    private companion object { const val TAG = "Speaker" }
}
