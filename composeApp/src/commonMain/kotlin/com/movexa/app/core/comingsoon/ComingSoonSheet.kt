package com.movexa.app.core.comingsoon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaPrimaryButton
import com.movexa.app.core.designsystem.MovexaType

/** Holds which [GatedFeature] (if any) should currently be shown as a Coming Soon sheet. */
class ComingSoonController {
    var current: GatedFeature? by mutableStateOf(null)
        private set

    fun show(feature: GatedFeature) {
        current = feature
    }

    fun dismiss() {
        current = null
    }
}

/**
 * The app-wide controller.
 *
 * A single instance so any screen can raise the sheet without threading the
 * controller through the navigation graph — the host in `App.kt` renders
 * whatever it is showing.
 */
object ComingSoon {
    val controller = ComingSoonController()
}

@Composable
fun rememberComingSoonController(): ComingSoonController = ComingSoon.controller

/**
 * Wrap a screen's content with this to get the standardized Coming Soon sheet
 * (PRODUCT_SPEC §5) whenever [controller] has a feature set on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonHost(
    controller: ComingSoonController,
    content: @Composable () -> Unit,
) {
    content()

    val feature = controller.current
    if (feature != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { controller.dismiss() },
            sheetState = sheetState,
            containerColor = MovexaColors.bgSurfaceElevated,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${feature.title} is coming soon",
                    style = MovexaType.title,
                    color = MovexaColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = feature.description,
                    style = MovexaType.body,
                    color = MovexaColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                MovexaPrimaryButton(
                    text = "Got it",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onClick = { controller.dismiss() },
                )
            }
        }
    }
}
