package com.nidoham.thrisa.presentation.main.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.sound.VoiceState
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.ErrorSoft
import com.nidoham.thrisa.core.theme.HeroBrush
import com.nidoham.thrisa.core.theme.HeroShape
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft
import com.nidoham.thrisa.core.util.GreetingProvider
import com.nidoham.thrisa.orb.AiOrb
import com.nidoham.thrisa.orb.OrbState

// ───────────────────────── Hero ─────────────────────────
@Composable
fun HeroCard(
    voice: VoiceState,
    modifier: Modifier = Modifier,
    level: Float? = null,   // optional live mic/TTS amplitude 0f..1f (null = simulated)
    displayName: String = "User"
) {
    val provider = remember { GreetingProvider() }
    val name = displayName.trim().takeUnless { it.isBlank() || it.equals("User", ignoreCase = true) }
    val greeting = provider.currentMessage()

    Column(
        modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(HeroBrush)
            .border(1.dp, CardStroke, HeroShape)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    color = TextHigh,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                if (name != null) {
                    Text(name, color = TextSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        // Orb takes the remaining height and scales to fit.
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AssistantAvatar(
                voice = voice,
                modifier = Modifier.fillMaxSize(),
                level = level
            )
        }

        // Fixed-height status area so the orb doesn't resize when the status changes.
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            StatusLabel(voice)
        }
    }
}

@Composable
private fun StatusLabel(voice: VoiceState) {
    when (voice) {
        is VoiceState.Idle -> Unit // show nothing
        is VoiceState.Preparing -> StatusChip("PREPARING")
        is VoiceState.Error -> Text(
            voice.message,
            color = ErrorSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        is VoiceState.Listening -> StatusChip("LISTENING")
        is VoiceState.Transcribing -> StatusChip("TRANSCRIBING")
        is VoiceState.Thinking -> StatusChip("THINKING")
        is VoiceState.Speaking -> StatusChip("SPEAKING")
    }
}

@Composable
private fun StatusChip(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Aqua, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = Aqua,
            fontSize = 13.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium
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
    level: Float? = null
) {
    Box(
        modifier.semantics { contentDescription = "Bondhu assistant avatar" },
        contentAlignment = Alignment.Center
    ) {
        AiOrb(
            state = voice.toOrbState(),
            modifier = Modifier.fillMaxSize(),
            level = level
        )
    }
}
