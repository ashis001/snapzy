package com.snapzy.app.core.media

import androidx.compose.runtime.Composable

/**
 * Requests the OS permissions needed to read the device gallery.
 *
 * Returns the current grant state and a callback that triggers the request.
 * On platforms where the gallery needs no permission (iOS), the callback is a
 * no-op and the state is always granted.
 */
@Composable
expect fun rememberMediaPermission(
    onResult: (granted: Boolean) -> Unit = {},
): Pair<Boolean, () -> Unit>
