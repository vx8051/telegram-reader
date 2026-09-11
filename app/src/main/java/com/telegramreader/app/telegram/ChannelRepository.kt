package com.telegramreader.app.telegram

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

data class Channel(val id: Long, val title: String, val username: String?)

/**
 * Keeps an in-memory map of the channels the account is a member of, fed by TDLib updates.
 * Titles are kept for *all* chats so the reader can announce a channel's name.
 */
class ChannelRepository(private val td: TdlibClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val chats = HashMap<Long, TdApi.Chat>()
    private val supergroups = HashMap<Long, TdApi.Supergroup>()
    /** Chat ids currently in the main chat list, i.e. chats the account has joined. */
    private val inMainList = HashSet<Long>()
    private val lock = Any()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        scope.launch {
            td.updates.collect { u ->
                when (u) {
                    is TdApi.UpdateNewChat -> synchronized(lock) {
                        chats[u.chat.id] = u.chat
                        if (u.chat.chatLists.orEmpty().any { it is TdApi.ChatListMain }) inMainList += u.chat.id
                    }
                    is TdApi.UpdateChatTitle -> synchronized(lock) { chats[u.chatId]?.title = u.title }
                    is TdApi.UpdateSupergroup -> synchronized(lock) { supergroups[u.supergroup.id] = u.supergroup }
                    is TdApi.UpdateChatAddedToList ->
                        if (u.chatList is TdApi.ChatListMain) synchronized(lock) { inMainList += u.chatId }
                    is TdApi.UpdateChatRemovedFromList ->
                        if (u.chatList is TdApi.ChatListMain) synchronized(lock) { inMainList -= u.chatId }
                    else -> return@collect
                }
                publish()
            }
        }
        scope.launch {
            td.authState.collect {
                when (it) {
                    is TdApi.AuthorizationStateReady -> refresh()
                    is TdApi.AuthorizationStateLoggingOut -> {
                        synchronized(lock) { chats.clear(); supergroups.clear(); inMainList.clear() }
                        publish()
                    }
                    else -> {}
                }
            }
        }
    }

    fun titleOf(chatId: Long): String? = synchronized(lock) { chats[chatId]?.title }

    /** Asks TDLib to page the whole main chat list into memory (updates arrive via [TdApi.UpdateNewChat]). */
    fun refresh() {
        if (_loading.value) return
        scope.launch {
            _loading.value = true
            try {
                // loadChats returns error 404 once every chat has been loaded.
                while (true) {
                    try {
                        td.send(TdApi.LoadChats(TdApi.ChatListMain(), 100))
                    } catch (e: TdException) {
                        if (e.error.code == 404) break
                        Log.w(TAG, "loadChats: ${e.message}")
                        break
                    }
                }
            } finally {
                _loading.value = false
                publish()
            }
        }
    }

    private fun publish() {
        val list = synchronized(lock) {
            chats.values.mapNotNull { chat ->
                val type = chat.type as? TdApi.ChatTypeSupergroup ?: return@mapNotNull null
                if (!type.isChannel) return@mapNotNull null
                if (chat.id !in inMainList) return@mapNotNull null
                val username = supergroups[type.supergroupId]?.usernames?.editableUsername?.takeIf { it.isNotEmpty() }
                Channel(chat.id, chat.title, username)
            }.sortedBy { it.title.lowercase() }
        }
        _channels.update { list }
    }

    private companion object { const val TAG = "ChannelRepository" }
}
