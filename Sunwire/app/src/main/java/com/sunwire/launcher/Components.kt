package com.sunwire.launcher

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal fun polar(c: Offset, r: Float, deg: Double) = Offset(
    c.x + r * cos(Math.toRadians(deg)).toFloat(),
    c.y + r * sin(Math.toRadians(deg)).toFloat(),
)

/** A hexagon with a point at the top, centred on [c]. */
internal fun hexPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val p = polar(c, r, i * 60.0 - 90.0)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** A single leaf growing from [base] toward the angle [deg], [len] long. */
internal fun leafPath(base: Offset, len: Float, deg: Double): Path {
    val tip = polar(base, len, deg)
    val mid = polar(base, len * 0.5f, deg)
    val left = polar(mid, len * 0.32f, deg - 90.0)
    val right = polar(mid, len * 0.32f, deg + 90.0)
    return Path().apply {
        moveTo(base.x, base.y)
        quadraticBezierTo(left.x, left.y, tip.x, tip.y)
        quadraticBezierTo(right.x, right.y, base.x, base.y)
        close()
    }
}

/**
 * A slowly turning halo. In Solarpunk it is a sunburst; in Cyberpunk it is a HUD reticle
 * (a dashed neon ring, two arcs and a hexagon).
 */
@Composable
fun Halo(
    modifier: Modifier = Modifier,
    rays: Int = 12,
    speed: Float = 12f, // seconds per full turn
    reverse: Boolean = false,
    animate: Boolean = LocalAnimations.current,
) {
    // Driven frame by frame so changing the spin speed takes effect immediately
    // (an infinite transition ignores a changed duration while it is running).
    val angle = remember { mutableFloatStateOf(0f) }
    val periodSecs by rememberUpdatedState(speed)
    val dir by rememberUpdatedState(if (reverse) -1f else 1f)
    if (animate) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val turns = (now - last) / 1_000_000_000f / (periodSecs * Neo.spinFactor)
                        angle.floatValue = (angle.floatValue + dir * turns * 360f) % 360f
                    }
                    last = now
                }
            }
        }
    }

    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        rotate(angle.floatValue, c) {
            if (cyber) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(r * 0.16f, r * 0.10f))
                drawCircle(glow.copy(alpha = 0.35f), r * 0.92f, c, style = Stroke(r * 0.10f, pathEffect = dash))
                drawCircle(lit, r * 0.92f, c, style = Stroke(r * 0.035f, pathEffect = dash))
                val arcTl = Offset(c.x - r * 0.72f, c.y - r * 0.72f)
                val arcSize = Size(r * 1.44f, r * 1.44f)
                drawArc(lit, -60f, 110f, false, arcTl, arcSize, style = Stroke(r * 0.06f, cap = StrokeCap.Round))
                drawArc(lit, 120f, 110f, false, arcTl, arcSize, style = Stroke(r * 0.06f, cap = StrokeCap.Round))
                drawPath(hexPath(c, r * 0.46f), Neo.AccentLight, style = Stroke(r * 0.05f, join = StrokeJoin.Round))
                drawCircle(lit, r * 0.12f, c)
            } else {
                val brush = Brush.radialGradient(listOf(Neo.AccentLight, Neo.Accent, Neo.AccentDark), center = c, radius = r)
                val half = 180.0 / rays * 0.45
                for (i in 0 until rays) {
                    val a = i * 360.0 / rays - 90.0
                    val tip = polar(c, r, a)
                    val left = polar(c, r * 0.55f, a - half)
                    val right = polar(c, r * 0.55f, a + half)
                    drawPath(Path().apply { moveTo(left.x, left.y); lineTo(tip.x, tip.y); lineTo(right.x, right.y); close() }, brush)
                }
                drawCircle(brush, r * 0.52f, c)
                drawCircle(Neo.Base.copy(alpha = 0.85f), r * 0.40f, c, style = Stroke(r * 0.04f))
                drawCircle(Neo.AccentLight.copy(alpha = 0.9f), r * 0.16f, c)
            }
        }
    }
}

/** Leaves along the bottom edge of the Solarpunk backdrop: x, y (as fractions), angle, length in dp. */
private val BACKDROP_LEAVES = listOf(
    floatArrayOf(0.05f, 1.00f, -75f, 70f), floatArrayOf(0.13f, 1.00f, -112f, 54f),
    floatArrayOf(0.21f, 1.00f, -58f, 40f), floatArrayOf(0.91f, 1.00f, -102f, 64f),
    floatArrayOf(0.82f, 1.00f, -68f, 46f), floatArrayOf(1.00f, 0.56f, -160f, 50f),
    floatArrayOf(0.00f, 0.34f, -18f, 44f),
)

