package com.zhijian.reading.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens: neutral paper, ink text, one orange accent. The tone of
 * 微信读书 — calm light surfaces, near-black text, no decorative extras.
 */
@Immutable
data class Palette(
    val dark: Boolean,

    /** the page behind everything */
    val bgApp: Color,
    /** grouped settings rows and filter chips */
    val bgSidebar: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val subtle: Color,
    val hover: Color,
    val pressed: Color,
    val selected: Color,
    val inverse: Color,
    val scrim: Color,

    val border: Color,
    val borderSubtle: Color,

    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textAccent: Color,
    val textOnAccent: Color,

    val icon: Color,
    val iconMuted: Color,

    val accent: Color,
    val accentSubtle: Color,

    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerSolid: Color,

    val chrome: Color,

    /** shelf planks, kept for the covers grid shadow */
    val plankTop: Color,
    val plankFace: Color,
    val plankEdge: Color,

    val formats: Map<String, Color>,
) {
    fun format(f: String): Color = formats[f.uppercase()] ?: textSecondary
}

fun lightPalette() = Palette(
    dark = false,
    bgApp = Color(0xFFF7F7F5),
    bgSidebar = Color(0xFFF1F1EF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    subtle = Color(0xFFEDEDEB),
    hover = Color(0xFFE8E8E6),
    pressed = Color(0xFFDEDEDC),
    selected = Color(0xFFFFF1E7),
    inverse = Color(0xFF1A1A1A),
    scrim = Color(0x66000000),
    border = Color(0xFFE4E4E2),
    borderSubtle = Color(0xFFEAEAE8),
    text = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF595959),
    textTertiary = Color(0xFF999999),
    textAccent = Color(0xFFD95B22),
    textOnAccent = Color(0xFFFFFFFF),
    icon = Color(0xFF333333),
    iconMuted = Color(0xFF999999),
    accent = Color(0xFFFF6F38),
    accentSubtle = Color(0xFFFFF1E7),
    success = Color(0xFF3B7A57),
    warning = Color(0xFFB0752B),
    danger = Color(0xFFB3261E),
    dangerSolid = Color(0xFFB3261E),
    chrome = Color(0xFFD8D8D5),
    plankTop = Color(0xFFE8E7E3),
    plankFace = Color(0xFFDCDBD7),
    plankEdge = Color(0xFFC8C7C3),
    formats = mapOf(
        "EPUB" to Color(0xFF2E6B4F),
        "PDF" to Color(0xFFA5432F),
        "MOBI" to Color(0xFF8A6A2F),
        "AZW3" to Color(0xFF8A6A2F),
        "AZW" to Color(0xFF8A6A2F),
        "TXT" to Color(0xFF4A6584),
    ),
)

fun darkPalette() = Palette(
    dark = true,
    bgApp = Color(0xFF141414),
    bgSidebar = Color(0xFF1A1A1A),
    surface = Color(0xFF202020),
    surfaceRaised = Color(0xFF262626),
    subtle = Color(0xFF242424),
    hover = Color(0xFF2C2C2C),
    pressed = Color(0xFF333333),
    selected = Color(0xFF412A1E),
    inverse = Color(0xFFF2F2F2),
    scrim = Color(0x99000000),
    border = Color(0xFF333333),
    borderSubtle = Color(0xFF242424),
    text = Color(0xFFF2F2F2),
    textSecondary = Color(0xFFB5B5B5),
    textTertiary = Color(0xFF808080),
    textAccent = Color(0xFFFF9A6B),
    textOnAccent = Color(0xFF1F0E06),
    icon = Color(0xFFD9D9D9),
    iconMuted = Color(0xFF808080),
    accent = Color(0xFFFF7E45),
    accentSubtle = Color(0xFF412A1E),
    success = Color(0xFF6FA987),
    warning = Color(0xFFD8A45B),
    danger = Color(0xFFE0715F),
    dangerSolid = Color(0xFFB3261E),
    chrome = Color(0xFF3C3C3C),
    plankTop = Color(0xFF232323),
    plankFace = Color(0xFF1D1D1D),
    plankEdge = Color(0xFF151515),
    formats = mapOf(
        "EPUB" to Color(0xFF7FBFA0),
        "PDF" to Color(0xFFE08A72),
        "MOBI" to Color(0xFFCBA96B),
        "AZW3" to Color(0xFFCBA96B),
        "AZW" to Color(0xFFCBA96B),
        "TXT" to Color(0xFF8FB0D4),
    ),
)

/** 4 px spacing scale. */
object Space {
    val xs = 4
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 24
    val xxl = 32
}

/** Corner radii — covers are softly rounded, like Apple Books. */
object Radius {
    val xs = 4
    val sm = 6
    val md = 10
    val lg = 14
    val xl = 20
    val pill = 999
}

/** Fixed sizes the layout keeps. */
object Sizes {
    const val coverW = 132f
    const val coverH = 198f
}

val LocalPalette = staticCompositionLocalOf { lightPalette() }

/** The palette in the current theme. */
val palette: Palette
    @Composable get() = LocalPalette.current

@Composable
fun ProvidePalette(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides if (dark) darkPalette() else lightPalette(),
        content = content,
    )
}
