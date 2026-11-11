package com.naira.assistant.orb

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class OrbState { Idle, Listening, Thinking, Speaking }

data class OrbPalette(val core: Color, val edge: Color)

/** Crimson particle theme, like the reference. Override for your brand. */
data class OrbColors(
    val idle: OrbPalette = OrbPalette(Color(0xFFFF5C8A), Color(0xFFE0114B)),
    val listening: OrbPalette = OrbPalette(Color(0xFFFF8FB0), Color(0xFFF50057)),
    val thinking: OrbPalette = OrbPalette(Color(0xFFE85C8A), Color(0xFF9C0F3F)),
    val speaking: OrbPalette = OrbPalette(Color(0xFFFF7FA0), Color(0xFFFF2D55)),
) {
    internal fun forState(state: OrbState) = when (state) {
        OrbState.Idle -> idle
        OrbState.Listening -> listening
        OrbState.Thinking -> thinking
        OrbState.Speaking -> speaking
    }
}

private const val PARTICLE_COUNT = 1600
private val SPARKLE_ANGLES = floatArrayOf(0.3f, 1.2f, 2.2f, 3.4f, 4.4f, 5.5f)
private val SPARKLE_RADII = floatArrayOf(1.06f, 1.16f, 1.04f, 1.14f, 1.08f, 1.18f)
private val FACE_WHITE = Color(0xFFFFF4F6)
private val PUPIL_DARK = Color(0xFF4A0716)

/** Fibonacci sphere: evenly spread unit points, built exactly once. */
private fun buildParticles(): FloatArray {
    val out = FloatArray(PARTICLE_COUNT * 3)
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
    for (i in 0 until PARTICLE_COUNT) {
        val y = 1f - 2f * (i + 0.5f) / PARTICLE_COUNT
        val rad = sqrt((1f - y * y).coerceAtLeast(0f))
        val a = golden * i
        out[i * 3] = cos(a) * rad
        out[i * 3 + 1] = y
        out[i * 3 + 2] = sin(a) * rad
    }
    return out
}

/** sin^20 positive lobe → a very narrow pulse, used for quick eye blinks. */
private fun narrowPulse(x: Float): Float {
    val s = sin(x).coerceAtLeast(0f)
    val s2 = s * s; val s4 = s2 * s2; val s8 = s4 * s4
    return s8 * s8 * s4
}

/**
 * Particle-sphere assistant orb with an expressive face.
 *
 * Performance:
 *  - 1600 dots are batched into 4 native drawPoints calls using remembered
 *    FloatArray buffers + one reused Paint → zero per-frame allocation.
 *  - All animation values are read in the draw phase only; no recomposition.
 *  - Constant tables are hoisted; paths (clip, mouth, sparkle) are reused.
 *  - Wobble/shimmer use integer harmonics of the phase so loops never snap.
 *
 * @param level live audio from 0f to 1f (mic while Listening, TTS while Speaking);
 *              null → simulated breathing so the orb still feels alive.
 * @param showFace toggle the eyes + smile overlay.
 */