/** The background behind every screen: your wallpaper, or a sunlit garden (Solarpunk) or a neon grid (Cyberpunk). */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Neo.Surface, Neo.Base)))) {
        val wall = Neo.wallpaper
        if (wall != null) {
            Image(wall, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Neo.wallDim)))
        } else {
            val cyber = Neo.cyber
            val glow = Neo.neon.glow
            val light = Neo.AccentLight
            val leaf = Neo.Leaf
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (cyber) {
                    // a neon floor grid running off to a horizon, a haze above it, and fine scanlines over everything
                    val horizon = h * 0.62f
                    val vx = w / 2f
                    for (i in -8..8) {
                        drawLine(
                            glow.copy(alpha = 0.07f), Offset(vx + i * 6.dp.toPx(), horizon), Offset(vx + i * w * 0.16f, h),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    var k = 1
                    while (true) {
                        val y = horizon + (h - horizon) * (k * k) / 100f // rows spread out toward the viewer
                        if (y > h) break
                        drawLine(glow.copy(alpha = 0.04f + 0.004f * k), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                        k++
                    }
                    val haze = 90.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, glow.copy(alpha = 0.10f)), startY = horizon - haze, endY = horizon),
                        topLeft = Offset(0f, horizon - haze), size = Size(w, haze),
                    )
                    val step = 3.dp.toPx()
                    var y = 0f
                    while (y < h) {
                        drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        y += step
                    }
                } else {
                    // morning light from the top corner and a few leaves reaching in from the edges
                    drawRect(Brush.radialGradient(listOf(light.copy(alpha = 0.13f), Color.Transparent), center = Offset(w, 0f), radius = size.maxDimension * 0.7f))
                    for (l in BACKDROP_LEAVES) {
                        drawPath(leafPath(Offset(l[0] * w, l[1] * h), l[3].dp.toPx(), l[2].toDouble()), leaf.copy(alpha = 0.07f))
                    }
                }
            }
            Halo(Modifier.size(280.dp).align(Alignment.TopEnd).offset(100.dp, (-60).dp).alpha(0.09f), rays = 14, speed = 48f)
            Halo(Modifier.size(220.dp).align(Alignment.CenterStart).offset((-110).dp, 40.dp).alpha(0.06f), rays = 11, speed = 36f, reverse = true)
            Halo(Modifier.size(200.dp).align(Alignment.BottomEnd).offset(70.dp, 60.dp).alpha(0.07f), rays = 10, speed = 30f, reverse = true)
        }
    }
}

/** Two small leaves on a short stem, growing from [at] toward [deg]. Sits in the corners of Solarpunk panels. */
private fun DrawScope.sprig(at: Offset, deg: Double, color: Color) {
    val s = 9.dp.toPx()
    drawLine(color.copy(alpha = 0.8f), at, polar(at, s * 1.2f, deg), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
    drawPath(leafPath(polar(at, s * 0.45f, deg), s, deg - 40.0), color)
    drawPath(leafPath(polar(at, s * 0.8f, deg), s * 0.85f, deg + 40.0), color.copy(alpha = 0.85f))
}

/** The Solarpunk finish for a panel: sunlight through the glass, a sheen along the top, and sprigs in two corners. */
private fun DrawScope.solarTrim(leaf: Color, light: Color) {
    val w = size.width
    val h = size.height
    drawRect(Brush.radialGradient(listOf(light.copy(alpha = 0.10f), Color.Transparent), center = Offset(w * 0.2f, 0f), radius = maxOf(w, h) * 0.8f))
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), startY = 0f, endY = h * 0.35f),
        size = Size(w, h * 0.35f),
    )
    val i = 9.dp.toPx()
    sprig(Offset(i, i), 45.0, leaf)
    sprig(Offset(w - i, h - i), 225.0, leaf)
    drawCircle(light.copy(alpha = 0.7f), 2.dp.toPx(), Offset(w - i, i))
    drawCircle(light.copy(alpha = 0.7f), 2.dp.toPx(), Offset(i, h - i))
}

/**
 * The Cyberpunk finish for a panel: scanlines, circuit traces, a couple of glitch slivers, a neon line set in
 * from the edge, and corner brackets. The neon follows the Neon glow colour in Settings.
 */
