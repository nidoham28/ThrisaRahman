package com.naira.assistant.presentation.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.theme.CardBrush
import com.naira.assistant.core.theme.CardShape
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Dim
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.domain.Inspiration

// -----------------------------------------------------
// GLASS CARD
// -----------------------------------------------------

/**
 * Surface with a gradient fill ([CardBrush]) and a hairline border.
 *
 * When [onClick] is non-null, uses [Surface]'s `onClick` overload so Material
 * handles the ripple, semantics, focus ring, and the 48 dp minimum touch
 * target — none of which `Modifier.clickable` guarantees on its own.
 *
 * The gradient itself is applied via the modifier chain (Surface only accepts
 * a solid [Color] `color` param). `clip` + `background` + `border` all run
 * *before* Surface wraps the content, so the ripple draws clipped to [CardShape].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val decorated = modifier
        .clip(CardShape)
        .background(CardBrush)
        .border(width = 1.dp, color = CardStroke, shape = CardShape)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = decorated,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CardShape,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Surface(
            modifier = decorated,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CardShape,
            content = content,
        )
    }
}

// -----------------------------------------------------
// INFO CARD
// -----------------------------------------------------

@Composable
fun InfoCard(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    value: @Composable RowScope.() -> Unit,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dim.InfoCard),
    ) {
        // Column fills the Surface's min height (Surface propagates min
        // constraints), so SpaceBetween actually distributes. Without the
        // min height, this Column would size to content and SpaceBetween
        // would be a no-op.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            InfoCardHeader(title = title, titleTrailing = titleTrailing)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = value,
            )

            Text(
                text = caption,
                color = TextSoft,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoCardHeader(
    title: String,
    titleTrailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = TextSoft,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (titleTrailing != null) {
            Spacer(Modifier.width(6.dp))
            titleTrailing()
        }
    }
}

// -----------------------------------------------------
// INSPIRATION CARD
// -----------------------------------------------------

@Composable
fun InspirationCard(
    item: Inspiration,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBubble(
                icon = item.icon,
                tint = item.tint,
            )
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                color = TextHigh,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IconBubble(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 36.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative; adjacent title describes the card
            tint = tint,
            modifier = Modifier.size(size * 20f / 36f), // scale icon with bubble
        )
    }
}