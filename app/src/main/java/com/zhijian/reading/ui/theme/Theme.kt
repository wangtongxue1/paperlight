package com.zhijian.reading.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Type: sans for the interface, serif for book titles and reading —
 * the pairing Apple Books uses, and what CJK reading wants.
 */
object Type {
    val ui = FontFamily.SansSerif
    val serif = FontFamily.Serif
    val mono = FontFamily.Monospace

    val title = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    )
    val display = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp,
    )
    val body = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    )
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium)
    val small = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp,
    )
    val smallStrong = small.copy(fontWeight = FontWeight.Medium)
    val label = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    )
    val overline = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp,
    )
    val numeric = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp,
    )
    /** long reading text in the reader */
    val reading = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 28.sp,
    )
}

@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val pal = if (dark) darkPalette() else lightPalette()
    val scheme = if (dark) darkColorScheme(
        primary = pal.accent, onPrimary = pal.textOnAccent,
        primaryContainer = pal.accentSubtle, onPrimaryContainer = pal.textAccent,
        background = pal.bgApp, onBackground = pal.text,
        surface = pal.surface, onSurface = pal.text,
        surfaceVariant = pal.subtle, onSurfaceVariant = pal.textSecondary,
        surfaceContainerHigh = pal.surfaceRaised, surfaceContainer = pal.surfaceRaised,
        surfaceContainerLow = pal.surface, surfaceContainerLowest = pal.surface,
        outline = pal.border, outlineVariant = pal.borderSubtle,
        error = pal.dangerSolid, onError = Color.White,
        scrim = pal.scrim,
    ) else lightColorScheme(
        primary = pal.accent, onPrimary = pal.textOnAccent,
        primaryContainer = pal.accentSubtle, onPrimaryContainer = pal.textAccent,
        background = pal.bgApp, onBackground = pal.text,
        surface = pal.surface, onSurface = pal.text,
        surfaceVariant = pal.subtle, onSurfaceVariant = pal.textSecondary,
        surfaceContainerHigh = pal.surfaceRaised, surfaceContainer = pal.surfaceRaised,
        surfaceContainerLow = pal.surface, surfaceContainerLowest = pal.surface,
        outline = pal.border, outlineVariant = pal.borderSubtle,
        error = pal.dangerSolid, onError = Color.White,
        scrim = pal.scrim,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            bodyLarge = Type.body,
            bodyMedium = Type.body,
            bodySmall = Type.small,
            titleLarge = Type.title,
            labelLarge = Type.label,
            labelSmall = Type.overline,
        ),
    ) {
        ProvidePalette(dark, content)
    }
}
