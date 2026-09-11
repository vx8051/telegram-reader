package com.telegramreader.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun CredentialsScreen(vm: AppViewModel) {
    var apiId by rememberSaveable { mutableStateOf("") }
    var apiHash by rememberSaveable { mutableStateOf("") }
    val valid = apiId.toIntOrNull() != null && apiHash.trim().length >= 16

    OnboardingScaffold(
        icon = Icons.Default.Key,
        title = "Connect to Telegram",
        blurb = "The app uses your own Telegram account through TDLib. Create an application at " +
            "my.telegram.org → API development tools and paste its keys below. They never leave this device.",
    ) {
        OutlinedTextField(
            value = apiId, onValueChange = { apiId = it.filter(Char::isDigit) },
            label = { Text("api_id") }, singleLine = true, shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiHash, onValueChange = { apiHash = it },
            label = { Text("api_hash") }, singleLine = true, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.saveCredentials(apiId.toInt(), apiHash) }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continue")
        }
    }
}
