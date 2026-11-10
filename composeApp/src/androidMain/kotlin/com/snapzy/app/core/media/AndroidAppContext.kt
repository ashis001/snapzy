package com.snapzy.app.core.media

import android.content.Context

/** Set once from MainActivity.onCreate; lets non-Composable platform code (gallery save/share) reach the app Context. */
object AndroidAppContext {
    lateinit var context: Context
}
