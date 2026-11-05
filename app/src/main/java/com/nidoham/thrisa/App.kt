package com.nidoham.thrisa

import androidx.compose.runtime.Composable
import com.nidoham.thrisa.core.navigation.ThrisaNavScaffold
import com.nidoham.thrisa.core.theme.ThrisaTheme

@Composable
fun App() {
    ThrisaTheme(darkTheme = true) {
        ThrisaNavScaffold()
    }
}