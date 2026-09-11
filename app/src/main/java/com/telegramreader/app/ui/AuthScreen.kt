package com.telegramreader.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.drinkless.tdlib.TdApi

@Composable
fun AuthScreen(vm: AppViewModel, state: TdApi.AuthorizationState) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val (title, blurb) = when (state) {
        is TdApi.AuthorizationStateWaitPhoneNumber ->
            "Sign in" to "Enter your phone number in international format. Telegram sends the code to your other signed-in device, or by SMS."
        is TdApi.AuthorizationStateWaitCode -> {
            val via = when (state.codeInfo.type) {
                is TdApi.AuthenticationCodeTypeTelegramMessage -> "the Telegram app on another device"
                is TdApi.AuthenticationCodeTypeSms -> "SMS"
                is TdApi.AuthenticationCodeTypeCall -> "a phone call"
                else -> "Telegram"
            }
            "Enter the code" to "We sent a code to ${state.codeInfo.phoneNumber} via $via."
        }
        is TdApi.AuthorizationStateWaitPassword ->
            "Two-step verification" to ("Your account is protected with a cloud password." +
                (state.passwordHint.takeIf { it.isNotBlank() }?.let { " Hint: $it" } ?: ""))
        is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
            "Confirm on another device" to "Approve the login on a device where you're already signed in, or restart the app to use a phone number."
        is TdApi.AuthorizationStateWaitRegistration ->
            "No account" to "This phone number has no Telegram account. Register in the official app first."
        is TdApi.AuthorizationStateWaitEmailAddress, is TdApi.AuthorizationStateWaitEmailCode ->
            "E-mail login required" to "This account uses e-mail sign-in, which isn't supported yet. Sign in from the official app once, then retry."
        is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing ->
            "Logging out" to "One moment…"
        else -> "Waiting for Telegram" to state.javaClass.simpleName
    }

    OnboardingScaffold(icon = Icons.AutoMirrored.Filled.Send, title = title, blurb = blurb) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> PhoneStep(vm, busy)
            is TdApi.AuthorizationStateWaitCode -> CodeStep(vm, busy)
            is TdApi.AuthorizationStateWaitPassword -> PasswordStep(vm, busy)
            else -> if (busy || state is TdApi.AuthorizationStateLoggingOut || state is TdApi.AuthorizationStateClosing) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SubmitButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text(text)
    }
}

@Composable
private fun PhoneStep(vm: AppViewModel, busy: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = phone, onValueChange = { phone = it }, placeholder = { Text("+1 555 123 4567") }, label = { Text("Phone number") },
        singleLine = true, shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton("Send code", phone.length >= 5, busy) { vm.clearError(); vm.submitPhone(phone) }
}

@Composable
private fun CodeStep(vm: AppViewModel, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = code, onValueChange = { code = it.filter(Char::isDigit) }, label = { Text("Code") },
        singleLine = true, shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton("Verify", code.length >= 4, busy) { vm.clearError(); vm.submitCode(code) }
}

@Composable
private fun PasswordStep(vm: AppViewModel, busy: Boolean) {
    var password by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = password, onValueChange = { password = it }, label = { Text("Cloud password") },
        singleLine = true, shape = MaterialTheme.shapes.small,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton("Sign in", password.isNotEmpty(), busy) { vm.clearError(); vm.submitPassword(password) }
}
