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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.invisibleToUser
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
    // of showing a spinner. But *retry* while offline must show activity —
    // otherwise the "Try again" button looks broken.
    val isRetrying = isChecking && isOffline

    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-pulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-glow",
    )

    // Pulse whenever *any* check is in flight, including retries.
    val logoScale = if (isChecking) pulse else 1f
    val logoAlpha = when {
        isRetrying -> glow * 0.7f   // retry: dimmer than silent check
        isOffline -> 0.55f          // stable offline: card is focus
        isChecking -> glow          // silent first check
        else -> 1f
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
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = null, // decorative — launcher already names the app
                    modifier = Modifier
                        .size(210.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                )
            }

            AnimatedVisibility(
                visible = isOffline,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 },
                exit = fadeOut(tween(140)),
            ) {
                OfflineCard(
                    canContinueOffline = canContinueOffline,
                    onRetry = onRetry,
                    onContinueOffline = onContinueOffline,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        }

        // Screen-reader announcement for the otherwise silent check.
        // Sighted users see the pulse; TalkBack users need the words.
        if (isChecking && !isOffline) {
            Text(
                text = "Checking connection",
                modifier = Modifier.semantics { invisibleToUser() },
            )
        }
    }
}

@Composable
private fun OfflineCard(
    canContinueOffline: Boolean,
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
            // Subtle fill so the card is visible against `Night`.
            // The gradient border alone was nearly invisible.
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
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Aqua,
                    contentColor = Night,
                ),
                // heightIn (not height) so large fontScale can grow the button.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Try again", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            if (canContinueOffline) {
                OutlinedButton(
                    onClick = onContinueOffline,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHigh),
                    // Default OutlinedButton border is invisible on `Night`.
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