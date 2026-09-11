package com.telegramreader.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegramreader.app.App
import com.telegramreader.app.data.Prefs
import com.telegramreader.app.service.ReaderService
import com.telegramreader.app.service.ReaderState
import com.telegramreader.app.telegram.Channel
import com.telegramreader.app.telegram.MessageSpeech
import com.telegramreader.app.telegram.TdException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App

    val prefs: StateFlow<Prefs> = app.settings.prefs
    val authState: StateFlow<TdApi.AuthorizationState?> = app.tdlib.authState
    val connectionState: StateFlow<TdApi.ConnectionState?> = app.tdlib.connectionState
    val channels: StateFlow<List<Channel>> = app.channels.channels
    val channelsLoading: StateFlow<Boolean> = app.channels.loading
    val running: StateFlow<Boolean> = ReaderState.running
    val paused: StateFlow<Boolean> = ReaderState.paused
    val history = ReaderState.history
    val nowSpeaking: StateFlow<String?> = ReaderState.nowSpeaking

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    // ---- credentials & auth ----

    fun saveCredentials(apiId: Int, apiHash: String) {
        app.settings.edit { copy(apiId = apiId, apiHash = apiHash.trim()) }
        app.startTdlibIfConfigured()
    }

    fun submitPhone(phone: String) = run { app.tdlib.setPhoneNumber(phone.trim()) }
    fun submitCode(code: String) = run { app.tdlib.checkCode(code.trim()) }
    fun submitPassword(password: String) = run { app.tdlib.checkPassword(password) }

    fun logOut() {
        stopReading()
        run { app.tdlib.logOut() }
    }

    // ---- channels ----

    fun refreshChannels() = app.channels.refresh()

    fun toggleChannel(id: Long) = app.settings.edit {
        copy(channelIds = if (id in channelIds) channelIds - id else channelIds + id)
    }

    // ---- reader control ----

    fun startReading() = ReaderService.start(app)
    fun stopReading() = ReaderService.stop(app)
    fun pauseReading() = ReaderService.send(app, ReaderService.ACTION_PAUSE)
    fun resumeReading() = ReaderService.send(app, ReaderService.ACTION_RESUME)
    fun skipCurrent() = ReaderService.send(app, ReaderService.ACTION_SKIP)
    /** Reads the newest readable post from the selected channels; falls back to a sample sentence. */
    fun testVoice() {
        if (!running.value) startReading()
        run {
            val latest = prefs.value.channelIds.mapNotNull { chatId ->
                val history = app.tdlib.sendOrNull(TdApi.GetChatHistory(chatId, 0, 0, 10, false)) ?: return@mapNotNull null
                history.messages.firstOrNull { MessageSpeech.textOf(it.content) != null }
            }.maxByOrNull { it.date }
            if (latest != null) {
                ReaderService.test(app, MessageSpeech.textOf(latest.content)!!, latest.chatId)
            } else {
                ReaderService.test(app, "This is how new posts will sound.", null)
            }
        }
    }

    fun updatePrefs(block: Prefs.() -> Prefs) = app.settings.edit(block)

    private fun run(block: suspend () -> Any?) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: TdException) {
                _error.value = e.error.message
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            } finally {
                _busy.value = false
            }
        }
    }
}
