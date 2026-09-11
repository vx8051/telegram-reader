package com.telegramreader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Voice") }
            item {
                VoiceCard(
                    rate = prefs.speechRate, pitch = prefs.speechPitch, languages = prefs.voiceLanguages,
                    onRate = { r -> vm.updatePrefs { copy(speechRate = r) } },
                    onPitch = { p -> vm.updatePrefs { copy(speechPitch = p) } },
                    onLanguages = { l -> vm.updatePrefs { copy(voiceLanguages = l) } },
                )
            }
            item {
                GroupCard {
                    Column(Modifier.padding(16.dp, 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Default.Bluetooth)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Lead-in pause", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Silence before each post so Bluetooth / car audio doesn't clip the first word",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "%.1f s".format(prefs.leadInMs / 1000f), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = prefs.leadInMs.toFloat(), onValueChange = { v -> vm.updatePrefs { copy(leadInMs = (v / 100).toInt() * 100) } },
                            valueRange = 0f..2000f, steps = 19, modifier = Modifier.padding(start = 50.dp),
                            colors = SliderDefaults.colors(inactiveTickColor = Color.Transparent, activeTickColor = Color.Transparent),
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Icons.Default.Headset, "Only on external audio", "Skip the pause when playing through the phone speaker") {
                        Switch(prefs.leadInOnlyExternal, { v -> vm.updatePrefs { copy(leadInOnlyExternal = v) } })
                    }
                }
            }

            item { SectionHeader("Behaviour") }
            item {
                GroupCard {
                    SettingRow(Icons.Default.Campaign, "Announce channel name", "Say which channel a post is from") {
                        Switch(prefs.announceChannelName, { v -> vm.updatePrefs { copy(announceChannelName = v) } })
                    }
                    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Icons.Default.History, "Read missed posts", "Catch up on posts that arrived while offline") {
                        Switch(prefs.readMissedMessages, { v -> vm.updatePrefs { copy(readMissedMessages = v) } })
                    }
                    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Icons.Default.DoneAll, "Mark as read in Telegram", "After a post has been read aloud in full") {
                        Switch(prefs.markAsRead, { v -> vm.updatePrefs { copy(markAsRead = v) } })
                    }
                    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Icons.Default.RestartAlt, "Start after reboot", "Resume reading automatically") {
                        Switch(prefs.autoStartOnBoot, { v -> vm.updatePrefs { copy(autoStartOnBoot = v) } })
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun VoiceCard(
    rate: Float, pitch: Float, languages: String,
    onRate: (Float) -> Unit, onPitch: (Float) -> Unit, onLanguages: (String) -> Unit,
) {
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SliderRow(Icons.Default.Speed, "Speed", rate, 0.5f..2.5f, 19, onRate)
            SliderRow(Icons.Default.GraphicEq, "Pitch", pitch, 0.5f..2.0f, 14, onPitch)
            Spacer(Modifier.height(4.dp))
            var text by rememberSaveable { mutableStateOf(languages) }
            OutlinedTextField(
                value = text, onValueChange = { text = it; onLanguages(it.trim()) }, singleLine = true,
                label = { Text("Languages") },
                placeholder = { Text("en-US, uk-UA") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                supportingText = { Text("Comma-separated. With several, each post's language is detected and the matching voice is used. Empty = device default.") },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: Float,
    range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(inactiveTickColor = Color.Transparent, activeTickColor = Color.Transparent),
        )
        Text(
            "%.1f×".format(value), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp),
        )
    }
}

