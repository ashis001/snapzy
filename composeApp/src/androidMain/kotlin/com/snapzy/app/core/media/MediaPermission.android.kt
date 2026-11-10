package com.snapzy.app.core.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Runtime permission for reading the device gallery.
 *
 * API 33+ needs READ_MEDIA_IMAGES + READ_MEDIA_VIDEO; below that, one
 * READ_EXTERNAL_STORAGE grant covers both. The launcher is created lazily so
 * the permission is only requested when the user actually opens the picker.
 */
@Composable
actual fun rememberMediaPermission(
    onResult: (granted: Boolean) -> Unit,
): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val grantedState = remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val isGranted = results.values.all { it } || results.isEmpty()
        grantedState.value = isGranted
        currentOnResult.value(isGranted)
    }

    val request: () -> Unit = remember {
        { launcher.launch(permissions) }
    }

    return grantedState.value to request
}
