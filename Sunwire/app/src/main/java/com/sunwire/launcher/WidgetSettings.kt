package com.sunwire.launcher

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject
import kotlin.math.roundToInt

/** Settings that belong to one widget only. The defaults mean "behave as before". */
data class WidgetSettings(
    val clock: Int = 0,             // 0 = follow the phone, 12 or 24
    val fahrenheit: Boolean = false,
    val title: String = "",         // blank = the widget's own title
    val opacity: Float = 1f,        // 0.2 to 1: how solid the panel behind the widget is
    val cells: List<String> = DEFAULT_CELLS, // Energy cells widget: which readings, in order
)

internal fun encodeSettings(s: WidgetSettings): String =
    JSONObject().put("c", s.clock).put("f", s.fahrenheit).put("t", s.title).put("o", s.opacity.toDouble()).put("v", s.cells.joinToString(",")).toString()

internal fun decodeSettings(raw: String?): WidgetSettings {
    if (raw == null) return WidgetSettings()
    return try {
        val o = JSONObject(raw)
        WidgetSettings(
            clock = o.optInt("c", 0).let { if (it == 12 || it == 24) it else 0 },
            fahrenheit = o.optBoolean("f", false),
            title = o.optString("t", ""),
            opacity = o.optDouble("o", 1.0).toFloat().coerceIn(0.2f, 1f),
            cells = o.optString("v", "").split(",")
                .filter { k -> CELL_METRICS.any { it.key == k } }.distinct().take(MAX_CELLS)
                .ifEmpty { DEFAULT_CELLS },
        )
    } catch (e: Exception) {
        WidgetSettings()
    }
}

/** Widgets that show a time, so a 12 or 24 hour choice makes sense. */
internal fun WType.hasClockFormat() = this == WType.HOLO || this == WType.HOLO_STRIP || this == WType.RING || this == WType.DIGITAL || this == WType.SUN

/** Widgets that show a temperature. */
internal fun WType.hasTemperature() = this == WType.CORE || this == WType.CELLS

/** Widgets with a heading you can rename. Folders and link folders are renamed from inside. */
internal fun WType.hasTitle() = this == WType.CHARGE || this == WType.VAULT || this == WType.BAROMETER ||
    this == WType.DATAPAD || this == WType.DOCK || this == WType.PICKS

internal fun WType.defaultTitle(): String = when (this) {
    WType.CHARGE -> "Solar charge"
    WType.VAULT -> "Data vault"
    WType.BAROMETER -> "Barometer"
    WType.DATAPAD -> "Datapad"
    WType.DOCK -> "App dock"
    WType.PICKS -> "Netrunner's picks"
    else -> title
}

/** The settings pop-up shown when you tap ⚙ on a widget in Arrange mode. Every change is saved straight away. */
@Composable
fun WidgetSettingsDialog(w: WidgetItem, board: WidgetBoard, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cur = board.settingsOf(w.id)
    var title by remember(w.id) { mutableStateOf(cur.title) }
    fun change(f: (WidgetSettings) -> WidgetSettings) = board.setSettings(w.id, f(board.settingsOf(w.id)))

    Dialog(onDismissRequest = onClose) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(w.type.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Neo.AccentLight)
                Text("These settings belong to this widget only.", style = MaterialTheme.typography.labelLarge, color = Neo.Muted)

                if (w.type.hasClockFormat()) {
                    Text("Hour format", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("Phone", cur.clock == 0, Modifier.weight(1f)) { change { it.copy(clock = 0) } }
                        OptionChip("12-hour", cur.clock == 12, Modifier.weight(1f)) { change { it.copy(clock = 12) } }
                        OptionChip("24-hour", cur.clock == 24, Modifier.weight(1f)) { change { it.copy(clock = 24) } }
                    }
                }

                if (w.type == WType.MUSIC) {
                    Text("Neon colour", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    Text(
                        "The neon follows your Neon glow colour. Change it in Settings > Look > Neon glow. " +
                            "Resize the widget to change its shape: wide and short is a strip, taller and narrower is a square card.",
                        style = MaterialTheme.typography.labelLarge, color = Neo.Muted,
                    )
                    Text("Track info", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    Text(
                        "Play, pause, next and previous always work. The track name, artist and progress line need Notification access, " +
                            "which Android asks you to switch on once. On some phones you may first need to allow restricted settings " +
                            "for Sunwire in its App info.",
                        style = MaterialTheme.typography.labelLarge, color = Neo.Muted,
                    )
                    NodeRow { NodeButton("Notification access") { openListenerSettings(ctx) } }
                }

                if (w.type == WType.CELLS) {
                    Text("Readings", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    Text(
                        "Tap to add or remove a cell. They appear in the order you pick them. Choose 1 to $MAX_CELLS.",
                        style = MaterialTheme.typography.labelLarge, color = Neo.Muted,
                    )
                    CELL_METRICS.chunked(3).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { m ->
                                OptionChip(m.label, m.key in cur.cells, Modifier.weight(1f)) {
                                    change { s ->
                                        val now = board.settingsOf(w.id).cells
                                        s.copy(
                                            cells = when {
                                                m.key in now -> if (now.size > 1) now - m.key else now // keep at least one
                                                now.size < MAX_CELLS -> now + m.key
                                                else -> now
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (w.type == WType.CORE || (w.type == WType.CELLS && "heat" in cur.cells)) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("Celsius", !cur.fahrenheit, Modifier.weight(1f)) { change { it.copy(fahrenheit = false) } }
                        OptionChip("Fahrenheit", cur.fahrenheit, Modifier.weight(1f)) { change { it.copy(fahrenheit = true) } }
                    }
                }

                if (w.type.hasTitle()) {
                    Text("Title", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40); change { s -> s.copy(title = title) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(w.type.defaultTitle()) },
                        colors = neoFieldColors(),
                    )
                }

                Text("Opacity " + (cur.opacity * 100f).roundToInt() + "%", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                Text(
                    "How solid the panel behind the widget is. Lower it to let your wallpaper show through.",
                    style = MaterialTheme.typography.labelLarge, color = Neo.Muted,
                )
                Slider(
                    value = cur.opacity,
                    onValueChange = { v -> change { it.copy(opacity = v) } },
                    valueRange = 0.2f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Neo.AccentLight, activeTrackColor = Neo.Accent, inactiveTrackColor = Neo.Base,
                    ),
                )

                if (board.pageCount > 1) {
                    Text("Move to page", style = MaterialTheme.typography.titleMedium, color = Neo.Text)
                    NodeRow {
                        for (p in 0 until board.pageCount) {
                            if (p != board.page) {
                                NodeButton("Page ${p + 1}") {
                                    if (board.moveToPage(w.id, p)) {
                                        onClose()
                                    } else {
                                        Toast.makeText(ctx, "No room left on page ${p + 1}.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }

                NodeRow {
                    NodeButton("Reset") {
                        title = ""
                        board.setSettings(w.id, WidgetSettings())
                    }
                    NodeButton("Done", onClick = onClose)
                }
            }
        }
    }
}
