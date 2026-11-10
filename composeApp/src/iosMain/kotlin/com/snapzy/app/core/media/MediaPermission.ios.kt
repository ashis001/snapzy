package com.snapzy.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS grants photo-library access through the system PHPicker, which needs no
 * explicit permission for read access the app requests up front, so the state
 * is always granted and the request callback does nothing.
 */
@Composable
actual fun rememberMediaPermission(
    onResult: (granted: Boolean) -> Unit,
): Pair<Boolean, () -> Unit> {
    val noop: () -> Unit = remember { { } }
    return true to noop
}
