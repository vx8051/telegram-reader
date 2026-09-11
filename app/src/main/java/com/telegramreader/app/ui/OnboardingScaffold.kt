package com.telegramreader.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared layout for the credentials and sign-in steps: hero icon, headline, blurb, then a card with the form. */
@Composable
fun OnboardingScaffold(
    icon: ImageVector,
    title: String,
    blurb: String,
    form: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to TgBlue.copy(alpha = 0.18f), 0.45f to MaterialTheme.colorScheme.background),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(88.dp).background(Brush.linearGradient(listOf(TgBlue, TgIndigo)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                blurb, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = form)
            }
        }
    }
}
