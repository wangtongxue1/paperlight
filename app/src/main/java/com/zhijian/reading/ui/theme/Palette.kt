package com.zhijian.reading.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for 纸间阅读: warm paper, ink text, a vermilion accent.
 * The tone of Apple Books — light, generous and quiet — with one red
 * borrowed from a printed seal.
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
    bgApp = Color(0xFFF3F0EA),
    bgSidebar = Color(0xFFEDE9E1),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    subtle = Color(0xFFE7E2D8),
    hover = Color(0xFFE3DED3),
    pressed = Color(0xFFD9D3C6),
    selected = Color(0xFFF4E3DD),
    inverse = Color(0xFF1D1B17),
    scrim = Color(0x66000000),
    border = Color(0xFFDDD6C9),
    borderSubtle = Color(0xFFE6E0D5),
    text = Color(0xFF1D1B17),
    textSecondary = Color(0xFF5C564C),
    textTertiary = Color(0xFF8E877A),
    textAccent = Color(0xFFB23A2E),
    textOnAccent = Color(0xFFFFFFFF),
    icon = Color(0xFF3A352D),
    iconMuted = Color(0xFF8E877A),
    accent = Color(0xFFC24A3A),
    accentSubtle = Color(0xFFF3DED7),
    success = Color(0xFF3B7A57),
    warning = Color(0xFFB0752B),
    danger = Color(0xFFB3261E),
    dangerSolid = Color(0xFFB3261E),
    chrome = Color(0xFFCFC7B8),
    plankTop = Color(0xFFE0D8C8),
    plankFace = Color(0xFFD3C9B6),
    plankEdge = Color(0xFFBFB4A0),
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
    bgApp = Color(0xFF16130F),
    bgSidebar = Color(0xFF1D1915),
    surface = Color(0xFF241F1A),
    surfaceRaised = Color(0xFF2C2620),
    subtle = Color(0xFF2A251F),
    hover = Color(0xFF322B24),
    pressed = Color(0xFF3B332A),
    selected = Color(0xFF4A2A22),
    inverse = Color(0xFFF4EFE7),
    scrim = Color(0x99000000),
    border = Color(0xFF3A332B),
    borderSubtle = Color(0xFF2A251F),
    text = Color(0xFFF4EFE7),
    textSecondary = Color(0xFFBDB4A6),
    textTertiary = Color(0xFF8A8072),
    textAccent = Color(0xFFE8907F),
    textOnAccent = Color(0xFF24120D),
    icon = Color(0xFFD6CCBC),
    iconMuted = Color(0xFF8A8072),
    accent = Color(0xFFE0725C),
    accentSubtle = Color(0xFF4A2A22),
    success = Color(0xFF6FA987),
    warning = Color(0xFFD8A45B),
    danger = Color(0xFFE0715F),
    dangerSolid = Color(0xFFB3261E),
    chrome = Color(0xFF4A4237),
    plankTop = Color(0xFF2A241D),
    plankFace = Color(0xFF241F19),
    plankEdge = Color(0xFF1B1712),
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