private fun DrawScope.cyberTrim(seed: Int, lit: Color, glow: Color) {
    val w = size.width
    val h = size.height
    val cut = 14.dp.toPx()

    // fine scanlines and a cool haze from one corner
    val step = 3.dp.toPx()
    var y = 0f
    while (y < h) {
        drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += step
    }
    drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.08f), Color.Transparent), Offset(w, 0f), minOf(maxOf(w, h), 320.dp.toPx()) * 0.8f))

    // circuit traces: in from an edge, one bend, ending in a pad. Different on every panel, but steady on each.
    val rnd = Random(seed)
    val trace = glow.copy(alpha = 0.16f)
    val sw = 1.dp.toPx()
    repeat(4) { n ->
        val fromLeft = n % 2 == 0
        val y0 = (0.2f + rnd.nextFloat() * 0.6f) * h
        val run = (0.08f + rnd.nextFloat() * 0.18f) * w
        val x0 = if (fromLeft) 0f else w
        val x1 = if (fromLeft) run else w - run
        val y1 = (y0 + (if (rnd.nextBoolean()) 1 else -1) * (10f + rnd.nextFloat() * 24f).dp.toPx()).coerceIn(cut, maxOf(cut, h - cut))
        val x2 = if (fromLeft) x1 + 12.dp.toPx() else x1 - 12.dp.toPx()
        drawPath(Path().apply { moveTo(x0, y0); lineTo(x1, y0); lineTo(x2, y1) }, trace, style = Stroke(sw))
        drawCircle(trace, 2.4.dp.toPx(), Offset(x2, y1), style = Stroke(sw))
    }
    repeat(2) {
        val gx = rnd.nextFloat() * w * 0.6f
        val gy = rnd.nextFloat() * h
        drawRect(lit.copy(alpha = 0.07f), topLeft = Offset(gx, gy), size = Size((20f + rnd.nextFloat() * 60f).dp.toPx(), 2.dp.toPx()))
    }

    // the neon edge, following the cut corners
    val i = 5.dp.toPx()
    val edge = Path().apply {
        moveTo(i + cut, i)
        lineTo(w - i, i)
        lineTo(w - i, h - i - cut)
        lineTo(w - i - cut, h - i)
        lineTo(i, h - i)
        lineTo(i, i + cut)
        close()
    }
    drawPath(edge, glow.copy(alpha = 0.22f), style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))
    drawPath(edge, lit.copy(alpha = 0.9f), style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))

    // corner brackets on the two square corners
    val o = 10.dp.toPx()
    val b = 10.dp.toPx()
    val bs = 2.dp.toPx()
    drawPath(Path().apply { moveTo(w - o - b, o); lineTo(w - o, o); lineTo(w - o, o + b) }, lit, style = Stroke(bs))
    drawPath(Path().apply { moveTo(o, h - o - b); lineTo(o, h - o); lineTo(o + b, h - o) }, lit, style = Stroke(bs))
}

/**
 * The panel behind every widget and dialog. Solarpunk: frosted glass in a green-to-gold frame, with sprigs.
 * Cyberpunk: dark chrome with cut corners, scanlines, circuit traces and a neon edge.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    fillAlpha: Float = 1f, // 1 = solid; lower lets the wallpaper show through the panel
    content: @Composable BoxScope.() -> Unit,
) {
    val cyber = Neo.cyber
    val shape = if (cyber) CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(18.dp)
    val seed = remember { Random.nextInt() }
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    val leaf = Neo.Leaf
    val light = Neo.AccentLight
    val frame = if (cyber) {
        Brush.verticalGradient(listOf(Neo.AccentLight, Neo.Accent, Neo.AccentDark))
    } else {
        Brush.linearGradient(listOf(Neo.Leaf, Neo.Accent, Neo.AccentDark))
    }
    Box(
        modifier
            .then(if (fillAlpha >= 0.99f) Modifier.shadow(6.dp, shape) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neo.Panel.copy(alpha = fillAlpha), Neo.Surface.copy(alpha = fillAlpha))))
            .border(if (cyber) 1.5.dp else 2.dp, frame, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind { if (cyber) cyberTrim(seed, lit, glow) else solarTrim(leaf, light) }
            .padding(padding),
        content = content,
    )
}

/** A large glowing button that sinks when pressed: a rounded pill in Solarpunk, cut corners and a neon rim in Cyberpunk. */
@Composable
fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val cyber = Neo.cyber
    val shape = if (cyber) CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp) else RoundedCornerShape(26.dp)
    val colors = if (pressed) listOf(Neo.AccentDark, Neo.Accent) else listOf(Neo.AccentLight, Neo.Accent, Neo.AccentDark)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .shadow(if (pressed) 1.dp else 6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(if (cyber) 1.5.dp else 2.dp, if (cyber) Neo.neon.lit else Neo.Leaf.copy(alpha = 0.8f), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Neo.Ink)
    }
}

