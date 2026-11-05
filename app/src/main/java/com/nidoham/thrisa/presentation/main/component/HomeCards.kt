package com.nidoham.thrisa.presentation.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.theme.CardBrush
import com.nidoham.thrisa.core.theme.CardShape
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.Dim
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft
import com.nidoham.thrisa.domain.Inspiration

// ───────────────────────── Cards ─────────────────────────
@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(CardShape)
            .background(CardBrush)
            .border(1.dp, CardStroke, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) { content() }
}

@Composable
fun InfoCard(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    value: @Composable RowScope.() -> Unit,
) {
    GlassCard(modifier.height(Dim.InfoCard)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, color = TextSoft, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (titleTrailing != null) {
                    Spacer(Modifier.width(4.dp))
                    titleTrailing()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = value)
            Text(caption, color = TextSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun InspirationCard(item: Inspiration, modifier: Modifier = Modifier) {
    GlassCard(modifier.size(60.dp), onClick = {}) {
        Box(
            Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ){
            Row(
                Modifier.wrapContentSize(),
            ) {
                Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(item.title, color = TextHigh, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}