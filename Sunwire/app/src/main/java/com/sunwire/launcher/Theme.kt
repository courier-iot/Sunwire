package com.sunwire.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * One complete colour scheme: three background depths, the accent in three shades,
 * an alert colour, a leaf green, and the text colours.
 */
class Palette(
    val name: String,
    val base: Color, val surface: Color, val panel: Color,
    val accent: Color, val accentLight: Color, val accentDark: Color,
    val alert: Color, val leaf: Color,
    val text: Color, val ink: Color, val muted: Color,
)

val Palettes = listOf(
    // Sunlight gold over a deep forest night: the signature Solarpunk look
    Palette(
        "Sun",
        Color(0xFF0E1410), Color(0xFF15201A), Color(0xFF1E2E24),
        Color(0xFFE8B83A), Color(0xFFFFE08A), Color(0xFF7A5A12),
        Color(0xFFE2553C), Color(0xFF6CC48A),
        Color(0xFFF1EEDD), Color(0xFF13190F), Color(0xFFBFCBB8),
    ),
    // Fresh green growth
    Palette(
        "Moss",
        Color(0xFF0B130E), Color(0xFF13211A), Color(0xFF1C3326),
        Color(0xFF7CC46A), Color(0xFFC6F2A8), Color(0xFF335E2A),
        Color(0xFFE06A3A), Color(0xFFA8E07A),
        Color(0xFFEAF2DD), Color(0xFF0F1E12), Color(0xFFB5C9AE),
    ),
    // Teal water and reef light
    Palette(
        "Lagoon",
        Color(0xFF071314), Color(0xFF0F2224), Color(0xFF163336),
        Color(0xFF2FC1B0), Color(0xFF9CF2E4), Color(0xFF12625A),
        Color(0xFFF07A4A), Color(0xFF78D68E),
        Color(0xFFE3F2EE), Color(0xFF0A1E1D), Color(0xFFB0C9C5),
    ),
    // Warm coral over dark clay
    Palette(
        "Coral",
        Color(0xFF170E0E), Color(0xFF261616), Color(0xFF362020),
        Color(0xFFF0806A), Color(0xFFFFC3B4), Color(0xFF8A3A2C),
        Color(0xFFE8364A), Color(0xFF6CC4A0),
        Color(0xFFF6E7DF), Color(0xFF251412), Color(0xFFD4C1BB),
    ),
    // Violet bloom
    Palette(
        "Orchid",
        Color(0xFF120C18), Color(0xFF1E1530), Color(0xFF2C2046),
        Color(0xFFB07CF0), Color(0xFFE2C8FF), Color(0xFF563090),
        Color(0xFFF05A7A), Color(0xFF6FD0B0),
        Color(0xFFEFE7F7), Color(0xFF1B1226), Color(0xFFC8BFD6),
    ),
    // Cool polished chrome
    Palette(
        "Chrome",
        Color(0xFF0D1013), Color(0xFF161B20), Color(0xFF222930),
        Color(0xFFB4C0C8), Color(0xFFEDF2F5), Color(0xFF5A666E),
        Color(0xFFF0603A), Color(0xFF5FC8A4),
        Color(0xFFE9EEF1), Color(0xFF12171B), Color(0xFFB4BEC5),
    ),
    // Clear sky blue on midnight
    Palette(
        "Azure",
        Color(0xFF08101C), Color(0xFF10192C), Color(0xFF1A2846),
        Color(0xFF4A9CF0), Color(0xFFA8D4FF), Color(0xFF1E4A8A),
        Color(0xFFF08A3A), Color(0xFF5FD0C0),
        Color(0xFFE6EEF8), Color(0xFF0F1830), Color(0xFFB7C4D8),
    ),
    // Spice orange on charcoal
    Palette(
        "Saffron",
        Color(0xFF141110), Color(0xFF211C19), Color(0xFF312925),
        Color(0xFFF28C28), Color(0xFFFFC27A), Color(0xFF8A4210),
        Color(0xFFD42A2A), Color(0xFF8AC46A),
        Color(0xFFF1E6D4), Color(0xFF211510), Color(0xFFD0C6BA),
    ),
)

/** A neon colour: the bright core and the halo around it. Used by the holo digits, the music player and the Cyberpunk edges. */
class NeonColor(val name: String, val lit: Color, val glow: Color)

val NeonColors = listOf(
    NeonColor("Gold", Color(0xFFFFE79A), Color(0xFFFFC21A)),
    NeonColor("Lime", Color(0xFFD6FF9A), Color(0xFF7CFF1F)),
    NeonColor("Cyan", Color(0xFF9FFBFF), Color(0xFF00E5FF)),
    NeonColor("Blue", Color(0xFF9CC8FF), Color(0xFF2E7BFF)),
    NeonColor("Violet", Color(0xFFCBA8FF), Color(0xFF8A3DFF)),
    NeonColor("Magenta", Color(0xFFFF9CF0), Color(0xFFFF1FD2)),
    NeonColor("Red", Color(0xFFFF9A9A), Color(0xFFFF2A4A)),
    NeonColor("White", Color(0xFFF4FBFF), Color(0xFFCFE8FF)),
)

