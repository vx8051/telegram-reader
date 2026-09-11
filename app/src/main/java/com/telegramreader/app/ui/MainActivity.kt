package com.telegramreader.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) { Root(vm) }
            }
        }
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()

    when {
        !prefs.hasCredentials -> CredentialsScreen(vm)
        auth == null || auth is TdApi.AuthorizationStateWaitTdlibParameters ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        auth is TdApi.AuthorizationStateReady -> {
            var showSettings by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showSettings) { showSettings = false }
            if (showSettings) SettingsScreen(vm, onBack = { showSettings = false })
            else HomeScreen(vm, onOpenSettings = { showSettings = true })
        }
        else -> AuthScreen(vm, auth!!)
    }
}