/** Every app icon sits in the same ring, so the whole device gets one look: a leaf-and-sun bezel, or a neon HUD ring. */
@Composable
fun IconRing(icon: ImageBitmap, modifier: Modifier = Modifier, size: Dp = 62.dp) {
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            if (cyber) {
                drawCircle(Neo.Base, r * 0.94f, c)
                drawCircle(glow.copy(alpha = 0.30f), r * 0.88f, c, style = Stroke(r * 0.12f))
                drawCircle(lit, r * 0.88f, c, style = Stroke(r * 0.04f))
                for (i in 0 until 4) {
                    val deg = i * 90.0 + 45.0
                    drawLine(Neo.AccentLight, polar(c, r * 0.90f, deg), polar(c, r, deg), strokeWidth = r * 0.06f)
                }
            } else {
                drawCircle(Brush.sweepGradient(listOf(Neo.Leaf, Neo.Accent, Neo.AccentLight, Neo.Leaf), c), r, c)
                drawCircle(Neo.Base, r * 0.82f, c)
                for (i in 0 until 8) drawCircle(Neo.AccentLight.copy(alpha = 0.85f), r * 0.035f, polar(c, r * 0.91f, i * 45.0))
            }
        }
        Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.62f).clip(CircleShape))
    }
}

/**
 * A 270 degree meter. value is 0..1. dangerLow puts the red zone at the low end (for a battery).
 * Solarpunk fills a smooth arc from leaf green to sun gold; Cyberpunk lights neon segments.
 */
@Composable
fun ArcGauge(value: Float, modifier: Modifier = Modifier, dangerLow: Boolean = false) {
    val v by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val danger = if (dangerLow) v < 0.2f else v > 0.8f
        val arcR = r * 0.74f
        val thick = r * 0.15f
        val tl = Offset(c.x - arcR, c.y - arcR)
        val sz = Size(arcR * 2f, arcR * 2f)

        drawCircle(Brush.radialGradient(listOf(Neo.Panel, Neo.Base), center = c, radius = r), r, c)
        drawCircle(if (cyber) glow.copy(alpha = 0.35f) else Neo.AccentDark, r * 0.97f, c, style = Stroke(r * 0.04f))

        // the warning zone, just outside the track
        val zR = arcR + thick * 0.85f
        drawArc(
            Neo.Alert.copy(alpha = 0.9f),
            startAngle = if (dangerLow) 135f else 135f + 270f * 0.8f, sweepAngle = 270f * 0.2f, useCenter = false,
            topLeft = Offset(c.x - zR, c.y - zR), size = Size(zR * 2f, zR * 2f), style = Stroke(r * 0.05f),
        )

        if (cyber) {
            val segs = 18
            val gap = 3f
            val each = 270f / segs
            for (i in 0 until segs) {
                val on = (i + 0.5f) / segs <= v
                val start = 135f + i * each + gap / 2f
                if (on) drawArc(glow.copy(alpha = 0.25f), start, each - gap, false, tl, sz, style = Stroke(thick * 1.5f))
                drawArc(if (on) (if (danger) Neo.Alert else lit) else lit.copy(alpha = 0.10f), start, each - gap, false, tl, sz, style = Stroke(thick))
            }
        } else {
            drawArc(Neo.Text.copy(alpha = 0.10f), 135f, 270f, false, tl, sz, style = Stroke(thick, cap = StrokeCap.Round))
            if (v > 0.005f) {
                val fill = if (danger) Neo.Alert else lerp(Neo.Leaf, Neo.Accent, v)
                drawArc(fill, 135f, 270f * v, false, tl, sz, style = Stroke(thick, cap = StrokeCap.Round))
            }
        }

        for (i in 0..10) {
            val major = i % 5 == 0
            val deg = 135.0 + 27.0 * i
            drawLine(
                Neo.Text.copy(alpha = if (major) 0.7f else 0.35f),
                polar(c, r * (if (major) 0.42f else 0.48f), deg), polar(c, r * 0.54f, deg),
                strokeWidth = if (major) 4f else 2f,
            )
        }
        val pointer = if (cyber) lit else Neo.AccentLight
        drawLine(pointer, c, polar(c, r * 0.38f, 135.0 + 270.0 * v), strokeWidth = r * 0.04f, cap = StrokeCap.Round)
        drawCircle(pointer, r * 0.07f, c)
        drawCircle(Neo.Base, r * 0.03f, c)
    }
}