/**
 * The colours, style, font and wallpaper every screen uses. Everything here is Compose state,
 * so changing the theme repaints the whole launcher immediately.
 */
object Neo {
    var palette by mutableStateOf(Palettes[0])

    /** The neon colour of the holo digits, the music player and the Cyberpunk edges. */
    var neon by mutableStateOf(NeonColors[0])

    /** "solar" (frosted glass, living wood and sunlight) or "cyber" (dark chrome, cut corners, neon edges and scanlines). */
    var style by mutableStateOf("solar")

    /** True in the Cyberpunk style. */
    val cyber: Boolean get() = style == "cyber"

    // ----- your own colours -----

    var customAccent by mutableStateOf(Color(0xFFE8B83A))
    var customBase by mutableStateOf(Color(0xFF1E2E24))
    var customNeon by mutableStateOf(Color(0xFFFFC21A))

    /** Builds a whole palette from two colours you mix: the accent and the background. */
    private fun customPalette(accent: Color, bg: Color) = Palette(
        "Custom",
        base = lerp(bg, Color.Black, 0.65f), surface = lerp(bg, Color.Black, 0.35f), panel = bg,
        accent = accent, accentLight = lerp(accent, Color.White, 0.4f), accentDark = lerp(accent, Color.Black, 0.5f),
        alert = Color(0xFFE2553C), leaf = Color(0xFF6CC48A),
        text = Color(0xFFF1EEDD), ink = lerp(bg, Color.Black, 0.7f), muted = Color(0xFFBFCBB8),
    )

    private fun customNeonColor(neon: Color) = NeonColor("Custom", lerp(neon, Color.White, 0.45f), neon)

    /** Live update while you slide. Call [saveCustom] when you let go. */
    fun applyCustomPalette(accent: Color, bg: Color) {
        customAccent = accent
        customBase = bg
        palette = customPalette(accent, bg)
    }

    fun applyCustomNeon(c: Color) {
        customNeon = c
        neon = customNeonColor(c)
    }

    fun saveCustom(ctx: Context) {
        prefs(ctx).edit()
            .putInt("cust_accent", customAccent.toArgb()).putInt("cust_base", customBase.toArgb()).putInt("cust_neon", customNeon.toArgb())
            .putString("palette", palette.name).putString("neon", neon.name).apply()
    }

    /** The style and colours as they are right now, for saving in a preset. */
    fun currentColors() = LookColors(style, palette.name, neon.name, customAccent.toArgb(), customBase.toArgb(), customNeon.toArgb())

    /** Applies the colours and style from a preset. Your own custom colours are only replaced if the preset uses them. */
    fun applyColors(ctx: Context, c: LookColors) {
        if (c.palette == "Custom") { customAccent = Color(c.customAccent); customBase = Color(c.customBase) }
        if (c.neon == "Custom") customNeon = Color(c.customNeon)
        style = if (c.style == "cyber") "cyber" else "solar"
        palette = if (c.palette == "Custom") customPalette(customAccent, customBase) else Palettes.firstOrNull { it.name == c.palette } ?: palette
        neon = if (c.neon == "Custom") customNeonColor(customNeon) else NeonColors.firstOrNull { it.name == c.neon } ?: neon
        saveCustom(ctx)
        prefs(ctx).edit().putString("style", style).apply()
    }

    /** Multiplies how long a halo takes to turn: 1.8 is slow, 1 normal, 0.5 fast. */
    var spinFactor by mutableFloatStateOf(1f)

    /** "clean", "tech", "serif", "mono", "script" or "custom" (a font file you uploaded). */
    var fontKey by mutableStateOf("clean")
    private var custom by mutableStateOf<FontFamily?>(null)
    val hasCustomFont: Boolean get() = custom != null

    /** Rajdhani, a squared-off technical face. The GitHub build downloads it into assets/fonts. */
    private var tech by mutableStateOf<FontFamily?>(null)
    val hasTech: Boolean get() = tech != null

    /** A picture you chose, drawn behind the widgets and the index. */
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set
    val hasWallpaper: Boolean get() = wallpaper != null

    /** How much the wallpaper is darkened, 0 to 0.85. */
    var wallDim by mutableFloatStateOf(0.45f)

    /** The font used for all text. Reading it repaints everything when you change it. */
    val font: FontFamily
        get() = when (fontKey) {
            "tech" -> tech ?: FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "script" -> FontFamily.Cursive
            "mono" -> FontFamily.Monospace
            "custom" -> custom ?: FontFamily.SansSerif
            else -> FontFamily.SansSerif
        }

