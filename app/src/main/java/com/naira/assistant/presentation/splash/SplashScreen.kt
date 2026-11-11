package com.naira.assistant.presentation.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.R
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft

@Composable
fun SplashScreen(
    isChecking: Boolean,
    isOffline: Boolean,
    canContinueOffline: Boolean,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The first check is intentionally silent: the mark "breathes" instead
    // of showing a spinner. Retry while offline dims it further.
    val isRetrying = isChecking && isOffline

    // Animate ONLY while a check is in flight. An always-on infinite
    // transition burns frames (and battery) even when nothing moves.
    val pulse: State<Float>?
    val glow: State<Float>?
    if (isChecking) {
        val transition = rememberInfiniteTransition(label = "splash")
        pulse = transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logo-pulse",
        )
        glow = transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logo-glow",
        )
    } else {
        pulse = null
        glow = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Night)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Halo grouped with the logo so it stays centred on the mark
            // when the offline card pushes everything up.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(320.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Aqua.copy(alpha = 0.12f), Color.Transparent),
                            )
                        )
                )
                Image(
                    painter = painterResource(R.drawable.app_icon_round),
                    // TalkBack announcement for the otherwise silent check:
                    // a live region whose description changes when checking
                    // starts. (The old invisibleToUser() Text did the exact
                    // opposite — it hid the words FROM accessibility.)
                    contentDescription =
                        if (isChecking && !isOffline) "Checking connection" else null,
                    modifier = Modifier
                        .size(210.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        // Read animated values in the draw phase, not in
                        // composition — only this layer redraws per frame,
                        // the whole screen no longer recomposes at 60fps.
                        .graphicsLayer {
                            val s = pulse?.value ?: 1f
                            scaleX = s
                            scaleY = s
                            alpha = when {
                                isRetrying -> (glow?.value ?: 1f) * 0.7f
                                isOffline -> 0.55f
                                isChecking -> glow?.value ?: 1f
                                else -> 1f
                            }
                        },
                )
            }

            AnimatedVisibility(
                visible = isOffline,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 },
                exit = fadeOut(tween(140)),
            ) {
                OfflineCard(
                    canContinueOffline = canContinueOffline,
                    isRetrying = isRetrying,
                    onRetry = onRetry,
                    onContinueOffline = onContinueOffline,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun OfflineCard(
    canContinueOffline: Boolean,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        TextSoft.copy(alpha = 0.28f),
                        TextSoft.copy(alpha = 0.06f),
                    )
                ),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 22.dp, vertical = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "You're offline",
                color = TextHigh,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Reconnect to verify your sign-in. Your last saved session stays on this device.",
                color = TextSoft,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onRetry,
                // Block duplicate taps while a retry is already running.
                enabled = !isRetrying,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Aqua,
                    contentColor = Night,
                    disabledContainerColor = Aqua.copy(alpha = 0.4f),
                    disabledContentColor = Night.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = if (isRetrying) "Checking…" else "Try again",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }

            if (canContinueOffline) {
                OutlinedButton(
                    onClick = onContinueOffline,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHigh),
                    border = BorderStroke(1.dp, TextSoft.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Continue offline", fontSize = 15.sp)
                }
            }
        }
    }
}