/** Analogue clock face. hour is 0..12 (fractional), minute is 0..60 (fractional). Sun rays for the hours, or a neon HUD ring. */
@Composable
fun AnalogClock(hour: Float, minute: Float, modifier: Modifier = Modifier) {
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Neo.Panel, Neo.Base), center = c, radius = r), r, c)
        if (cyber) {
            drawCircle(glow.copy(alpha = 0.3f), r * 0.95f, c, style = Stroke(r * 0.08f))
            drawCircle(lit, r * 0.95f, c, style = Stroke(r * 0.025f))
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val deg = i * 6.0 - 90.0
                drawLine(
                    if (major) lit else Neo.Muted.copy(alpha = 0.5f),
                    polar(c, r * (if (major) 0.72f else 0.80f), deg), polar(c, r * 0.86f, deg),
                    strokeWidth = if (major) 4f else 1.5f,
                )
            }
            drawLine(Neo.AccentLight, c, polar(c, r * 0.45f, hour / 12.0 * 360.0 - 90.0), strokeWidth = r * 0.075f, cap = StrokeCap.Round)
            val m = polar(c, r * 0.68f, minute / 60.0 * 360.0 - 90.0)
            drawLine(glow.copy(alpha = 0.35f), c, m, strokeWidth = r * 0.10f, cap = StrokeCap.Round)
            drawLine(lit, c, m, strokeWidth = r * 0.04f, cap = StrokeCap.Round)
            drawCircle(lit, r * 0.07f, c)
        } else {
            drawCircle(Brush.sweepGradient(listOf(Neo.Leaf, Neo.Accent, Neo.Leaf), c), r * 0.95f, c, style = Stroke(r * 0.05f))
            for (i in 0 until 12) {
                val major = i % 3 == 0
                val deg = i * 30.0 - 90.0
                drawLine(
                    Neo.AccentLight.copy(alpha = if (major) 0.95f else 0.6f),
                    polar(c, r * (if (major) 0.64f else 0.72f), deg), polar(c, r * 0.84f, deg),
                    strokeWidth = if (major) 5f else 3f, cap = StrokeCap.Round,
                )
            }
            drawLine(Neo.Text, c, polar(c, r * 0.45f, hour / 12.0 * 360.0 - 90.0), strokeWidth = r * 0.075f, cap = StrokeCap.Round)
            drawLine(Neo.Accent, c, polar(c, r * 0.68f, minute / 60.0 * 360.0 - 90.0), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
            drawCircle(Neo.Accent, r * 0.07f, c)
        }
        drawCircle(Neo.Base, r * 0.03f, c)
    }
}


/**
 * The floating orb. Tap = index, double tap = hub menu,
 * press and hold, then drag = move it. onMove gives the drag in dp.
 */
@Composable
fun HubOrb(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onMoveEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    // A press-and-hold ends with a finger lift that would otherwise count as a tap.
    val ignoreTapUntil = remember { LongArray(1) }
    val tapNow by rememberUpdatedState(onTap)
    val doubleNow by rememberUpdatedState(onDoubleTap)
    val moveNow by rememberUpdatedState(onMove)
    val endNow by rememberUpdatedState(onMoveEnd)
    val scale by animateFloatAsState(if (moving) 1.15f else if (pressed) 0.9f else 1f, label = "orb")
    val haptic = LocalHapticFeedback.current
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Box(
        modifier
            .size(72.dp)
            // gestures sit before the scaling so a drag follows the finger exactly
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onDoubleTap = { if (System.currentTimeMillis() >= ignoreTapUntil[0]) doubleNow() },
                    onTap = { if (System.currentTimeMillis() >= ignoreTapUntil[0]) tapNow() },
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        ignoreTapUntil[0] = Long.MAX_VALUE
                        moving = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        ignoreTapUntil[0] = System.currentTimeMillis() + 500L
                        moving = false
                        endNow()
                    },
                    onDragCancel = {
                        ignoreTapUntil[0] = System.currentTimeMillis() + 500L
                        moving = false
                        endNow()
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        moveNow(drag.x.toDp().value, drag.y.toDp().value)
                    },
                )
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (moving) 20.dp else 10.dp, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            if (cyber) {
                drawCircle(Neo.Base, r, c)
                drawCircle(glow.copy(alpha = 0.35f), r * 0.86f, c, style = Stroke(r * 0.12f))
                drawCircle(lit, r * 0.86f, c, style = Stroke(r * 0.04f))
                for (i in 0 until 4) {
                    val deg = i * 90.0
                    drawLine(Neo.AccentLight, polar(c, r * 0.92f, deg), polar(c, r, deg), strokeWidth = r * 0.06f)
                }
            } else {
                drawCircle(Brush.radialGradient(listOf(Neo.AccentLight, Neo.Accent, Neo.AccentDark), center = c, radius = r), r, c)
                drawCircle(Neo.Base, r * 0.80f, c)
                for (i in 0 until 8) {
                    val deg = i * 45.0 + 22.5
                    drawPath(leafPath(polar(c, r * 0.78f, deg), r * 0.20f, deg), Neo.Leaf)
                }
            }
        }
        Halo(Modifier.fillMaxSize(0.62f), rays = 10, speed = 9f)
    }
}