    // In Cyberpunk the backgrounds sink toward a blue-black night and the accent turns to chrome.
    // Your palette still tints the chrome a little, so Coral gives warm chrome and Lagoon gives teal chrome.
    val Base: Color get() = if (cyber) lerp(palette.base, Color(0xFF05060A), 0.75f) else palette.base
    val Surface: Color get() = if (cyber) lerp(palette.surface, Color(0xFF0B0D15), 0.8f) else palette.surface
    val Panel: Color get() = if (cyber) lerp(palette.panel, Color(0xFF141925), 0.8f) else palette.panel
    val Accent: Color get() = if (cyber) lerp(palette.accent, Color(0xFF9AA8BC), 0.65f) else palette.accent
    val AccentLight: Color get() = if (cyber) lerp(palette.accentLight, Color(0xFFDDE6F2), 0.65f) else palette.accentLight
    val AccentDark: Color get() = if (cyber) lerp(palette.accentDark, Color(0xFF3A4352), 0.65f) else palette.accentDark
    val Alert: Color get() = palette.alert
    val Leaf: Color get() = palette.leaf
    val Text: Color get() = if (cyber) lerp(palette.text, Color(0xFFE6F0FF), 0.5f) else palette.text
    val Ink: Color get() = palette.ink
    val Muted: Color get() = if (cyber) lerp(palette.muted, Color(0xFFAFC0D6), 0.5f) else palette.muted

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sunwire", Context.MODE_PRIVATE)

    /** Call once at start-up, before anything is drawn. */
    fun load(ctx: Context) {
        val p = prefs(ctx)
        customAccent = Color(p.getInt("cust_accent", customAccent.toArgb()))
        customBase = Color(p.getInt("cust_base", customBase.toArgb()))
        customNeon = Color(p.getInt("cust_neon", customNeon.toArgb()))
        val paletteName = p.getString("palette", "Sun")
        palette = if (paletteName == "Custom") customPalette(customAccent, customBase) else Palettes.firstOrNull { it.name == paletteName } ?: Palettes[0]
        style = if (p.getString("style", "solar") == "cyber") "cyber" else "solar"
        spinFactor = p.getFloat("spin", 1f)
        val neonName = p.getString("neon", "Gold")
        neon = if (neonName == "Custom") customNeonColor(customNeon) else NeonColors.firstOrNull { it.name == neonName } ?: NeonColors[0]
        fontKey = p.getString("font", "clean") ?: "clean"
        readCustomFont(ctx)
        readTechFont(ctx)
        if (fontKey == "custom" && custom == null) fontKey = "clean"
        if (fontKey == "tech" && tech == null) fontKey = "clean"
        wallDim = p.getFloat("wall_dim", 0.45f)
        readWallpaper(ctx)
    }

