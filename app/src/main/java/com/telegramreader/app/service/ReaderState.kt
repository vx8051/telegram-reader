package com.telegramreader.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpokenEntry(val time: Long, val channel: String, val text: String)

/** Process-wide view of the reader service for the UI. */
object ReaderState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _nowSpeaking = MutableStateFlow<String?>(null)
    val nowSpeaking: StateFlow<String?> = _nowSpeaking.asStateFlow()

    private val _history = MutableStateFlow<List<SpokenEntry>>(emptyList())
    val history: StateFlow<List<SpokenEntry>> = _history.asStateFlow()

    /** Headline + detail line describing the reader; shared by the hero card and the notification. */
    fun statusText(running: Boolean, paused: Boolean, nowSpeaking: String?, selectedCount: Int): Pair<String, String> {
        val title = when {
            !running -> "Reader is off"
            paused -> "Paused"
            else -> "Listening"
        }
        val channels = "$selectedCount channel${if (selectedCount == 1) "" else "s"}"
        val detail = when {
            nowSpeaking != null -> "Reading: $nowSpeaking"
            running -> "Waiting for new posts in $channels"
            selectedCount == 0 -> "Select at least one channel below, then press play."
            else -> "$channels selected. Press play to start reading in the background."
        }
        return title to detail
    }

    internal fun setRunning(v: Boolean) { _running.value = v; if (!v) { _paused.value = false; _nowSpeaking.value = null } }
    internal fun setNowSpeaking(text: String?) { _nowSpeaking.value = text }
    internal fun setPaused(v: Boolean) { _paused.value = v }
    internal fun record(entry: SpokenEntry) = _history.update { (listOf(entry) + it).take(10) }
}
