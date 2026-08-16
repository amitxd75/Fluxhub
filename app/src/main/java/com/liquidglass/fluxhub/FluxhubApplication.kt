package com.liquidglass.fluxhub

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom Application class
 * Pre-warms Compose runtime classes on launch to minimize frame drops on initial page navigation.
 */
class FluxhubApplication : Application() {
    
    companion object {
        private const val TAG = "FluxhubApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Prewarm Compose runtime classes on background thread
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Trigger class loading for essential Compose components
                Class.forName("androidx.compose.runtime.ComposerKt")
                Class.forName("androidx.compose.ui.platform.AndroidComposeView")
                Class.forName("androidx.compose.foundation.layout.BoxKt")
                Class.forName("androidx.compose.material3.TextKt")
                Class.forName("androidx.compose.animation.AnimatedContentKt")
                Log.d(TAG, "Compose runtime prewarmed successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prewarm Compose runtime", e)
            }
        }
    }
}
