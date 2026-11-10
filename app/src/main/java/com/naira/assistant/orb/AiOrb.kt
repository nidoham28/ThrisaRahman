package com.naira.assistant.orb

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The mode the orb is currently visualising. */
enum class OrbState { Idle, Listening, Thinking, Speaking }

/** Core (bright centre) and edge (outer rim / glow) colour for a single state. */
data class OrbPalette(val core: Color, val edge: Color)

/** Colours for every state. Override to match your brand. */
data class OrbColors(
    val idle: OrbPalette = OrbPalette(Color(0xFF9FB4FF), Color(0xFF4C5FD5)),
    val listening: OrbPalette = OrbPalette(Color(0xFF7FF0FF), Color(0xFF1E88E5)),
    val thinking: OrbPalette = OrbPalette(Color(0xFFD1A6FF), Color(0xFF7B3FE4)),
    val speaking: OrbPalette = OrbPalette(Color(0xFFFFC38A), Color(0xFFE5408A)),
) {
    internal fun forState(state: OrbState) = when (state) {
        OrbState.Idle -> idle
        OrbState.Listening -> listening
        OrbState.Thinking -> thinking
        OrbState.Speaking -> speaking
    }
}

/**
 * Animated AI-assistant orb in the fluid "liquid aurora" style: a glass sphere
 * with three soft gradient blobs orbiting inside it and mixing additively, over
 * sphere shading, rim depth, a slow sheen and a specular highlight — the same
 * visual language as the Gemini assistant orb, drawn entirely with common
 * Compose canvases (works on Android and Compose Multiplatform).
 *
 * Performance notes:
 *  - All animation state is read inside the draw phase, so frames never
 *    recompose — only the draw layer re-issues.
 *  - No per-frame `Path` rebuilds: one reusable oval is used for the sphere
 *    clip, and every fluid shape is a GPU gradient instead of geometry.
 *  - Every animation frequency is an integer harmonic of the master clock, so
 *    the whole loop is perfectly seamless with no per-frame phase drift.
 *
 * @param state  Idle / Listening / Thinking / Speaking.
 * @param level  Optional live audio level 0f..1f (mic amplitude while Listening,
 *               TTS amplitude while Speaking). If null, a smooth simulated
 *               level is used so the orb still looks alive.
 */