@Composable
fun AiOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    level: Float? = null,
    colors: OrbColors = OrbColors(),
    showFace: Boolean = true,
) {
    val clock = rememberInfiniteTransition(label = "orb")
    val phase = clock.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    val swirl = clock.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "swirl",
    )
    val ripple = clock.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "ripple",
    )

    val fade = tween<Float>(500, easing = FastOutSlowInEasing)
    val listenW = animateFloatAsState(if (state == OrbState.Listening) 1f else 0f, fade, label = "lw")
    val thinkW = animateFloatAsState(if (state == OrbState.Thinking) 1f else 0f, fade, label = "tw")
    val speakW = animateFloatAsState(if (state == OrbState.Speaking) 1f else 0f, fade, label = "sw")

    val palette = colors.forState(state)
    val coreColor = animateColorAsState(palette.core, tween(600), label = "core")
    val edgeColor = animateColorAsState(palette.edge, tween(600), label = "edge")
    val smoothLevel = animateFloatAsState(
        (level ?: 0f).coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMedium), label = "level",
    )

    // ---- Reused, allocation-free draw resources ----
    val particles = remember { buildParticles() }
    val buffers = remember { Array(4) { FloatArray(PARTICLE_COUNT * 2) } }
    val counts = remember { IntArray(4) }
    val paint = remember { Paint().apply { isAntiAlias = true; strokeCap = Paint.Cap.ROUND } }
    val sphereClip = remember { Path() }
    val mouthPath = remember { Path() }
    val starPath = remember {
        Path().apply {
            moveTo(0f, -1f)
            quadraticTo(0.12f, -0.12f, 1f, 0f); quadraticTo(0.12f, 0.12f, 0f, 1f)
            quadraticTo(-0.12f, 0.12f, -1f, 0f); quadraticTo(-0.12f, -0.12f, 0f, -1f)
            close()
        }
    }

    val sizeModifier = if (size == Dp.Unspecified) Modifier else Modifier.size(size)

    Canvas(
        modifier.then(sizeModifier).semantics {
            contentDescription = when (state) {
                OrbState.Idle -> "Assistant idle"
                OrbState.Listening -> "Assistant listening"
                OrbState.Thinking -> "Assistant thinking"
                OrbState.Speaking -> "Assistant speaking"
            }
        }
    ) {
        val t = phase.value
        val lw = listenW.value; val tw = thinkW.value; val sw = speakW.value
        val core = coreColor.value; val edge = edgeColor.value
        val activeWeight = (lw + sw).coerceIn(0f, 1f)

        val r = this.size.minDimension / 2f
        if (r <= 0f) return@Canvas
        val c = center

        val rawLevel = if (level == null) {
            (sin(t * 3f) * 0.6f + sin(t * 7f + 1f) * 0.4f) * 0.5f + 0.5f
        } else smoothLevel.value
        val lv = rawLevel * activeWeight

        val breathe = 1f + 0.022f * sin(t) + lv * 0.05f
        val bodyR = r * 0.80f * breathe

        // 1) Halo
        val glowR = r * (0.98f + 0.10f * lv)
        drawCircle(
            Brush.radialGradient(
                listOf(edge.copy(alpha = 0.30f + 0.22f * lv), Color.Transparent), c, glowR,
            ), glowR, c,
        )

        sphereClip.reset()
        sphereClip.addOval(Rect(c.x - bodyR, c.y - bodyR, c.x + bodyR, c.y + bodyR))
        clipPath(sphereClip) {
            // 2) Nebula base behind the dots
            drawCircle(
                Brush.radialGradient(
                    listOf(edge.copy(alpha = 0.30f), edge.copy(alpha = 0.10f), Color.Transparent),
                    c, bodyR,
                ), bodyR, c,
            )

            // 3) Particle shell — rotate around Y, bucket by depth + shimmer.
            val cosR = cos(t); val sinR = sin(t)
            counts.fill(0)
            val shimmer = t * 4f
            for (i in 0 until PARTICLE_COUNT) {
                val x = particles[i * 3]; val y = particles[i * 3 + 1]; val z = particles[i * 3 + 2]
                val xr = x * cosR + z * sinR
                val zr = -x * sinR + z * cosR
                val bright = sin(shimmer + i * 0.7f) > -0.25f
                val b = (if (zr > 0f) 2 else 0) + (if (bright) 1 else 0)
                val idx = counts[b] * 2
                buffers[b][idx] = c.x + xr * bodyR
                buffers[b][idx + 1] = c.y + y * bodyR
                counts[b]++
            }
            val energy = (0.75f + 0.45f * lv).coerceAtMost(1f)
            val native = drawContext.canvas.nativeCanvas
            for (b in 0..3) {
                if (counts[b] == 0) continue
                val col: Color; val a: Float; val w: Float
                when (b) {
                    0 -> { col = lerp(edge, Color.Black, 0.25f); a = 0.22f; w = bodyR * 0.011f }
                    1 -> { col = edge; a = 0.50f; w = bodyR * 0.014f }
                    2 -> { col = core; a = 0.50f; w = bodyR * 0.017f }
                    else -> { col = lerp(core, Color.White, 0.30f); a = 0.95f; w = bodyR * 0.021f }
                }
                paint.color = col.copy(alpha = (a * energy).coerceIn(0f, 1f)).toArgb()
                paint.strokeWidth = w
                native.drawPoints(buffers[b], 0, counts[b] * 2, paint)
            }

            // 4) Rim glow
            drawCircle(
                Brush.radialGradient(
                    0.75f to Color.Transparent, 0.97f to edge.copy(alpha = 0.25f),
                    1f to Color.Transparent, center = c, radius = bodyR,
                ), bodyR, c, blendMode = BlendMode.Plus,
            )

            // 5) Thinking sweep (360° wrap is seamless; tw only gates alpha)
            if (tw > 0.01f) {
                rotate(swirl.value, c) {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(Color.Transparent, core.copy(alpha = 0.38f * tw),
                                Color.Transparent, Color.Transparent), c,
                        ), bodyR, c, blendMode = BlendMode.Plus,
                    )
                }
            }
        }

        // 6) Listening sonar rings
        if (lw > 0.01f) {
            repeat(2) { i ->
                val p = (ripple.value + i / 2f) % 1f
                drawCircle(
                    edge.copy(alpha = (1f - p) * 0.30f * lw),
                    bodyR + (r - bodyR) * p, c,
                )
            }
        }

        // 7) Face
        if (showFace) {
            val blink = 1f - 0.94f * narrowPulse(t * 2f + 0.9f)
            val lookX = sin(t) * 0.04f * bodyR + tw * 0.04f * bodyR
            val lookY = sin(t * 2f) * 0.02f * bodyR - tw * 0.055f * bodyR + lw * 0.02f * bodyR
            val rx = bodyR * 0.185f
            val ry = bodyR * 0.225f * blink * (1f - 0.12f * sw * lv)
            val irisR = bodyR * 0.105f
            val irisRy = min(irisR, ry * 0.82f)
            val pupilR = bodyR * 0.052f
            val pupilRy = min(pupilR, ry * 0.45f)
            val browColor = lerp(edge, Color.Black, 0.35f)

            for (s in intArrayOf(-1, 1)) {
                val ex = c.x + s * bodyR * 0.33f
                val ey = c.y - bodyR * 0.16f
                drawOval(FACE_WHITE, Offset(ex - rx, ey - ry), Size(rx * 2f, ry * 2f))
                val ix = ex + lookX; val iy = ey + lookY
                drawOval(edge, Offset(ix - irisR, iy - irisRy), Size(irisR * 2f, irisRy * 2f))
                drawOval(PUPIL_DARK, Offset(ix - pupilR, iy - pupilRy), Size(pupilR * 2f, pupilRy * 2f))
                if (blink > 0.5f) {
                    drawCircle(
                        Color.White, bodyR * 0.02f,
                        Offset(ix - bodyR * 0.03f, iy - bodyR * 0.035f),
                    )
                }
                // Mischievous angled brow
                drawLine(
                    browColor,
                    Offset(c.x + s * bodyR * 0.50f, c.y - bodyR * (0.46f + 0.03f * tw)),
                    Offset(c.x + s * bodyR * 0.16f, c.y - bodyR * 0.36f),
                    strokeWidth = bodyR * 0.055f, cap = StrokeCap.Round,
                )
            }

            // Smile: crescent that opens with the voice level
            val open = lv
            val halfW = bodyR * 0.30f * (1f - 0.15f * lw)
            val topY = c.y + bodyR * (0.20f + 0.01f * sin(t * 2f))
            mouthPath.reset()
            mouthPath.moveTo(c.x - halfW, topY)
            mouthPath.quadraticTo(c.x, topY + bodyR * (0.16f + 0.42f * open), c.x + halfW, topY)
            mouthPath.quadraticTo(c.x, topY + bodyR * (0.05f + 0.10f * open), c.x - halfW, topY)
            mouthPath.close()
            drawPath(mouthPath, FACE_WHITE)
        }

        // 8) Twinkling sparkles around the orb
        val sparkleColor = lerp(core, Color.White, 0.6f)
        for (i in SPARKLE_ANGLES.indices) {
            val twinkle = sin(t * 2f + i * 1.05f).coerceAtLeast(0f)
            if (twinkle < 0.05f) continue
            val a = SPARKLE_ANGLES[i]
            val px = c.x + cos(a) * r * SPARKLE_RADII[i]
            val py = c.y + sin(a) * r * SPARKLE_RADII[i]
            val s = r * 0.055f * (0.7f + 0.3f * twinkle)
            withTransform({ translate(px, py); scale(s, s, Offset.Zero) }) {
                drawPath(starPath, sparkleColor.copy(alpha = twinkle * twinkle * 0.9f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AiOrbDemo() {
    var state by remember { mutableStateOf(OrbState.Idle) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AiOrb(state = state, size = 260.dp)
        Spacer(Modifier.height(40.dp))
        Text(state.name)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbState.entries.forEach { s -> Button(onClick = { state = s }) { Text(s.name) } }
        }
    }
}