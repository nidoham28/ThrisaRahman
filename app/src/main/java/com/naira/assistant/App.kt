
package com.naira.assistant

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.naira.assistant.core.navigation.ThrisaNavScaffold
import com.naira.assistant.core.theme.ThrisaTheme

@Composable
fun App(
    modifier: Modifier = Modifier
) {
    ThrisaTheme(
        darkTheme = true
    ) {
        ThrisaNavScaffold(
            modifier = modifier.fillMaxSize()
        )
    }
}
