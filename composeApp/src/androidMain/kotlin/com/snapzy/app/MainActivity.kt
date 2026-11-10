package com.snapzy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.snapzy.app.core.media.AndroidAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.context = applicationContext
        setContent {
            SnapzyApp()
        }
    }
}
