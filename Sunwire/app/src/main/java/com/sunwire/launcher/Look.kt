package com.sunwire.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** The colours and style part of a preset. The three custom colours are carried so a shared preset can use them. */
data class LookColors(
    val style: String,
    val palette: String, // a palette name, or "Custom"
    val neon: String,    // a neon colour name, or "Custom"
    val customAccent: Int,
    val customBase: Int,
    val customNeon: Int,
)

/**
 * What a preset can carry besides its layout. Either part can be left out.
 * [wall]: null = leave the wallpaper alone, "" = clear it, otherwise the name of the saved picture.
 */
data class Look(val colors: LookColors?, val wall: String?, val dim: Float)

/** A short description for the presets list, or "" if the preset is layout only. */
fun lookLabel(look: Look?): String {
    if (look == null) return ""
    val parts = mutableListOf<String>()
    if (look.colors != null) parts += "colours"
    if (look.wall != null) parts += if (look.wall.isEmpty()) "plain background" else "wallpaper"
    return parts.joinToString(" + ")
}

internal fun encodeLook(l: Look?): JSONObject? {
    if (l == null) return null
    val o = JSONObject()
    l.colors?.let { c ->
        o.put(
            "c",
            JSONObject().put("s", c.style).put("p", c.palette).put("n", c.neon)
                .put("ca", c.customAccent).put("cb", c.customBase).put("cn", c.customNeon),
        )
    }
    l.wall?.let { o.put("w", it) }
    o.put("d", l.dim.toDouble())
    return o
}

internal fun decodeLook(o: JSONObject?): Look? {
    if (o == null) return null
    val c = o.optJSONObject("c")?.let { x ->
        LookColors(
            x.optString("s", "solar"), x.optString("p", "Sun"), x.optString("n", "Gold"),
            x.optInt("ca", 0xFFE8B83A.toInt()), x.optInt("cb", 0xFF1E2E24.toInt()), x.optInt("cn", 0xFFFFC21A.toInt()),
        )
    }
    val w = if (o.has("w")) o.optString("w", "") else null
    if (c == null && w == null) return null
    return Look(c, w, o.optDouble("d", 0.45).toFloat().coerceIn(0f, 0.85f))
}

/** Makes a look from a shared code safe to use: no wallpaper (pictures are never shared), and only known names. */
internal fun sanitizeLook(l: Look?): Look? {
    if (l == null) return null
    val c = l.colors ?: return null
    val palette = if (c.palette == "Custom" || Palettes.any { it.name == c.palette }) c.palette else "Sun"
    val neon = if (c.neon == "Custom" || NeonColors.any { it.name == c.neon }) c.neon else "Gold"
    val style = if (c.style == "cyber") "cyber" else "solar"
    return Look(c.copy(style = style, palette = palette, neon = neon), null, l.dim)
}

internal fun hsvOf(c: Color): FloatArray {
    val a = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), a)
    return a
}

internal fun colorOf(hsv: FloatArray): Color = Color(android.graphics.Color.HSVToColor(hsv))

/**
 * Three sliders (hue, saturation, brightness) that mix one colour. The saturation and brightness ranges are limited
 * by the caller so the result stays readable. [onChange] fires as you slide, [onDone] when you let go.
 * [resetKey] makes the sliders start again from [start], for example when you pick one of the ready-made palettes.
 */
@Composable
fun ColourEditor(
    label: String,
    resetKey: Any,
    start: Color,
    satMin: Float, satMax: Float, valMin: Float, valMax: Float,
    onChange: (Color) -> Unit,
    onDone: () -> Unit,
) {
    var hsv by remember(resetKey) { mutableStateOf(hsvOf(start)) }
    fun push(h: FloatArray) {
        hsv = h
        onChange(colorOf(h))
    }
    val sat = hsv[1].coerceIn(satMin, satMax)
    val bri = hsv[2].coerceIn(valMin, valMax)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(2.dp))
            Spacer(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(colorOf(floatArrayOf(hsv[0], sat, bri)))
                    .border(1.dp, Neo.AccentDark, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = Neo.Text)
        }
        EditorSlider("Hue", hsv[0], 0f, 360f, onDone) { push(floatArrayOf(it, sat, bri)) }
        EditorSlider("Strength", sat, satMin, satMax, onDone) { push(floatArrayOf(hsv[0], it, bri)) }
        EditorSlider("Brightness", bri, valMin, valMax, onDone) { push(floatArrayOf(hsv[0], sat, it)) }
    }
}

@Composable
private fun EditorSlider(label: String, value: Float, min: Float, max: Float, onDone: () -> Unit, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(86.dp), style = MaterialTheme.typography.labelLarge, color = Neo.Muted)
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Neo.AccentLight, activeTrackColor = Neo.Accent, inactiveTrackColor = Neo.Base,
            ),
        )
    }
}
