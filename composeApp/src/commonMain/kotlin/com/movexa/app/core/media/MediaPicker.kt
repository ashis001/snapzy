package com.movexa.app.core.media

import androidx.compose.runtime.Composable

/** Returns a launcher callback; invoke it to open the system image picker. */
@Composable
expect fun rememberImagePicker(onResult: (PickedMedia.Image?) -> Unit): () -> Unit

/** Returns a launcher callback; invoke it to open the system video picker. */
@Composable
expect fun rememberVideoPicker(onResult: (PickedMedia.Video?) -> Unit): () -> Unit
