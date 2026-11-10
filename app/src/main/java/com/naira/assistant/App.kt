package com.naira.assistant

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.naira.assistant.core.navigation.ThrisaNavScaffold
import com.naira.assistant.core.theme.ThrisaTheme

@Composable
fun App(
    modifier: Modifier = Modifier,
    // Default: follow the system setting. If NAIRA is a dark-only brand,
    // set this to `true` at the MainActivity call site AND switch
    // enableEdgeToEdge() to `SystemBarStyle.dark(...)` — see notes below.
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    ThrisaTheme(darkTheme = darkTheme) {
        ThrisaNavScaffold(
            // Do not chain another fillMaxSize() here. `modifier` already
            // carries fillMaxSize() from MainActivity — a second one is
            // redundant and prevents multi-pane / preview use.
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "App — dark", showBackground = true)
@Composable
private fun AppPreviewDark() {
    App(darkTheme = true)
}