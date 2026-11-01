package de.aniworld.tv.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.aniworld.tv.R
import de.aniworld.tv.platform.AppLog
import de.aniworld.tv.ui.theme.AccentBlue
import de.aniworld.tv.ui.theme.AccentCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

private const val INTRO_DURATION_MS = 2950

// Zeitpunkte im Intro-Ton: Bass-Schlag, wenn der Schriftzug herauskommt, danach wobbelt der Bass aus
private const val BASS_HIT_MS = 1420
private const val WOBBLE_START_MS = 1920f

private val TriangleWidth = 70.dp
private val TriangleHeight = 84.dp
private val WordmarkGap = 18.dp

// Leichtes Überschwingen, damit das Dreieck "aufspringt"
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Fortschritt 0..1 des Abschnitts zwischen [startMs] und [endMs] der Animation */
private fun phase(timeMs: Float, startMs: Int, endMs: Int, easing: Easing = FastOutSlowInEasing): Float =
    easing.transform(((timeMs - startMs) / (endMs - startMs)).coerceIn(0f, 1f))

/** Drehwinkel des Dreiecks: drei Umdrehungen, zum Ende hin langsamer */
private fun spinAngle(timeMs: Float): Float = 1080f * phase(timeMs, 0, 1400)

/** Stoß beim Bass-Schlag: schnell rauf, dann ausklingen (0..1) */
private fun bassHit(timeMs: Float): Float =
    phase(timeMs, BASS_HIT_MS, BASS_HIT_MS + 60) * (1f - phase(timeMs, BASS_HIT_MS + 60, BASS_HIT_MS + 400))

/** Leuchten im Takt des Tons: Wummern bei jeder halben Umdrehung, Schlag, dann Wobbeln (0..1) */
private fun glowPulse(timeMs: Float): Float {
    val wub = ((1f - cos(spinAngle(timeMs) * PI.toFloat() / 180f)) / 2f).pow(6)
    val wobble = if (timeMs >= WOBBLE_START_MS) {
        0.25f * (1f - cos(2f * PI.toFloat() * 5f * (timeMs - WOBBLE_START_MS) / 1000f))
    } else 0f
    return maxOf(wub, bassHit(timeMs), wobble)
}

/** Play-Dreieck mit runden Ecken als ein einziger Pfad, damit es beim Ausblenden keine dunkleren Kanten bekommt */
private fun roundedTriangle(corners: List<Offset>, radius: Float): Path = Path().apply {
    corners.forEachIndexed { i, corner ->
        val prev = corners[(i + corners.size - 1) % corners.size]
        val next = corners[(i + 1) % corners.size]
        val from = corner + (prev - corner) * (radius / (prev - corner).getDistance())
        val to = corner + (next - corner) * (radius / (next - corner).getDistance())
        if (i == 0) moveTo(from.x, from.y) else lineTo(from.x, from.y)
        quadraticTo(corner.x, corner.y, to.x, to.y)
    }
    close()
}

/** Lädt den Intro-Ton; der Player gibt sich nach dem Abspielen selbst frei */
private fun loadIntroSound(context: Context): MediaPlayer? =
    runCatching { MediaPlayer.create(context, R.raw.intro_sound) }
        .onFailure { AppLog.w("Intro", "Intro-Ton nicht ladbar", it) }
        .getOrNull()
        ?.apply {
            setOnCompletionListener { it.release() }
            setOnErrorListener { player, _, _ -> player.release(); true }
        }

/**
 * Start-Animation beim Öffnen der App: Das Play-Dreieck springt in der Bildmitte auf und dreht sich dreimal,
 * dann rückt es nach links und der Schriftzug "ABI TV" kommt hinter ihm nach rechts heraus.
 * Zum Schluss wird das Logo größer, blendet aus und gibt die Startseite frei. Dazu läuft ein passender Ton.
 * Nimmt keinen Fokus, damit die Startseite dahinter ihren Fokus behält; Klicks und Tipps schluckt sie.
 */
