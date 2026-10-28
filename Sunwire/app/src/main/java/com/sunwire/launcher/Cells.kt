package com.sunwire.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

/** One thing an energy cell can measure. */
class CellMetric(val key: String, val label: String, val blurb: String)

/** Every reading an Energy cells widget can show, in the order they are offered. */
val CELL_METRICS = listOf(
    CellMetric("battery", "Charge", "Battery charge"),
    CellMetric("storage", "Vault", "Storage used"),
    CellMetric("memory", "Memory", "RAM in use"),
    CellMetric("heat", "Heat", "Battery temperature"),
    CellMetric("pressure", "Pressure", "Air pressure (needs a sensor)"),
    CellMetric("day", "Day", "How much of today has passed"),
)

val DEFAULT_CELLS = listOf("battery", "storage", "memory")

/** The most cells one widget can hold. */
const val MAX_CELLS = 6

/** What one cell shows. [bad] says how worrying the level is, 0 (fine) to 1 (alarming), or null for a neutral reading. */
private class Reading(val label: String, val fill: Float, val text: String, val bad: Float?)

private fun dayFraction(): Float = LocalTime.now().toSecondOfDay() / 86_400f

/**
 * A stack of glowing cells that fill from left to right. Which readings it shows is chosen with the settings
 * button on the widget in Arrange mode. They are drawn in the order picked.
 */
