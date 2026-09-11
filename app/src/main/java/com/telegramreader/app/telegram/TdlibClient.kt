package com.telegramreader.app.telegram

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine/Flow wrapper around TDLib's JNI [Client].
 *
 * One instance per process. [start] must be called once with the api credentials; after that
 * [authState] drives the login UI and [updates] fans out every TDLib update to interested parties.
 */
class TdlibClient(private val context: Context) {

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
    val connectionState: StateFlow<TdApi.ConnectionState?> = _connectionState.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Update>(
        extraBufferCapacity = 4096,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()

    @Volatile private var client: Client? = null
    @Volatile private var apiId: Int = 0
    @Volatile private var apiHash: String = ""

    val isStarted get() = client != null

    @Synchronized
    fun start(apiId: Int, apiHash: String) {
        if (client != null) return
        this.apiId = apiId
        this.apiHash = apiHash
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
        } catch (e: Client.ExecutionException) {
            Log.w(TAG, "setLogVerbosityLevel failed", e)
        }
        client = Client.create(
            { obj -> onUpdate(obj) },
            { t -> Log.e(TAG, "TDLib update handler threw", t) },
            { t -> Log.e(TAG, "TDLib default exception", t) },
        )
    }

    private fun onUpdate(obj: TdApi.Object) {
        val update = obj as? TdApi.Update ?: return
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthState(update.authorizationState)
            is TdApi.UpdateConnectionState -> _connectionState.value = update.state
        }
        _updates.tryEmit(update)
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        _authState.value = state
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateClosed -> {
                // The instance is dead (e.g. after logOut); spin up a fresh one so sign-in can restart.
                synchronized(this) { client = null }
                start(apiId, apiHash)
            }
        }
    }

    private fun sendParameters() {
        val base = File(context.filesDir, "tdlib").apply { mkdirs() }
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(base, "db").absolutePath
            filesDirectory = File(base, "files").absolutePath
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = false
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@TdlibClient.apiId
            apiHash = this@TdlibClient.apiHash
            systemLanguageCode = Locale.getDefault().toLanguageTag()
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = "0.1.0"
        }
        client?.send(params) { r -> if (r is TdApi.Error) Log.e(TAG, "setTdlibParameters: $r") }
    }

    /** Sends [fn] and suspends for the response; throws [TdException] on a TDLib error. */
    suspend fun <R : TdApi.Object> send(fn: TdApi.Function<R>): R {
        val c = client ?: throw IllegalStateException("TDLib client not started")
        return suspendCancellableCoroutine { cont ->
            c.send(fn, { result ->
                if (result is TdApi.Error) {
                    cont.resumeWithException(TdException(result))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(result as R)
                }
            }, { t -> cont.resumeWithException(t) })
        }
    }

    /** Like [send] but swallows errors, returning null. */
    suspend fun <R : TdApi.Object> sendOrNull(fn: TdApi.Function<R>): R? =
        try { send(fn) } catch (e: Exception) { Log.w(TAG, "${fn.javaClass.simpleName} failed: ${e.message}"); null }

    // ---- Auth helpers ----
    suspend fun setPhoneNumber(phone: String) =
        send(TdApi.SetAuthenticationPhoneNumber(phone, null))

    suspend fun checkCode(code: String) = send(TdApi.CheckAuthenticationCode(code))

    suspend fun checkPassword(password: String) = send(TdApi.CheckAuthenticationPassword(password))

    suspend fun logOut() = send(TdApi.LogOut())

    companion object {
        private const val TAG = "TdlibClient"
    }
}
