package com.naira.assistant.presentation.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.naira.assistant.core.sound.VoiceState
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.ErrorSoft
import com.naira.assistant.core.theme.HeroBrush
import com.naira.assistant.core.theme.HeroShape
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.core.util.GreetingProvider
import com.naira.assistant.orb.AiOrb
import com.naira.assistant.orb.OrbState

// ───────────────────────── Hero ─────────────────────────
@Composable
fun HeroCard(
    voice: VoiceState,
    modifier: Modifier = Modifier,
    level: Float? = null,
    displayName: String = "User",
) {
    // Greeting refreshes on resume + at midnight while the card is visible.
    // Falls back to recomputing on cheap recompositions too — the provider
    // is a pure function of clock, so caching only saves allocation.
    val provider = remember { GreetingProvider() }
    var greeting by remember { mutableStateOf(provider.currentMessage()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        greeting = provider.currentMessage()
    }
    LaunchedEffect(Unit) {
        // Cheap ticker: wake at each minute boundary so "Good morning" →
        // "Good afternoon" flips without a full app restart.
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            greeting = provider.currentMessage()
        }
    }

    // Treat the placeholder default as "no name configured". Users whose
    // legal name is literally "User" are vanishingly rare; if that ever
    // matters, pass a distinct sentinel from the caller instead.
    val name = displayName.trim().takeUnless {
        it.isBlank() || it.equals("User", ignoreCase = true)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(HeroBrush)
            .border(1.dp, CardStroke, HeroShape)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // Header. No SpaceBetween + weight(1f) + Spacer dance — just a Column
        // that owns the left edge. A future trailing action can slot in via
        // an optional `trailing` parameter without restructuring.
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = greeting,
                color = TextHigh,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (name != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = name,
                    color = TextSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Orb takes remaining height and scales to fit.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            AssistantAvatar(
                voice = voice,
                modifier = Modifier.fillMaxSize(),
                level = level,
            )
        }

        // Status area. heightIn (not height) so multi-line errors and large
        // font scales grow the box instead of clipping. Reserved *minimum*
        // keeps the orb's size stable when status is Idle.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            StatusLabel(voice)
        }
    }
}

@Composable
private fun StatusLabel(voice: VoiceState) {
    when (voice) {
        is VoiceState.Idle -> Unit

        is VoiceState.Preparing -> StatusChip("PREPARING")
        is VoiceState.Listening -> StatusChip("LISTENING")
        is VoiceState.Transcribing -> StatusChip("TRANSCRIBING")
        is VoiceState.Thinking -> StatusChip("THINKING")
        is VoiceState.Speaking -> StatusChip("SPEAKING")

        is VoiceState.Error -> Text(
            text = voice.message,
            color = ErrorSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Composable
private fun StatusChip(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Aqua, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Aqua,
            fontSize = 13.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Maps the app's voice state to the orb's visual mode. */
private fun VoiceState.toOrbState(): OrbState = when (this) {
    is VoiceState.Preparing -> OrbState.Thinking
    is VoiceState.Listening -> OrbState.Listening
    is VoiceState.Transcribing -> OrbState.Thinking
    is VoiceState.Thinking -> OrbState.Thinking
    is VoiceState.Speaking -> OrbState.Speaking
    is VoiceState.Idle,
    is VoiceState.Error -> OrbState.Idle
}

@Composable
fun AssistantAvatar(
    voice: VoiceState,
    modifier: Modifier = Modifier,
    level: Float? = null,
) {
    // AiOrb already sets a state-aware contentDescription
    // ("Assistant listening", "Assistant thinking", etc.). Do NOT wrap it
    // in another semantics { contentDescription = … } — that hides the
    // state from TalkBack and (previously) shipped the wrong assistant
    // name ("Bondhu") hardcoded.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AiOrb(
            state = voice.toOrbState(),
            modifier = Modifier.fillMaxSize(),
            level = level,
        )
    }
}