/**
 * A small round button with a single word inside. Solarpunk: a flower of petals. Cyberpunk: a neon hexagon.
 * It turns a notch when pressed.
 */
@Composable
fun NodeButton(
    text: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 92.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val turn by animateFloatAsState(
        if (pressed) 15f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "nodeTurn",
    )
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "nodeScale")
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Box(
        modifier
            .size(diameter)
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            val face = if (pressed) listOf(Neo.Accent, Neo.AccentDark) else listOf(Neo.AccentLight, Neo.Accent)
            if (cyber) {
                rotate(turn, c) {
                    drawPath(hexPath(c, r * 0.95f), glow.copy(alpha = 0.30f), style = Stroke(r * 0.10f, join = StrokeJoin.Round))
                    drawPath(hexPath(c, r * 0.95f), lit, style = Stroke(r * 0.035f, join = StrokeJoin.Round))
                }
                drawPath(hexPath(c, r * 0.82f), Neo.Base)
                drawPath(hexPath(c, r * 0.75f), Brush.radialGradient(face, center = c, radius = r * 0.75f))
            } else {
                val body = Brush.radialGradient(listOf(Neo.AccentLight, Neo.Accent, Neo.AccentDark), center = c, radius = r)
                rotate(turn, c) {
                    val petals = 10
                    for (i in 0 until petals) {
                        rotate(i * 360f / petals, c) {
                            drawOval(body, topLeft = Offset(c.x - r * 0.17f, c.y - r), size = Size(r * 0.34f, r * 0.5f))
                        }
                    }
                }
                drawCircle(Neo.Base, r * 0.72f, c)
                drawCircle(Brush.radialGradient(face, center = c, radius = r * 0.66f), r * 0.66f, c)
                drawCircle(Neo.Leaf.copy(alpha = 0.8f), r * 0.69f, c, style = Stroke(r * 0.025f))
            }
        }
        val fs = (diameter.value * 0.56f / (text.length * 0.62f)).coerceIn(9f, 15f)
        Text(
            text, fontSize = fs.sp, fontFamily = Neo.font, fontWeight = FontWeight.Bold,
            color = Neo.Ink, maxLines = 1, softWrap = false,
        )
    }
}

