package com.example.timeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Settings › Appearance › Reduce celebrations: no confetti or tick animation (the banner stays). */
val LocalReduceCelebrations = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The accent is the user's choice across five reviewed colorways (G4) — or, since Phase 15 and
 * only when they switch it on, the wallpaper's colours ([dynamicColor], Android 12+), mapped onto
 * the same tokens. [pureBlack] turns the dark page true black.
 */
@Composable
fun TimeTheme(
    colorway: Colorway = Colorway.Violet,
    themeMode: String = "system",          // "system" | "light" | "dark"
    dynamicColor: Boolean = false,
    pureBlack: Boolean = false,
    reduceCelebrations: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Status/navigation bar icons follow the app's theme, not the phone's: with the app set to
    // Light on a phone in dark mode, the clock and battery were white on a white page.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val dynamicScheme =if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
    } else null
    val base = dynamicScheme?.let { timeColorsFrom(it, dark) } ?: colorsFor(colorway, dark)
    val t = if (dark && pureBlack) base.pureBlack() else base

    val scheme = if (!dark) lightColorScheme(
        primary = t.acc,
        onPrimary = Color.White,
        primaryContainer = t.accC,
        onPrimaryContainer = t.accInk,
        secondaryContainer = t.accC2,
        onSecondaryContainer = t.accInk,
        background = t.surf,
        onBackground = Ink,
        surface = t.surf,
        onSurface = Ink,
        surfaceVariant = t.accS,
        onSurfaceVariant = Ink2,
        outline = t.edge,
        outlineVariant = t.line,
        error = WarnRed,
    ) else darkColorScheme(
        // Derived from the colorway's own dark token set rather than a separate hardcoded
        // palette, so the M3 roles and the app's `TimeColors` can never disagree about what
        // "surface" means — the mismatch that made dark mode unreadable before.
        primary = t.acc,
        onPrimary = DarkOnAccent,
        primaryContainer = t.accC,
        onPrimaryContainer = t.accInk,
        secondaryContainer = t.accC2,
        onSecondaryContainer = t.accInk,
        background = t.surf,
        onBackground = DarkInk,
        surface = t.surf,
        onSurface = DarkInk,
        surfaceVariant = t.accS,
        onSurfaceVariant = DarkInk2,
        outline = t.edge,
        outlineVariant = t.line,
        error = Color(0xFFF2B8B5),
    )

    CompositionLocalProvider(
        LocalTimeColors provides t,
        LocalStatusColors provides statusColorsFor(dark),
        LocalReduceCelebrations provides reduceCelebrations,
    ) {
        MaterialTheme(colorScheme = scheme, typography = TimeTypography, content = content)
    }
}

/** Shorthand: `TimeTheme.colors.block` */
object TimeThemeTokens {
    val colors: TimeColors
        @Composable get() = LocalTimeColors.current

    /** Semantic status colours (overdue/paused/tracking/done), dark-mode aware. */
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
