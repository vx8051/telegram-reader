package com.telegramreader.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.telegramreader.app.R
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

    private val handler = Handler(Looper.getMainLooper())

    // Cue and lead-in silence are played by the app itself: TTS earcons/silent utterances are executed
    // in the engine's process and (on Google TTS at least) never report completion.
    private val sounds = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttrs).build()
    private val cueSound = sounds.load(appContext, R.raw.radio_cue, 1)
    private val silenceSound = sounds.load(appContext, R.raw.silence, 1)

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
        set(v) { field = v; if (ready) tts.setSpeechRate(v) }
    @Volatile var pitch = 1.0f
        set(v) { field = v; if (ready) tts.setPitch(v) }
    /** Silence (ms) queued before each item; 0 disables. */
    @Volatile var leadInMs = 0
    /** Apply the lead-in only when audio is routed to Bluetooth / USB / car. */
    @Volatile var leadInOnlyExternal = true
    /** Play the radio squelch/chirp cue before each item. */
    @Volatile var radioCue = false

    /** Locale used when an item has none; null = device default. */
    @Volatile var defaultLocale: Locale? = null
        set(v) { field = v; if (ready) applyLanguage(null) }
    private var appliedLocale: Locale? = null

    /** TTS engine package name; empty = system default. Changing it re-creates the engine binding. */
    @Volatile var engine: String = ""
        set(v) {
            if (field == v) return
            field = v
            synchronized(lock) {
                interruptLocked()          // current item goes back to the head of the queue
                ready = false
                appliedLocale = null
                tts.shutdown()
                tts = createTts(v)
            }
        }

    private var tts: TextToSpeech = createTts("")

    private fun createTts(enginePackage: String): TextToSpeech {
        lateinit var created: TextToSpeech
        val listener = TextToSpeech.OnInitListener { status -> onTtsInit(created, status) }
        created = if (enginePackage.isBlank()) TextToSpeech(appContext, listener)
        else TextToSpeech(appContext, listener, enginePackage)
        return created
    }

    private fun onTtsInit(instance: TextToSpeech, status: Int) {
        synchronized(lock) {
            if (instance !== tts) return   // a stale engine finished initialising after being replaced
            if (status != TextToSpeech.SUCCESS) {
                _engineError.value = "Text-to-speech engine failed to initialise (status $status)"
                Log.e(TAG, _engineError.value!!)
                return
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
            pumpLocked()
        }
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
        handler.removeCallbacksAndMessages(null)
        sounds.autoPause()
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
            handler.removeCallbacksAndMessages(null)
            sounds.autoPause()
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
            handler.removeCallbacksAndMessages(null)
            sounds.autoPause()
            tts.stop()
            _speaking.value = null
            releaseFocusLocked()
        }
    }

    fun shutdown() {
        clear()
        sounds.release()
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
        current = next
        _speaking.value = next

        val external = isExternalOutput()
        val lead = leadInMs.takeIf { it > 0 && (!leadInOnlyExternal || external) } ?: 0
        val cue = radioCue
        Log.d(TAG, "outputs=${outputTypes()} external=$external leadIn=${lead}ms cue=$cue")
        if (lead == 0 && !cue) { speakLocked(next); return }

        // Lead-in: play near-silent noise (not digital zeros — signal-gated head units ignore those) so
        // Bluetooth / car audio opens and unmutes its stream; then the cue; then speech.
        if (lead > 0) sounds.play(silenceSound, 1f, 1f, 1, 0, 1f)
        if (cue) handler.postDelayed({ synchronized(lock) { if (current === next) sounds.play(cueSound, CUE_VOLUME, CUE_VOLUME, 1, 0, 1f) } }, lead.toLong())
        val speechAt = lead + (if (cue) CUE_MS else 0)
        handler.postDelayed({ synchronized(lock) { if (current === next) speakLocked(next) } }, speechAt.toLong())
    }

    /** Queues [item]'s text into the TTS engine; must hold [lock] and have `current === item`. */
    private fun speakLocked(item: Item) {
        val chunks = MessageSpeech.chunk(item.text, TextToSpeech.getMaxSpeechInputLength() - 1)
        currentChunksLeft = chunks.size
        val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) }
        chunks.forEachIndexed { i, chunk ->
            val r = tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, "${item.id}-$i")
            if (r != TextToSpeech.SUCCESS) {
                Log.w(TAG, "speak() returned $r")
                currentChunksLeft--
            }
        }
        if (currentChunksLeft <= 0) { current = null; _speaking.value = null; pumpLocked() }
    }

    private fun outputTypes(): String =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString { "${it.type}:${it.productName}" }

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

    private companion object {
        const val TAG = "Speaker"
        /** Length of res/raw/radio_cue.wav plus a small gap before speech. */
        const val CUE_MS = 500
        /** Cue level relative to speech; the beeps are piercing at full scale. */
        const val CUE_VOLUME = 0.75f
    }
}
