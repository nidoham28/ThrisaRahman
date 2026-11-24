package com.naira.assistant

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.naira.assistant.core.navigation.ThrisaNavScaffold
import com.naira.assistant.core.theme.ThrisaTheme
import com.naira.assistant.domain.settings.UserSettingsStorage
import com.naira.assistant.ads.AdMobManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),

            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val themePreference = remember { UserSettingsStorage() }.getTheme()
            val darkTheme = when (themePreference.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            ThrisaTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    ThrisaNavScaffold(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AdMobManager.requestConsentAndInitialize(this)
    }
}