@Composable
fun AiOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    level: Float? = null,
    colors: OrbColors = OrbColors(),
) {
    // ---- Continuous clocks (all loop seamlessly) ----
    val clock = rememberInfiniteTransition(label = "orb")
    // Master organic phase, 0..2π over ~9s. Blobs wobble on integer harmonics of it.
    val phase = clock.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val sheen = clock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen",
    )
    val swirl = clock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "swirl",
    )
    val ripple = clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )

    // ---- State blend weights (cross-fade between modes) ----
    val fade = tween<Float>(500, easing = FastOutSlowInEasing)
    val listenW = animateFloatAsState(if (state == OrbState.Listening) 1f else 0f, fade, label = "lw")
    val thinkW = animateFloatAsState(if (state == OrbState.Thinking) 1f else 0f, fade, label = "tw")
    val speakW = animateFloatAsState(if (state == OrbState.Speaking) 1f else 0f, fade, label = "sw")

    // ---- Colours ----
    val palette = colors.forState(state)
    val coreColor = animateColorAsState(palette.core, tween(600), label = "core")
    val edgeColor = animateColorAsState(palette.edge, tween(600), label = "edge")

    // ---- Audio level (smoothed) ----
    val smoothLevel = animateFloatAsState(
        targetValue = (level ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "level",
    )

    val sizeModifier = if (size == Dp.Unspecified) Modifier else Modifier.size(size)
    val sphereClip = remember { Path() }

    Canvas(modifier.then(sizeModifier)) {
        val t = phase.value
        val lw = listenW.value
        val tw = thinkW.value
        val sw = speakW.value
        val core = coreColor.value
        val edge = edgeColor.value
        val active = lw + sw // voice-driven energy

        val r = this.size.minDimension / 2f
        val c = center

        // Simulated amplitude (0..1, breathing two-octave wave) while no mic/TTS
        // level is supplied; only voiced states light it up.
        val simulated = (sin(t * 3f) * 0.6f + sin(t * 7f + 1f) * 0.4f) * 0.5f + 0.5f
        val lv = if (level == null) simulated * active.coerceIn(0f, 1f) else smoothLevel.value

        // Gentle breathing + voice-driven swell.
        val breathe = 1f + 0.022f * sin(t) + lv * 0.05f * active.coerceIn(0f, 1f)
        val bodyR = r * 0.80f * breathe

        // 1) Atmosphere: soft halo that brightens with the voice.
        val glowR = r * (0.98f + 0.10f * lv)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(edge.copy(alpha = 0.30f + 0.22f * lv), Color.Transparent),
                center = c,
                radius = glowR,
            ),
            radius = glowR,
            center = c,
        )

        // 2) Sphere interior — everything below mixes inside the glass ball.
        sphereClip.reset()
        sphereClip.addOval(
            Rect(
                offset = Offset(c.x - bodyR, c.y - bodyR),
                size = Size(bodyR * 2f, bodyR * 2f),
            )
        )
        clipPath(sphereClip) {
            // Base shading: lit toward the top-left, deep at the rim → 3D ball.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(core.copy(alpha = 0.16f), edge.copy(alpha = 0.60f)),
                    center = c + Offset(-bodyR * 0.25f, -bodyR * 0.30f),
                    radius = bodyR * 1.7f,
                ),
                radius = bodyR,
                center = c,
            )

            // 3) Aurora blobs: three soft lights orbiting on elliptical paths,
            //    mixed additively so their overlap glows like liquid plasma.
            val third = lerp(core, edge, 0.5f)
            val blobColors = listOf(core, edge, third)
            val blobSpeeds = listOf(1, -2, 3)              // integer harmonics → seamless loop
            val blobOrbits = listOf(0.30f, 0.44f, 0.22f)   // fraction of bodyR
            val blobSizes = listOf(0.66f, 0.58f, 0.52f)    // fraction of bodyR
            for (i in 0..2) {
                val col = blobColors[i]
                val angle = t * blobSpeeds[i] + i * 2.1f
                val orbit = bodyR * blobOrbits[i] * (1f + 0.20f * sin(t * 2f + i * 1.9f))
                val cx = c.x + cos(angle) * orbit
                val cy = c.y + sin(angle) * orbit * 0.8f    // squashed orbit = organic drift
                val blobR = bodyR * blobSizes[i] *
                        (1f + 0.10f * sin(t * 2f + i)) *
                        (1f + 0.22f * lv * active.coerceIn(0f, 1f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(col.copy(alpha = 0.80f), col.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = blobR,
                    ),
                    radius = blobR,
                    center = Offset(cx, cy),
                    blendMode = BlendMode.Plus,
                )
            }

            // 4) Inner core light: gentle two-octave flicker, brighter when voiced.
            val coreGlow = 0.42f + 0.13f * sin(t * 2f) + 0.07f * sin(t * 5f) +
                    0.22f * lv * active.coerceIn(0f, 1f) + tw * 0.10f
            val coreR = bodyR * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (coreGlow).coerceIn(0f, 0.85f)),
                        lerp(core, Color.White, 0.55f).copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = c + Offset(-bodyR * 0.10f, -bodyR * 0.12f),
                    radius = coreR,
                ),
                radius = coreR,
                center = c + Offset(-bodyR * 0.10f, -bodyR * 0.12f),
                blendMode = BlendMode.Plus,
            )

            // 5) Thinking: fast energy swirl sweeping around the inside of the ball.
            if (tw > 0.01f) {
                rotate(degrees = swirl.value, pivot = c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                core.copy(alpha = 0.38f * tw),
                                Color.Transparent,
                                Color.Transparent,
                            ),
                            center = c,
                        ),
                        radius = bodyR,
                        center = c,
                        blendMode = BlendMode.Plus,
                    )
                }
            }

            // 6) Rim depth: darken just inside the edge so the sphere reads as glass.
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.70f to Color.Transparent,
                        0.95f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                    center = c,
                    radius = bodyR,
                ),
                radius = bodyR,
                center = c,
            )

            // 7) Sheen: a soft highlight band drifting around the surface.
            rotate(degrees = sheen.value, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent,
                            Color.Transparent,
                        ),
                        center = c,
                    ),
                    radius = bodyR,
                    center = c,
                    blendMode = BlendMode.Plus,
                )
            }
        }

        // 8) Listening: soft sonar rings expanding outward from the sphere.
        if (lw > 0.01f) {
            repeat(2) { i ->
                val p = (ripple.value + i / 2f) % 1f
                drawCircle(
                    color = edge.copy(alpha = (1f - p) * 0.30f * lw),
                    radius = bodyR + (r - bodyR) * p,
                    center = c,
                )
            }
        }

        // 9) Specular highlight: the "glass" catchlight, top-left.
        val hlAlpha = 0.42f + 0.08f * sin(t)
        val hlR = bodyR * 0.42f
        val hlC = c + Offset(-bodyR * 0.34f, -bodyR * 0.40f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = hlAlpha), Color.Transparent),
                center = hlC,
                radius = hlR,
            ),
            radius = hlR,
            center = hlC,
        )
    }
}

/** Demo screen: tap a button to switch modes. Safe to drop into commonMain. */
@Composable
fun AiOrbDemo() {
    var state by remember { mutableStateOf(OrbState.Idle) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AiOrb(state = state, size = 260.dp)
        Spacer(Modifier.height(40.dp))
        Text(state.name)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbState.entries.forEach { s ->
                Button(onClick = { state = s }) { Text(s.name) }
            }
        }
    }
}
