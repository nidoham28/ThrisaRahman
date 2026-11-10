package com.naira.assistant

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.naira.assistant.core.theme.ThrisaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Call before super.onCreate(): the decor view reads the edge-to-edge
        // flags when it's created inside super.onCreate(). After that, the
        // window is already configured.
        enableEdgeToEdge(
            // Transparent status bar in both modes — content draws behind it.
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            // Transparent nav bar too. If you keep the default, API < 29 gets
            // a translucent scrim for contrast — safe to leave as default
            // unless your design tests clean over the gesture pill.
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        setContent {
            ThrisaTheme {
                // Insets are now handled *inside* App (Scaffold, per-screen
                // imePadding, etc.). The root just fills the window.
                App(modifier = Modifier.fillMaxSize())
            }
        }
    }
}