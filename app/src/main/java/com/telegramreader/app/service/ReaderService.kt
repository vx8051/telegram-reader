package com.telegramreader.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.telegramreader.app.App
import com.telegramreader.app.R
import com.telegramreader.app.telegram.MessageSpeech
import com.telegramreader.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.LinkedHashSet

/**
 * Foreground service that keeps the process (and thus the TDLib connection) alive and reads every
 * new post from the selected channels aloud, whether or not the activity is visible.
 */
class ReaderService : LifecycleService() {

    private lateinit var speaker: Speaker
    private lateinit var languages: LanguageDetector
    private lateinit var wakeLock: PowerManager.WakeLock
    private val app get() = application as App

    /** Ids of messages already spoken, to guard against duplicate updates. */
    private val seen = object : LinkedHashSet<Long>() {
        override fun add(element: Long): Boolean {
            val added = super.add(element)
            if (size > 500) iterator().let { it.next(); it.remove() }
            return added
        }
    }
    private data class MessageRef(val chatId: Long, val messageId: Long)

    /** Media albums arrive as one message per item; only the first (which carries the caption) is read. */
    private var lastAlbumId = 0L

    override fun onCreate() {
        super.onCreate()
        speaker = Speaker(this)
        languages = LanguageDetector(this)
        speaker.onItemFinished = { item ->
            val ref = item.tag as? MessageRef
            if (ref != null && app.settings.prefs.value.markAsRead) {
                lifecycleScope.launch {
                    app.tdlib.sendOrNull(
                        TdApi.ViewMessages(ref.chatId, longArrayOf(ref.messageId), TdApi.MessageSourceChatHistory(), true),
                    )
                }
            }
        }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telegramreader:reader")
            .apply { setReferenceCounted(false) }

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        ReaderState.setRunning(true)

        // Keep TTS settings in sync with preferences.
        lifecycleScope.launch {
            app.settings.prefs.collect { p ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                speaker.engine = p.ttsEngine
                speaker.rate = p.speechRate
                speaker.pitch = p.speechPitch
                speaker.defaultLocale = p.voiceLocales.firstOrNull()
                speaker.leadInMs = p.leadInMs
                speaker.leadInOnlyExternal = p.leadInOnlyExternal
                speaker.radioCue = p.radioCue
            }
        }
        // Hold a wake lock only while actually speaking, and mirror pause state to the notification.
        lifecycleScope.launch {
            combine(speaker.speaking, speaker.paused) { s, p -> s to p }.collect { (item, paused) ->
                if (item != null) wakeLock.acquire(10 * 60 * 1000L) else if (wakeLock.isHeld) wakeLock.release()
                ReaderState.setNowSpeaking(item?.text)
                ReaderState.setPaused(paused)
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
        // The actual work: watch TDLib updates for new posts in the selected channels.
        // Runs off the main thread because language detection may block briefly.
        lifecycleScope.launch(Dispatchers.Default) {
            app.tdlib.updates.collect { u ->
                when (u) {
                    is TdApi.UpdateNewMessage -> onNewMessage(u.message)
                    else -> {}
                }
            }
        }
    }

    private fun onNewMessage(m: TdApi.Message) {
        val prefs = app.settings.prefs.value
        if (m.chatId !in prefs.channelIds) return
        if (m.isOutgoing || m.sendingState != null) return
        if (!seen.add(m.id)) return
        // On reconnect TDLib replays posts that arrived while offline; skip them unless asked to.
        if (!prefs.readMissedMessages && m.date < System.currentTimeMillis() / 1000 - MISSED_TOLERANCE_SEC) return
        if (m.mediaAlbumId != 0L) {
            if (m.mediaAlbumId == lastAlbumId) return
            lastAlbumId = m.mediaAlbumId
        }

        val body = MessageSpeech.textOf(m.content) ?: return
        speak(body, m.chatId, MessageRef(m.chatId, m.id))
    }

    /** Announces the channel (if enabled), picks a voice for [body]'s language, and queues it. */
    private fun speak(body: String, chatId: Long?, ref: MessageRef? = null) {
        val prefs = app.settings.prefs.value
        val channel = chatId?.let { app.channels.titleOf(it) }
        val text = if (prefs.announceChannelName && channel != null) "$channel. $body" else body
        val locale = languages.choose(body, prefs.voiceLocales)
        Log.d(TAG, "Speaking from ${channel ?: "-"} [${locale ?: "default"}]: ${body.take(80)}")
        speaker.enqueue(text, locale, ref)
        ReaderState.record(SpokenEntry(System.currentTimeMillis(), channel ?: "Test", body))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> speaker.pause()
            ACTION_RESUME -> speaker.resume()
            ACTION_SKIP -> speaker.skip()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_TEST -> intent.getStringExtra(EXTRA_TEXT)?.let { body ->
                val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L).takeIf { it != 0L }
                lifecycleScope.launch(Dispatchers.Default) { speak(body, chatId) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ReaderState.setRunning(false)
        speaker.shutdown()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun buildNotification(): Notification {
        val paused = speaker.paused.value
        val (title, detail) = ReaderState.statusText(
            running = true, paused = paused, nowSpeaking = speaker.speaking.value?.text,
            selectedCount = app.settings.prefs.value.channelIds.size,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(code: Int, act: String) = PendingIntent.getService(
            this, code, Intent(this, ReaderService::class.java).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (paused) {
            b.addAction(0, getString(R.string.action_resume), action(1, ACTION_RESUME))
        } else {
            b.addAction(0, getString(R.string.action_pause), action(1, ACTION_PAUSE))
            b.addAction(0, getString(R.string.action_skip), action(2, ACTION_SKIP))
        }
        b.addAction(0, getString(R.string.action_stop), action(3, ACTION_STOP))
        return b.build()
    }

    companion object {
        private const val TAG = "ReaderService"
        private const val NOTIFICATION_ID = 1
        private const val MISSED_TOLERANCE_SEC = 5 * 60

        const val ACTION_PAUSE = "com.telegramreader.app.PAUSE"
        const val ACTION_RESUME = "com.telegramreader.app.RESUME"
        const val ACTION_SKIP = "com.telegramreader.app.SKIP"
        const val ACTION_STOP = "com.telegramreader.app.STOP"
        const val ACTION_TEST = "com.telegramreader.app.TEST"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CHAT_ID = "chat_id"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, ReaderService::class.java))

        fun stop(context: Context) =
            context.startService(Intent(context, ReaderService::class.java).setAction(ACTION_STOP))

        fun send(context: Context, action: String) =
            context.startService(Intent(context, ReaderService::class.java).setAction(action))

        /** Speaks [body] as if it were a new post in [chatId] (null = no channel announcement). */
        fun test(context: Context, body: String, chatId: Long?) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReaderService::class.java).setAction(ACTION_TEST)
                    .putExtra(EXTRA_TEXT, body).putExtra(EXTRA_CHAT_ID, chatId ?: 0L),
            )
    }
}
