
package com.nidoham.thrisa

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nidoham.thrisa.core.navigation.ThrisaNavScaffold
import com.nidoham.thrisa.core.theme.ThrisaTheme

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
