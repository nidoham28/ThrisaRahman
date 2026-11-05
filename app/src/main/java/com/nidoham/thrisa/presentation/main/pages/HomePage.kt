package com.nidoham.thrisa.presentation.main.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.sound.VoiceState
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.Dim
import com.nidoham.thrisa.core.theme.Lilac
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.util.currentDateInfo
import com.nidoham.thrisa.domain.inspirations
import com.nidoham.thrisa.presentation.main.component.HeroCard
import com.nidoham.thrisa.presentation.main.component.InfoCard
import com.nidoham.thrisa.presentation.main.component.InspirationCard

/** Minimal weather model for the Home card. Replace with your real domain type when available. */
data class WeatherSummary(val condition: String, val temperatureC: Int)

private val CardGap = 10.dp
private val HeroMinHeight = 180.dp
private val SmallIconSize = 14.dp
private const val Placeholder = "—"

@Composable
fun HomePage(
    voice: VoiceState,
    displayName: String = "User",
    weather: WeatherSummary? = null,
    mood: String? = null,
    modifier: Modifier = Modifier,
) {
    val today = remember { currentDateInfo() }
    val dateLabel = "${today.dayName}, ${today.month}"

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Dim.ScreenPad)
            .padding(bottom = Dim.Gap),
        verticalArrangement = Arrangement.spacedBy(Dim.Gap)
    ) {
        // The hero takes whatever vertical space the cards below leave over,
        // but never collapses on small screens.
        HeroCard(
            voice,
            Modifier.fillMaxWidth().heightIn(min = HeroMinHeight).weight(1f),
            displayName = displayName
        )

        CardRow {
            inspirations.forEach { InspirationCard(it, Modifier.weight(1f)) }
        }

        CardRow {
            InfoCard(
                title = "Weather",
                caption = weather?.condition ?: "Unavailable",
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Outlined.WbCloudy,
                    contentDescription = null,
                    tint = Aqua,
                    modifier = Modifier.size(Dim.IconCard)
                )
                Spacer(Modifier.width(6.dp))
                MetricText(weather?.let { "${it.temperatureC}°" } ?: Placeholder)
            }

            InfoCard(
                title = "Today",
                caption = dateLabel,
                modifier = Modifier.weight(1f)
            ) {
                MetricText(today.dayNumber.toString())
            }

            InfoCard(
                title = "Mood",
                caption = if (mood == null) "Not set" else "Feeling today",
                modifier = Modifier.weight(1f),
                titleTrailing = {
                    Icon(
                        Icons.Outlined.EmojiEmotions,
                        contentDescription = null,
                        tint = Lilac,
                        modifier = Modifier.size(SmallIconSize)
                    )
                }
            ) {
                Text(
                    mood ?: Placeholder,
                    color = Lilac,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/** Equal-width row of cards with consistent spacing. */
@Composable
private fun CardRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardGap)
    ) { content() }
}

@Composable
private fun MetricText(text: String, color: Color = TextHigh) {
    Text(
        text,
        color = color,
        fontSize = 28.sp,
        fontWeight = FontWeight.Light,
        maxLines = 1
    )
}