/** A centred, wrapping row of node buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}


// ───────────────────────── Holo clock ─────────────────────────


@Composable
private fun HoloDigit(digit: Char, w: Dp, h: Dp) {
    val glass = if (Neo.cyber) CutCornerShape(topStart = w * 0.22f, bottomEnd = w * 0.22f) else RoundedCornerShape(w * 0.28f)
    val fs = with(LocalDensity.current) { (h * 0.60f).toSp() }
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Box(
        Modifier.size(w, h).clip(glass)
            .background(Brush.verticalGradient(listOf(lerp(glow, Color.Black, 0.86f), Color(0xFF040507))))
            .border(1.5.dp, Brush.verticalGradient(listOf(Neo.AccentLight, Neo.AccentDark)), glass),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // scanlines, a diagonal glint on the glass, and the glowing emitter strip along the bottom
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(glow.copy(alpha = 0.07f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            val glint = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width * 0.38f, 0f)
                lineTo(size.width * 0.05f, size.height * 0.6f)
                lineTo(0f, size.height * 0.6f)
                lineTo(0f, size.height * 0.3f)
                close()
            }
            drawPath(glint, Color.White.copy(alpha = 0.05f))
            val by = size.height * 0.93f
            drawLine(glow.copy(alpha = 0.45f), Offset(size.width * 0.15f, by), Offset(size.width * 0.85f, by), strokeWidth = size.height * 0.05f, cap = StrokeCap.Round)
            drawLine(lit, Offset(size.width * 0.25f, by), Offset(size.width * 0.75f, by), strokeWidth = size.height * 0.015f, cap = StrokeCap.Round)
        }
        // a faint ghost of every segment behind the lit digit
        Text("8", fontSize = fs, fontFamily = FontFamily.SansSerif, color = glow.copy(alpha = 0.12f))
        if (digit != ' ') {
            Text(
                digit.toString(), fontSize = fs, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
                color = lit, style = TextStyle(shadow = Shadow(glow, Offset.Zero, 24f)),
            )
        }
    }
}

@Composable
private fun HoloColon(w: Dp, h: Dp) {
    val pulse = if (LocalAnimations.current) {
        val t = rememberInfiniteTransition(label = "holoColon")
        val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    } else 1f
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Canvas(Modifier.size(w, h)) {
        val s = size.width * 0.26f
        for (f in listOf(0.34f, 0.66f)) {
            val c = Offset(size.width / 2f, size.height * f)
            drawCircle(glow.copy(alpha = 0.35f * pulse), s * 1.9f, c)
            drawRect(lit.copy(alpha = pulse), topLeft = Offset(c.x - s / 2f, c.y - s / 2f), size = Size(s, s))
        }
    }
}

/** Four glowing holo digits (HHMM, a space gives a dark panel) with pulsing colon dots and a date line. */
@Composable
fun HoloClock(hhmm: String, date: String?, modifier: Modifier = Modifier, badge: String? = null) =
    BoxWithConstraints(modifier) {
        val mw = maxWidth
        val mh = maxHeight
        val gap = 4.dp
        val dateH = if (date != null && mh > 80.dp) 30.dp else 0.dp
        val reserve = if (badge != null) 26.dp else 0.dp
        val th = maxOf(minOf(mh - dateH, (mw - gap * 4 - reserve) / 2.64f), 20.dp)
        val tw = th * 0.56f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                HoloDigit(hhmm[0], tw, th)
                HoloDigit(hhmm[1], tw, th)
                HoloColon(tw * 0.4f, th)
                HoloDigit(hhmm[2], tw, th)
                HoloDigit(hhmm[3], tw, th)
                if (badge != null) {
                    Column(Modifier.height(th), verticalArrangement = Arrangement.Bottom) {
                        Text(
                            badge, fontSize = 11.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
                            color = Neo.neon.lit, style = TextStyle(shadow = Shadow(Neo.neon.glow, Offset.Zero, 16f)),
                        )
                    }
                }
            }
            if (dateH > 0.dp && date != null) {
                Text(date, fontSize = 13.sp, fontFamily = Neo.font, color = Neo.Text, maxLines = 1, modifier = Modifier.padding(top = 4.dp).wrapContentHeight(Alignment.Top, unbounded = true))
            }
        }
    }


// ───────────────────────── Compass, moon and sun drawings ─────────────────────────

/** Draws the outer ring shared by the compass and moon: green-to-gold in Solarpunk, neon in Cyberpunk. */
private fun DrawScope.dialRing(c: Offset, r: Float, cyber: Boolean, lit: Color, glow: Color) {
    if (cyber) {
        drawCircle(glow.copy(alpha = 0.3f), r * 0.95f, c, style = Stroke(r * 0.08f))
        drawCircle(lit, r * 0.95f, c, style = Stroke(r * 0.025f))
    } else {
        drawCircle(Brush.sweepGradient(listOf(Neo.Leaf, Neo.Accent, Neo.Leaf), c), r * 0.95f, c, style = Stroke(r * 0.06f))
    }
}

/** A compass. The card turns so your heading (degrees, 0 = north) sits at the top. */
@Composable
fun CompassRose(heading: Float, modifier: Modifier = Modifier) = BoxWithConstraints(modifier.aspectRatio(1f)) {
    val d = minOf(maxWidth, maxHeight)
    val fs = with(LocalDensity.current) { (d * 0.12f).toSp() }
    val inset = d * 0.15f
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Neo.Panel, Neo.Base), center = c, radius = r), r, c)
        dialRing(c, r, cyber, lit, glow)
    }
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = -heading }) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 0 until 36) {
                val major = i % 3 == 0
                val deg = i * 10.0 - 90.0
                drawLine(
                    Neo.Text.copy(alpha = if (major) 0.75f else 0.4f),
                    polar(c, r * (if (major) 0.76f else 0.80f), deg), polar(c, r * 0.85f, deg),
                    strokeWidth = if (major) 4f else 2f,
                )
            }
            val north = Path().apply {
                moveTo(c.x, c.y - r * 0.40f); lineTo(c.x - r * 0.09f, c.y); lineTo(c.x + r * 0.09f, c.y); close()
            }
            val south = Path().apply {
                moveTo(c.x, c.y + r * 0.40f); lineTo(c.x - r * 0.09f, c.y); lineTo(c.x + r * 0.09f, c.y); close()
            }
            drawPath(north, Neo.Alert)
            drawPath(south, Neo.Muted.copy(alpha = 0.7f))
            drawCircle(if (cyber) lit else Neo.Accent, r * 0.06f, c)
            drawCircle(Neo.Base, r * 0.025f, c)
        }
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = inset), fontSize = fs, fontFamily = Neo.font, fontWeight = FontWeight.Bold, color = Neo.Alert)
        Text("E", Modifier.align(Alignment.CenterEnd).padding(end = inset), fontSize = fs, fontFamily = Neo.font, fontWeight = FontWeight.Bold, color = Neo.Text)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = inset), fontSize = fs, fontFamily = Neo.font, fontWeight = FontWeight.Bold, color = Neo.Text)
        Text("W", Modifier.align(Alignment.CenterStart).padding(start = inset), fontSize = fs, fontFamily = Neo.font, fontWeight = FontWeight.Bold, color = Neo.Text)
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        val mark = Path().apply {
            moveTo(c.x, c.y - r * 0.80f); lineTo(c.x - r * 0.06f, c.y - r * 0.93f); lineTo(c.x + r * 0.06f, c.y - r * 0.93f); close()
        }
        drawPath(mark, if (cyber) lit else Neo.AccentLight)
    }
}

