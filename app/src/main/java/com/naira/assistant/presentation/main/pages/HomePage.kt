package com.naira.assistant.presentation.main.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.naira.assistant.core.sound.VoiceState
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.Dim
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.util.currentDateInfo
import com.naira.assistant.domain.inspirations
import com.naira.assistant.presentation.main.assistantMoodCaption
import com.naira.assistant.presentation.main.component.HeroCard
import com.naira.assistant.presentation.main.component.InfoCard
import com.naira.assistant.presentation.main.component.InspirationCard
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Minimal weather model for the Home card. Replace with your real domain type when available. */
data class WeatherSummary(val condition: String, val temperatureC: Int)

private val CardGap = 10.dp
private val HeroMinHeight = 180.dp
private val SmallIconSize = 14.dp
private const val Placeholder = "—"

/**
 * Fixed width for each inspiration card inside the horizontally scrollable row.
 *
 * 140 dp shows ~2.5 cards on a standard phone (hinting at scrollability) and
 * ~6 on a tablet (users learn the pattern). If you ever ship a tablet-first
 * layout, consider switching to `LazyRow` with `maxWidth = 180.dp` and letting
 * cards size to content instead.
 */
private val InspirationCardWidth = 140.dp

/**
 * Fraction of the LazyColumn viewport the hero card occupies on tall screens.
 * On short viewports (landscape, split-screen) the LazyColumn scrolls so the
 * hero stays visible without clipping the cards below.
 */
private const val HeroHeightFraction = 0.65f

@Composable
fun HomePage(
    voice: VoiceState,
    modifier: Modifier = Modifier,
    displayName: String = "User",
    weather: WeatherSummary? = null,
    mood: String? = null,
    /** Live mic/TTS amplitude 0f..1f, forwarded to the orb. Null = simulated. */
    level: Float? = null,
) {
    // Hoisted out of the LazyColumn so the horizontal scroll position survives
    // the item being disposed when scrolled out of view.
    val inspirationScroll = rememberScrollState()

    // Date must refresh on resume *and* when the app stays open across midnight.
    // Without this, "Friday, November" silently becomes stale after a full day.
    var today by remember { mutableStateOf(currentDateInfo()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = currentDateInfo()
    }
    LaunchedEffect(Unit) {
        // Cheap ticker. State writes are no-ops when the value is unchanged,
        // so this costs one `equals` per minute when nothing is happening.
        while (true) {
            delay(60_000L.milliseconds)
            today = currentDateInfo()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dim.ScreenPad),
        // contentPadding lets the last item scroll *past* the bottom edge
        // rather than being clipped by it — subtle but it's the Material
        // standard for scrollable lists.
        contentPadding = PaddingValues(bottom = Dim.Gap),
        verticalArrangement = Arrangement.spacedBy(Dim.Gap),
    ) {
        item(key = "hero") {
            HeroCard(
                voice = voice,
                modifier = Modifier
                    .fillMaxWidth()
                    // LazyItemScope extension — 65% of the LazyColumn's viewport.
                    // On tall screens this gives the hero presence; on short
                    // screens the LazyColumn scrolls instead of clipping.
                    .fillParentMaxHeight(HeroHeightFraction),
                level = level,
                displayName = displayName,
            )
        }

        item(key = "inspirations") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(inspirationScroll),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                inspirations.forEach { inspiration ->
                    InspirationCard(
                        item = inspiration,
                        modifier = Modifier.width(InspirationCardWidth),
                    )
                }
            }
        }

        item(key = "cards") {
            CardRow {
                InfoCard(
                    title = "Weather",
                    caption = weather?.condition ?: "Unavailable",
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WbCloudy,
                        contentDescription = null,
                        tint = Aqua,
                        modifier = Modifier.size(Dim.IconCard),
                    )
                    Spacer(Modifier.width(6.dp))
                    MetricText(weather?.let { "${it.temperatureC}°" } ?: Placeholder)
                }

                InfoCard(
                    title = "Today",
                    caption = "${today.dayName}, ${today.month}",
                    modifier = Modifier.weight(1f),
                ) {
                    MetricText(today.dayNumber.toString())
                }

                InfoCard(
                    title = "Mood",
                    caption = assistantMoodCaption(mood),
                    modifier = Modifier.weight(1f),
                    titleTrailing = {
                        Icon(
                            imageVector = Icons.Outlined.EmojiEmotions,
                            contentDescription = null,
                            tint = Lilac,
                            modifier = Modifier.size(SmallIconSize),
                        )
                    },
                ) {
                    Text(
                        text = mood ?: Placeholder,
                        color = Lilac,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        // Previously missing: long mood names truncated
                        // without ellipsis, looking like a rendering bug.
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Equal-width row of cards with consistent spacing. */
@Composable
private fun CardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) { content() }
}

@Composable
private fun MetricText(text: String, color: Color = TextHigh) {
    Text(
        text = text,
        color = color,
        fontSize = 28.sp,
        fontWeight = FontWeight.Light,
        maxLines = 1,
    )
}