    private fun readTechFont(ctx: Context) {
        tech = try {
            val names = ctx.assets.list("fonts")?.toSet().orEmpty()
            val need = listOf("Rajdhani-Regular.ttf", "Rajdhani-Medium.ttf", "Rajdhani-Bold.ttf")
            if (names.containsAll(need)) {
                val am = ctx.assets
                FontFamily(
                    Font("fonts/Rajdhani-Regular.ttf", am, FontWeight.Normal),
                    Font("fonts/Rajdhani-Medium.ttf", am, FontWeight.Medium),
                    Font("fonts/Rajdhani-Bold.ttf", am, FontWeight.Bold),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ----- wallpaper -----

    private fun wallFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    private fun readWallpaper(ctx: Context) {
        val f = wallFile(ctx)
        wallpaper = if (f.exists()) {
            try { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (e: Exception) { null }
        } else {
            null
        }
    }

    /** Shrinks the picture to a sensible size, fixes its rotation, and keeps a private copy. */
    fun installWallpaper(ctx: Context, uri: Uri): Boolean {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
            val turn = try {
                cr.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            if (turn != 0f) {
                val m = Matrix().apply { postRotate(turn) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            wallFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            wallpaper = bmp.asImageBitmap()
            true
        } catch (e: Exception) {
            false
        }
    }

    private val presetWall = Regex("pw_\\d+\\.jpg")

    /** Keeps a private copy of the current wallpaper for a preset. Returns the copy's name, or null if there is none. */
    fun copyWallpaperTo(ctx: Context, name: String): String? = try {
        val src = wallFile(ctx)
        if (!src.exists() || !presetWall.matches(name)) {
            null
        } else {
            src.copyTo(File(ctx.filesDir, name), overwrite = true)
            name
        }
    } catch (e: Exception) {
        null
    }

    /** Makes a preset's saved picture the wallpaper. Returns false if the picture is missing. */
    fun useWallpaperFile(ctx: Context, name: String): Boolean = try {
        val f = File(ctx.filesDir, name)
        if (!presetWall.matches(name) || !f.exists()) {
            false
        } else {
            f.copyTo(wallFile(ctx), overwrite = true)
            readWallpaper(ctx)
            wallpaper != null
        }
    } catch (e: Exception) {
        false
    }

    /** Deletes saved preset pictures that no preset uses any more. */
    fun sweepPresetWallpapers(ctx: Context, keep: Set<String>) {
        ctx.filesDir.listFiles()?.filter { presetWall.matches(it.name) && it.name !in keep }?.forEach { it.delete() }
    }

    fun removeWallpaper(ctx: Context) {
        wallFile(ctx).delete()
        wallpaper = null
    }

    fun setDim(ctx: Context, v: Float) {
        wallDim = v
        prefs(ctx).edit().putFloat("wall_dim", v).apply()
    }

    fun chooseNeon(ctx: Context, c: NeonColor) {
        neon = c
        prefs(ctx).edit().putString("neon", c.name).apply()
    }

    fun choose(ctx: Context, pal: Palette) {
        palette = pal
        prefs(ctx).edit().putString("palette", pal.name).apply()
    }

    /** Switch between "solar" and "cyber". Everything repaints straight away. */
    fun chooseStyle(ctx: Context, key: String) {
        style = if (key == "cyber") "cyber" else "solar"
        prefs(ctx).edit().putString("style", style).apply()
    }

    private fun customFile(ctx: Context) = File(ctx.filesDir, "custom_font")

    private fun readCustomFont(ctx: Context) {
        val f = customFile(ctx)
        custom = if (f.exists()) {
            try { FontFamily(Typeface.createFromFile(f)) } catch (e: Exception) { null }
        } else {
            null
        }
    }

    fun chooseFont(ctx: Context, key: String) {
        fontKey = key
        prefs(ctx).edit().putString("font", key).apply()
    }

    private fun looksLikeFont(b: ByteArray): Boolean {
        val tag = String(b, Charsets.ISO_8859_1)
        val ttf = b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0
        return ttf || tag == "true" || tag == "OTTO" || tag == "ttcf"
    }

    /** Copies a .ttf or .otf you picked into the app's private storage. Returns false if it is not a font. */
    fun installCustomFont(ctx: Context, uri: Uri): Boolean {
        return try {
            val tmp = File(ctx.filesDir, "custom_font.tmp")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return false
            val head = ByteArray(4)
            val sizeOk = tmp.length() in 1_000L..25_000_000L
            val magicOk = sizeOk && tmp.inputStream().use { it.read(head) == 4 } && looksLikeFont(head)
            if (!magicOk) {
                tmp.delete()
                return false
            }
            tmp.copyTo(customFile(ctx), overwrite = true)
            tmp.delete()
            readCustomFont(ctx)
            if (custom == null) return false
            chooseFont(ctx, "custom")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeCustomFont(ctx: Context) {
        customFile(ctx).delete()
        custom = null
        if (fontKey == "custom") chooseFont(ctx, "clean")
    }

    fun setSpin(ctx: Context, factor: Float) {
        spinFactor = factor
        prefs(ctx).edit().putFloat("spin", factor).apply()
    }
}

val LocalAnimations = compositionLocalOf { true }

/** False while the home screen is in the background, so sensors and timers can sleep. */
val LocalActive = compositionLocalOf { true }

private fun neoTypography(font: FontFamily): Typography {
    val base = Typography()
    // Line heights are relative (em) so they shrink with the text. A fixed 24sp line used to inflate
    // every small label and get it clipped at the bottom of a widget.
    return Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 2.sp, lineHeight = 1.2.em),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 1.2.em),
        titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 1.25.em),
        bodyLarge = base.bodyLarge.copy(fontFamily = font, fontSize = 17.sp, lineHeight = 1.3.em),
        bodyMedium = base.bodyMedium.copy(fontFamily = font, lineHeight = 1.3.em),
        labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.Medium, lineHeight = 1.25.em),
    )
}

@Composable
fun SunwireTheme(content: @Composable () -> Unit) {
    // keep the status and navigation bars in the same deep tone as the theme
    val view = LocalView.current
    val bar = Neo.Base.toArgb()
    SideEffect {
        val window = (view.context as? Activity)?.window
        window?.statusBarColor = bar
        window?.navigationBarColor = bar
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neo.Accent, onPrimary = Neo.Ink,
            secondary = Neo.Leaf, background = Neo.Base,
            surface = Neo.Surface, onSurface = Neo.Text, onBackground = Neo.Text,
        ),
        typography = remember(Neo.font) { neoTypography(Neo.font) },
        content = content,
    )
}
