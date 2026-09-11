package com.telegramreader.app.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramreader.app.service.ReaderState
import com.telegramreader.app.telegram.Channel
import org.drinkless.tdlib.TdApi
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val loading by vm.channelsLoading.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val nowSpeaking by vm.nowSpeaking.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Telegram Reader", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = vm::logOut) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroCard(
                    running = running, paused = paused, connection = connection, nowSpeaking = nowSpeaking,
                    selectedCount = prefs.channelIds.size,
                    onStart = vm::startReading, onStop = vm::stopReading,
                    onPause = vm::pauseReading, onResume = vm::resumeReading,
                    onSkip = vm::skipCurrent, onTest = vm::testVoice,
                )
            }
            item { BatteryCard() }

            item {
                SectionHeader("Channels", trailing = {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = vm::refreshChannels, Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, "Refresh") }
                })
            }
            item {
                ChannelPicker(channels = channels, selectedIds = prefs.channelIds, loading = loading, onToggle = vm::toggleChannel)
            }

            if (history.isNotEmpty()) {
                item { SectionHeader("Recently read") }
                item {
                    GroupCard {
                        history.forEachIndexed { i, h ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.padding(16.dp, 12.dp)) {
                                Box(Modifier.width(3.dp).height(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "${h.channel} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.time))}",
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(h.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeroCard(
    running: Boolean, paused: Boolean, connection: TdApi.ConnectionState?, nowSpeaking: String?, selectedCount: Int,
    onStart: () -> Unit, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onSkip: () -> Unit, onTest: () -> Unit,
) {
    val active = running && !paused
    val gradient = when {
        active -> listOf(TgBlue, TgIndigo)
        running -> listOf(TgAmber, TgCoral)
        else -> listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    }
    val fg by animateColorAsState(if (running) Color.White else MaterialTheme.colorScheme.onSurface, label = "fg")
    val fgMuted = fg.copy(alpha = 0.75f)
    val (status, detail) = ReaderState.statusText(running, paused, nowSpeaking, selectedCount)
    val (connIcon, connText) = when (connection) {
        is TdApi.ConnectionStateReady -> Icons.Default.Cloud to "Connected"
        is TdApi.ConnectionStateUpdating -> Icons.Default.Sync to "Syncing"
        is TdApi.ConnectionStateWaitingForNetwork -> Icons.Default.CloudOff to "No network"
        else -> Icons.Default.Sync to "Connecting"
    }

    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brush.linearGradient(gradient)).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) PulseDot(fg) else Box(Modifier.size(10.dp).background(fgMuted, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(status, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = fg)
                Spacer(Modifier.weight(1f))
                Pill(connIcon, connText, fg)
            }

            Text(detail, style = MaterialTheme.typography.bodyMedium, color = fgMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val big = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (running) Color.White else MaterialTheme.colorScheme.primary,
                    contentColor = if (running) gradient.first() else Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                val small = IconButtonDefaults.filledIconButtonColors(
                    containerColor = fg.copy(alpha = 0.18f), contentColor = fg,
                    disabledContainerColor = fg.copy(alpha = 0.08f), disabledContentColor = fg.copy(alpha = 0.35f),
                )
                when {
                    !running -> FilledIconButton(onClick = onStart, enabled = selectedCount > 0, colors = big, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.PlayArrow, "Start", Modifier.size(32.dp))
                    }
                    paused -> FilledIconButton(onClick = onResume, colors = big, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.PlayArrow, "Resume", Modifier.size(32.dp))
                    }
                    else -> FilledIconButton(onClick = onPause, colors = big, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.Pause, "Pause", Modifier.size(32.dp))
                    }
                }
                FilledIconButton(onClick = onSkip, enabled = running, colors = small, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Skip")
                }
                FilledIconButton(onClick = onStop, enabled = running, colors = small, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Stop, "Stop")
                }
                FilledIconButton(onClick = onTest, colors = small, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.RecordVoiceOver, "Test voice")
                }
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.size(10.dp).alpha(a).background(color, CircleShape))
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, fg: Color) {
    Row(
        Modifier.background(fg.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPicker(channels: List<Channel>, selectedIds: Set<Long>, loading: Boolean, onToggle: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = channels.filter { it.id in selectedIds }
    val summary = when {
        channels.isEmpty() && loading -> "Loading channels…"
        channels.isEmpty() -> "No channels — join some in Telegram"
        selected.isEmpty() -> "Choose channels to read"
        else -> "${selected.size} of ${channels.size} selected"
    }
    Column {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (channels.isNotEmpty()) expanded = it }) {
            OutlinedTextField(
                value = summary, onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text("Channels") },
                leadingIcon = { Icon(Icons.Default.Campaign, null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                channels.forEach { ch ->
                    val checked = ch.id in selectedIds
                    DropdownMenuItem(
                        onClick = { onToggle(ch.id) }, // stays open for multi-select
                        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(ch.id, ch.title, size = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(ch.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (ch.username != null) {
                                        Text("@${ch.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    val pm = context.getSystemService(PowerManager::class.java)
    var ignoring by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    LaunchedEffect(Unit) { ignoring = pm.isIgnoringBatteryOptimizations(context.packageName) }
    if (ignoring) return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = TgAmber.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.BatterySaver, tint = TgAmber)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Allow background activity", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Battery optimisation may cut the Telegram connection when the screen is off. Tap to exempt this app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
