package com.telegramreader.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/** Small uppercase section label with an optional trailing control. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Card surface used for grouped settings. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) { Column { content() } }
}

/** Row with a leading icon, primary + secondary text, and a trailing control. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(36.dp).background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

private val avatarPalette = listOf(
    Color(0xFFE17076), Color(0xFFFAA774), Color(0xFFA695E7), Color(0xFF7BC862),
    Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE), Color(0xFF2AABEE),
)

/** Telegram-style round avatar: initials on a colour chosen from the id. */
@Composable
fun Avatar(id: Long, title: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val color = avatarPalette[(id % avatarPalette.size).toInt().absoluteValue]
    val initials = title.split(' ', '-', '|').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "#" }
    Box(
        Modifier.size(size).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials, color = Color.White, fontWeight = FontWeight.Bold,
            style = if (size >= 44.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}