/** The moon as it looks tonight. ageDays is days since the new moon (0 to 29.53). */
@Composable
fun MoonDisc(ageDays: Double, modifier: Modifier = Modifier) {
    val cyber = Neo.cyber
    val lit = Neo.neon.lit
    val glow = Neo.neon.glow
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val mr = r * 0.84f
        val f = (ageDays / 29.530588853).toFloat().coerceIn(0f, 1f)
        drawCircle(Neo.Base, r, c)
        dialRing(c, r, cyber, lit, glow)
        drawCircle(Color(0xFF1A1F2A), mr, c) // the dark side
        val waxing = f <= 0.5f
        val q = if (waxing) f else 1f - f // 0 = new, 0.5 = full
        val k = cos(2.0 * Math.PI * q).toFloat() // 1 = new ... -1 = full
        val rx = maxOf(abs(k) * mr, 0.5f)
        val litSide = Path().apply {
            arcTo(Rect(c.x - mr, c.y - mr, c.x + mr, c.y + mr), -90f, 180f, true)
            arcTo(Rect(c.x - rx, c.y - mr, c.x + rx, c.y + mr), 90f, if (k > 0f) -180f else 180f, false)
            close()
        }
        scale(if (waxing) 1f else -1f, 1f, c) {
            clipPath(litSide) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFF4F1E2), Color(0xFFCFD3C4)), center = Offset(c.x - mr * 0.2f, c.y - mr * 0.2f), radius = mr * 1.4f),
                    mr, c,
                )
                val craters = listOf(
                    Triple(-0.30f, -0.25f, 0.16f), Triple(0.25f, -0.35f, 0.10f), Triple(0.10f, 0.20f, 0.20f),
                    Triple(-0.35f, 0.35f, 0.09f), Triple(0.45f, 0.30f, 0.07f),
                )
                for ((dx, dy, rr) in craters) drawCircle(Color(0x22405040), mr * rr, Offset(c.x + mr * dx, c.y + mr * dy))
            }
        }
    }
}

/** The sun's path across the sky. progress is 0 at sunrise to 1 at sunset, or null at night. */
@Composable
fun SunArc(progress: Float?, modifier: Modifier = Modifier) {
    val sun = if (Neo.cyber) Neo.neon.lit else Neo.AccentLight
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * 0.82f
        val rad = minOf(w * 0.42f, baseY - 6f)
        val cx = w / 2f
        drawLine(Neo.AccentDark, Offset(cx - rad - 10f, baseY), Offset(cx + rad + 10f, baseY), strokeWidth = 3f, cap = StrokeCap.Round)
        drawArc(
            Neo.Accent, 180f, 180f, false, Offset(cx - rad, baseY - rad), Size(rad * 2f, rad * 2f),
            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
        if (progress != null) {
            val th = Math.PI * (1.0 - progress)
            val p = Offset(cx + rad * cos(th).toFloat(), baseY - rad * sin(th).toFloat())
            drawCircle(sun.copy(alpha = 0.25f), 18f, p)
            for (i in 0 until 8) drawLine(sun.copy(alpha = 0.8f), polar(p, 12f, i * 45.0), polar(p, 17f, i * 45.0), strokeWidth = 2f, cap = StrokeCap.Round)
            drawCircle(sun, 9f, p)
        }
    }
}
