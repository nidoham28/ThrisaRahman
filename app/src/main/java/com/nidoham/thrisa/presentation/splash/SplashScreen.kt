package com.nidoham.thrisa.presentation.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.R
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.Night
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft

@Composable
fun SplashScreen(
    isChecking: Boolean,
    isOffline: Boolean,
    canContinueOffline: Boolean,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    // The check is intentionally silent: no spinner, no copy.
    // We only let the mark "breathe" so the screen doesn't look frozen.
    val isSilentCheck = isChecking && !isOffline

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

    Box(modifier = Modifier.fillMaxSize().background(Night)) {

        // Soft halo — adds depth behind the mark without any chrome.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Aqua.copy(alpha = 0.12f), Color.Transparent),
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = "Thrisa",
                    modifier = Modifier
                        .size(210.dp)
                        .scale(if (isSilentCheck) pulse else 1f)
                        .alpha(
                            when {
                                isOffline -> 0.45f      // step back, the card is the focus
                                isSilentCheck -> glow
                                else -> 1f
                            }
                        ),
                )

                // Surfaced only when there is a real issue.
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
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        TextSoft.copy(alpha = 0.22f),
                        TextSoft.copy(alpha = 0.04f),
                    )
                ),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 22.dp, vertical = 24.dp),
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
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Try again", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            if (canContinueOffline) {
                OutlinedButton(
                    onClick = onContinueOffline,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Continue offline", color = TextHigh, fontSize = 15.sp)
                }
            }
        }
    }
}