@Composable
fun IntroAnimation(onFinished: () -> Unit) {
    val time = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        // Ton vorher laden, damit Ton und Bild gleichzeitig starten
        withContext(Dispatchers.IO) { loadIntroSound(appContext) }?.start()
        time.animateTo(INTRO_DURATION_MS.toFloat(), tween(INTRO_DURATION_MS, easing = LinearEasing))
        currentOnFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020306)),
                        radius = size.maxDimension * 0.7f
                    ),
                    alpha = 1f - phase(time.value, 2550, 2950)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    // Ohne Ebenen-Alpha, sonst würde das Leuchten an den Rändern der Zeile abgeschnitten
                    val t = time.value
                    // Erst steht das Dreieck in der Bildmitte, dann rückt es nach links und macht dem Schriftzug Platz
                    val toCenter = size.width / 2f - TriangleWidth.toPx() / 3f
                    translationX = (1f - phase(t, 1350, 2000)) * toCenter
                    val zoom = 1f + 0.15f * phase(t, 2450, 2950, FastOutLinearInEasing)
                    // Kurzer Stoß beim Bass-Schlag
                    val punch = 1f + 0.08f * bassHit(t)
                    scaleX = zoom * punch
                    scaleY = zoom * punch
                }
                .drawBehind {
                    // Leuchten hinter dem Dreieck, pulsiert mit dem Bass
                    val t = time.value
                    val pulse = glowPulse(t)
                    val center = Offset(TriangleWidth.toPx() / 3f, size.height / 2f)
                    val radius = TriangleHeight.toPx() * (1.3f + 0.35f * pulse)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentCyan.copy(alpha = 0.45f), AccentBlue.copy(alpha = 0.15f), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center,
                        alpha = (phase(t, 300, 1100) + 0.7f * pulse).coerceAtMost(1f) * (1f - phase(t, 2450, 2850))
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(TriangleWidth, TriangleHeight)
                    // Vor dem Schriftzug, damit der hinter dem Dreieck hervorkommt
                    .zIndex(1f)
                    .graphicsLayer {
                        val t = time.value
                        val scale = 0.3f + 0.7f * phase(t, 0, 600, PopEasing)
                        scaleX = scale
                        scaleY = scale
                        rotationZ = spinAngle(t)
                        // Im Schwerpunkt des Dreiecks drehen, so bleibt es beim Drehen an seinem Platz
                        transformOrigin = TransformOrigin(1f / 3f, 0.5f)
                    }
            ) {
                val t = time.value
                val triangle = roundedTriangle(
                    corners = listOf(Offset(0f, 0f), Offset(size.width, size.height / 2f), Offset(0f, size.height)),
                    radius = size.width * 0.1f
                )
                drawPath(
                    path = triangle,
                    brush = Brush.linearGradient(
                        colors = listOf(AccentBlue, AccentCyan),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f)
                    ),
                    alpha = phase(t, 0, 350) * (1f - phase(t, 2450, 2850))
                )
            }

            Spacer(modifier = Modifier.width(WordmarkGap))

            Box(
                modifier = Modifier.drawWithContent {
                    // Erst ab dem breiten Teil des Dreiecks zeichnen: Links davon steckt der Schriftzug noch ganz dahinter
                    clipRect(left = -(WordmarkGap.toPx() + TriangleWidth.toPx() * 0.8f)) {
                        this@drawWithContent.drawContent()
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            val t = time.value
                            val hidden = WordmarkGap.toPx() + TriangleWidth.toPx() * 0.8f + size.width
                            translationX = -(1f - phase(t, 1350, 2000)) * hidden
                            alpha = phase(t, 1350, 1650) * (1f - phase(t, 2450, 2800))
                            // Eigene Ebene, damit der Lichtstreifen nur auf dem Schriftzug liegt
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            val shine = phase(time.value, 1900, 2450)
                            if (shine > 0f && shine < 1f) {
                                val x = size.width * (-0.3f + 1.6f * shine)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                                        start = Offset(x - size.width * 0.18f, 0f),
                                        end = Offset(x + size.width * 0.18f, size.height)
                                    ),
                                    blendMode = BlendMode.SrcAtop
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.horizontalGradient(listOf(AccentBlue, AccentCyan)))
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "ABI",
                            color = Color.White,
                            fontSize = 36.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "TV",
                        color = AccentCyan,
                        fontSize = 36.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
