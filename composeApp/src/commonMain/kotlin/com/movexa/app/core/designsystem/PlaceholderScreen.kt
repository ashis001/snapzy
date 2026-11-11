package com.movexa.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared shell for a screen that's stubbed in this phase of MVP_PLAN.md.
 * Replace the body of the screen file that calls this once that phase starts —
 * the navigation route and call site never need to change.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    primaryAction: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize().background(MovexaColors.bgBase).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = "‹ Back",
                style = MovexaType.body,
                color = MovexaColors.brandPrimary,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = title, style = MovexaType.title, color = MovexaColors.textPrimary, textAlign = TextAlign.Center)
                Text(
                    text = subtitle,
                    style = MovexaType.body,
                    color = MovexaColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                primaryAction?.invoke()
            }
        }
    }
}