@Composable
fun CellsContent(keys: List<String>, fahrenheit: Boolean) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    var battery by remember { mutableFloatStateOf(batteryLevel(ctx)) }
    var storage by remember { mutableFloatStateOf(storageUse().first) }
    var mem by remember { mutableFloatStateOf(0f) }
    var heat by remember { mutableFloatStateOf(-1f) } // degrees C, -1 = unknown
    var day by remember { mutableFloatStateOf(dayFraction()) }
    var hpa by remember { mutableFloatStateOf(0f) }
    var hasBaro by remember { mutableStateOf(true) }
    val wantBaro = "pressure" in keys

    // refresh every few seconds while the home screen is showing
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            battery = batteryLevel(ctx)
            storage = storageUse().first
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mem = if (mi.totalMem > 0L) 1f - mi.availMem.toFloat() / mi.totalMem.toFloat() else 0f
            val t = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            heat = if (t == null || t == Int.MIN_VALUE) -1f else t / 10f
            day = dayFraction()
            delay(5000)
        }
    }

    // the pressure sensor is only switched on while a Pressure cell is chosen
    DisposableEffect(active, wantBaro) {
        if (!wantBaro) {
            onDispose { }
        } else {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
            if (sensor == null) {
                hasBaro = false
                onDispose { }
            } else {
                hasBaro = true
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) { hpa = e.values[0] }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                if (active) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                onDispose { sm.unregisterListener(listener) }
            }
        }
    }

    val readings = keys.mapNotNull { k ->
        when (k) {
            "battery" -> Reading("Charge", battery, "${(battery * 100).roundToInt()}%", 1f - battery)
            "storage" -> Reading("Vault", storage, "${(storage * 100).roundToInt()}% full", storage)
            "memory" -> Reading("Memory", mem, "${(mem * 100).roundToInt()}% used", mem)
            "heat" -> if (heat < 0f) {
                Reading("Heat", 0f, "n/a", null)
            } else {
                val f = ((heat - 20f) / 40f).coerceIn(0f, 1f)
                Reading("Heat", f, if (fahrenheit) "${(heat * 9f / 5f + 32f).roundToInt()}°F" else "${heat.roundToInt()}°C", f)
            }
            "pressure" -> when {
                !hasBaro -> Reading("Pressure", 0f, "no sensor", null)
                hpa <= 0f -> Reading("Pressure", 0f, "reading...", null)
                else -> Reading("Pressure", ((hpa - 950f) / 100f).coerceIn(0f, 1f), "${hpa.roundToInt()} hPa", null)
            }
            "day" -> Reading("Day", day, "${(day * 100).roundToInt()}% gone", null)
            else -> null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val n = readings.size.coerceAtLeast(1)
        val gap = 6.dp
        val rowH = maxOf((maxHeight - gap * (n - 1)) / n, 10.dp)
        val fs = minOf(rowH.value * 0.5f, maxWidth.value * 0.05f).coerceIn(8f, 15f)
        val tubeH = minOf(rowH * 0.85f, 30.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            readings.forEach { r ->
                Row(Modifier.fillMaxWidth().height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.label, Modifier.weight(0.27f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelLarge, fontSize = fs.sp, color = Neo.Text,
                    )
                    CellTube(r.fill, r.bad, Modifier.weight(0.45f).padding(horizontal = 6.dp).height(tubeH))
                    Text(
                        r.text, Modifier.weight(0.28f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelLarge, fontSize = fs.sp, color = Neo.Muted,
                    )
                }
            }
        }
    }
}

/**
 * One glass cell. The charge rises from the left and turns from leaf green to sun gold, then alert red, as things
 * get worrying. Neutral readings glow gold in Solarpunk and in your neon colour in Cyberpunk, where the cell
 * is also split into segments.
 */
@Composable
private fun CellTube(fill: Float, bad: Float?, modifier: Modifier) {
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), tween(700), label = "cell")
    val cyber = Neo.cyber
    val liquid = when {
        bad == null -> if (cyber) Neo.neon.lit else Neo.AccentLight
        bad < 0.6f -> Neo.Leaf
        bad < 0.85f -> Neo.AccentLight
        else -> Neo.Alert
    }
    val glow = Neo.neon.glow
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val rim = maxOf(2f, h * 0.1f)
        val half = rim / 2f
        val r = h / 2f
        // the cell itself, inset so the rim is not cut off at the edge
        val glass = Path().apply { addRoundRect(RoundRect(half, half, w - half, h - half, r - half, r - half)) }
        drawPath(glass, Neo.Base.copy(alpha = 0.85f))

        // the charge, clipped to the inside of the cell
        val inset = rim
        val inner = Path().apply {
            addRoundRect(RoundRect(inset, inset, w - inset, h - inset, maxOf(0f, r - inset), maxOf(0f, r - inset)))
        }
        clipPath(inner) {
            val right = inset + (w - 2f * inset) * level
            if (level > 0.005f) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(lerp(liquid, Color.White, 0.25f), liquid, lerp(liquid, Color.Black, 0.35f)),
                        startY = inset, endY = h - inset,
                    ),
                    topLeft = Offset(inset, inset), size = Size(right - inset, h - 2f * inset),
                )
                if (cyber) {
                    // dark gaps split the charge into segments
                    val seg = maxOf(6f, h * 0.55f)
                    var x = inset + seg
                    while (x < right) {
                        drawRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(x, inset), size = Size(maxOf(1.5f, h * 0.06f), h - 2f * inset))
                        x += seg
                    }
                } else {
                    // a few rising bubbles
                    for (i in 0 until 4) {
                        val bx = inset + (right - inset) * (0.15f + 0.22f * i)
                        val by = h * (if (i % 2 == 0) 0.38f else 0.62f)
                        if (bx < right - h * 0.1f) drawCircle(Color.White.copy(alpha = 0.35f), h * 0.07f, Offset(bx, by))
                    }
                }
                // the bright leading edge of the charge
                drawRect(Color.White.copy(alpha = 0.4f), topLeft = Offset(right - 2f, inset), size = Size(2f, h - 2f * inset))
            }
        }

        // tick marks at a quarter, half and three quarters
        for (i in 1..3) {
            val x = w * i / 4f
            drawLine(Neo.Text.copy(alpha = 0.28f), Offset(x, h * 0.72f), Offset(x, h * 0.9f), strokeWidth = 1.2f)
        }
        // glass shine
        drawRoundRect(
            Color.White.copy(alpha = 0.10f),
            topLeft = Offset(r * 0.6f, h * 0.14f), size = Size(maxOf(0f, w - r * 1.2f), h * 0.16f),
            cornerRadius = CornerRadius(h * 0.08f),
        )
        // rim: green-to-gold in Solarpunk, chrome with a neon halo in Cyberpunk
        if (cyber) drawPath(glass, glow.copy(alpha = 0.28f), style = Stroke(width = rim * 2.4f))
        drawPath(
            glass,
            if (cyber) {
                Brush.verticalGradient(listOf(Neo.AccentLight, Neo.Accent, Neo.AccentDark), startY = 0f, endY = h)
            } else {
                Brush.horizontalGradient(listOf(Neo.Leaf, Neo.Accent), startX = 0f, endX = w)
            },
            style = Stroke(width = rim),
        )
    }
}
