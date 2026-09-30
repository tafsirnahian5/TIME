package com.example.timeapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Neutrals — constant across every colorway. */
val Ink   = Color(0xFF1C1B1F)
val Ink2  = Color(0xFF49454F)
val Ink3  = Color(0xFF5C5766)
val Ink4  = Color(0xFF6B6577)
val CardW = Color(0xFFFFFFFF)

/** Dark-mode neutrals, the counterparts of Ink/Ink2 above. */
val DarkInk  = Color(0xFFE6E1E9)
val DarkInk2 = Color(0xFFCAC4D0)
/** Text/icons drawn on top of a filled accent in dark mode. */
val DarkOnAccent = Color(0xFF1C1B1F)

/** Semantic record markers — constant across colorways. */
val MarkerTask  = Color(0xFF6C4ED9)
val MarkerEvent = Color(0xFF2E7DE8)
val MarkerMoney = Color(0xFFE5A32B)
val WarnRed     = Color(0xFFB3261E)
val OkGreen     = Color(0xFF146C2E)

/**
 * The mock's token set. Held alongside M3's ColorScheme because several roles
 * (block, accentDim, ring, the three surface tiers) have no M3 equivalent.
 */
@Immutable
data class TimeColors(
    val acc: Color, val accInk: Color,
    val accC: Color, val accC2: Color, val accC3: Color,
    val accDim: Color, val block: Color,
    val accS: Color, val accS2: Color, val accS3: Color,
    val surf: Color,
    /** The raised card surface. Screens used to hardcode `Color.White` here, which is why
     * dark mode rendered light-on-light and was unusable — always use this token. */
    val card: Color,
    val line: Color, val line2: Color, val line3: Color,
    val edge: Color, val edge2: Color,
    val ring: Color, val ring2: Color,
)

enum class Colorway(val label: String) {
    Violet("Violet"),
    InkOrange("Ink & Orange"),
    CyanAmber("Cyan & Amber"),
    ForestCoral("Forest & Coral"),
    NavyLime("Navy & Lime");
}

val VioletColors = TimeColors(
    acc = Color(0xFF5B41AE), accInk = Color(0xFF21005D),
    accC = Color(0xFFE5DDF8), accC2 = Color(0xFFE9E1FA), accC3 = Color(0xFFEDE7F6),
    accDim = Color(0xFFD8CDF4), block = Color(0xFFDFD3F7),
    accS = Color(0xFFF3EDF7), accS2 = Color(0xFFF0EBF4), accS3 = Color(0xFFF7F2FA),
    surf = Color(0xFFFBF8FE), card = CardW,
    line = Color(0xFFEDE8F2), line2 = Color(0xFFF2EDF6), line3 = Color(0xFFEDE7F2),
    edge = Color(0xFFCFC7DB), edge2 = Color(0xFFDCD5E4),
    ring = Color(0xFFE7E0EC), ring2 = Color(0xFFE0D8EC),
)

val InkOrangeColors = TimeColors(
    acc = Color(0xFFA8420A), accInk = Color(0xFF3A1000),
    accC = Color(0xFFFFDCC6), accC2 = Color(0xFFFFE7D8), accC3 = Color(0xFFFFEFE4),
    accDim = Color(0xFFFFC9A6), block = Color(0xFFFFD3B5),
    accS = Color(0xFFFBF0E9), accS2 = Color(0xFFF7ECE4), accS3 = Color(0xFFFDF4EE),
    surf = Color(0xFFFFFBF8), card = CardW,
    line = Color(0xFFF2E6DE), line2 = Color(0xFFF8EFE9), line3 = Color(0xFFF2E6DE),
    edge = Color(0xFFDCC8BB), edge2 = Color(0xFFE6D6CB),
    ring = Color(0xFFEFE0D6), ring2 = Color(0xFFE8D6C9),
)

val CyanAmberColors = TimeColors(
    acc = Color(0xFF00697C), accInk = Color(0xFF001F26),
    accC = Color(0xFFB4EBF8), accC2 = Color(0xFFCDF1FA), accC3 = Color(0xFFDDF5FC),
    accDim = Color(0xFF93DFF1), block = Color(0xFFA8E5F4),
    accS = Color(0xFFE9F4F7), accS2 = Color(0xFFE3F1F5), accS3 = Color(0xFFF0F8FA),
    surf = Color(0xFFF7FCFE), card = CardW,
    line = Color(0xFFDDEBEF), line2 = Color(0xFFE9F2F5), line3 = Color(0xFFDDEBEF),
    edge = Color(0xFFB9CCD2), edge2 = Color(0xFFCBDCE1),
    ring = Color(0xFFD8E8EC), ring2 = Color(0xFFCFE2E7),
)

val ForestCoralColors = TimeColors(
    acc = Color(0xFF106149), accInk = Color(0xFF002114),
    accC = Color(0xFFBFEBD8), accC2 = Color(0xFFD2F1E3), accC3 = Color(0xFFE0F5EB),
    accDim = Color(0xFF9EDFC4), block = Color(0xFFB2E6D0),
    accS = Color(0xFFE8F4EE), accS2 = Color(0xFFE2F1EA), accS3 = Color(0xFFF0F8F4),
    surf = Color(0xFFF7FCF9), card = CardW,
    line = Color(0xFFDCEBE3), line2 = Color(0xFFE8F3ED), line3 = Color(0xFFDCEBE3),
    edge = Color(0xFFB5CCC0), edge2 = Color(0xFFC8DCD2),
    ring = Color(0xFFD6E8DF), ring2 = Color(0xFFCCE2D7),
)

val NavyLimeColors = TimeColors(
    acc = Color(0xFF24418C), accInk = Color(0xFF001849),
    accC = Color(0xFFDAE2FF), accC2 = Color(0xFFE5EAFF), accC3 = Color(0xFFEDF0FF),
    accDim = Color(0xFFBCCBFA), block = Color(0xFFC9D6FB),
    accS = Color(0xFFECEFF8), accS2 = Color(0xFFE6EBF6), accS3 = Color(0xFFF3F5FC),
    surf = Color(0xFFFAFBFF), card = CardW,
    line = Color(0xFFE2E7F2), line2 = Color(0xFFEDF0F8), line3 = Color(0xFFE2E7F2),
    edge = Color(0xFFC2C9DB), edge2 = Color(0xFFD2D8E6),
    ring = Color(0xFFDDE2EE), ring2 = Color(0xFFD3DAE9),
)


/**
 * Dark token sets — one per colorway.
 *
 * These exist because `TimeColors` is used directly for backgrounds all over the app
 * (`colors.surf`, `colors.card`, `colors.accS`, `colors.line`). Previously only M3's
 * `ColorScheme` flipped in dark mode while these stayed light, so text went near-white on
 * still-white cards. Contrast for every text/surface pair here is asserted in `ThemeContrastTest`.
 *
 * `accInk` inverts meaning between modes on purpose: it is always "readable text on an accent
 * container", which is a dark ink on light and a light tint on dark.
 */
val VioletDarkColors = TimeColors(
    acc = Color(0xFFD0BCFF), accInk = Color(0xFFEADDFF),
    accC = Color(0xFF4F378B), accC2 = Color(0xFF453070), accC3 = Color(0xFF3B2A5E),
    accDim = Color(0xFF9A82DB), block = Color(0xFF4A3578),
    accS = Color(0xFF211F26), accS2 = Color(0xFF1E1B22), accS3 = Color(0xFF262329),
    surf = Color(0xFF141218), card = Color(0xFF1D1B20),
    line = Color(0xFF2B2930), line2 = Color(0xFF322F37), line3 = Color(0xFF2B2930),
    edge = Color(0xFF49454F), edge2 = Color(0xFF3B3843),
    ring = Color(0xFF332F38), ring2 = Color(0xFF3A353F),
)

val InkOrangeDarkColors = TimeColors(
    acc = Color(0xFFFFB68E), accInk = Color(0xFFFFDBC8),
    accC = Color(0xFF7A2F00), accC2 = Color(0xFF692800), accC3 = Color(0xFF572100),
    accDim = Color(0xFFD9895F), block = Color(0xFF6E2B00),
    accS = Color(0xFF241F1B), accS2 = Color(0xFF201C18), accS3 = Color(0xFF2A241F),
    surf = Color(0xFF17120F), card = Color(0xFF211B17),
    line = Color(0xFF302821), line2 = Color(0xFF372E27), line3 = Color(0xFF302821),
    edge = Color(0xFF52463D), edge2 = Color(0xFF433931),
    ring = Color(0xFF382F28), ring2 = Color(0xFF40362E),
)

val CyanAmberDarkColors = TimeColors(
    acc = Color(0xFF57D6F0), accInk = Color(0xFFB4EBF8),
    accC = Color(0xFF004E5C), accC2 = Color(0xFF00424E), accC3 = Color(0xFF003640),
    accDim = Color(0xFF3FAEC5), block = Color(0xFF004857),
    accS = Color(0xFF1B2326), accS2 = Color(0xFF182022), accS3 = Color(0xFF20292C),
    surf = Color(0xFF0F1618), card = Color(0xFF181F22),
    line = Color(0xFF223033), line2 = Color(0xFF28373A), line3 = Color(0xFF223033),
    edge = Color(0xFF3E5156), edge2 = Color(0xFF334347),
    ring = Color(0xFF283537), ring2 = Color(0xFF2F3E41),
)

val ForestCoralDarkColors = TimeColors(
    acc = Color(0xFF84D6B4), accInk = Color(0xFFA0F2CD),
    accC = Color(0xFF00513A), accC2 = Color(0xFF004531), accC3 = Color(0xFF003927),
    accDim = Color(0xFF5FAF90), block = Color(0xFF004B36),
    accS = Color(0xFF1B241F), accS2 = Color(0xFF18211C), accS3 = Color(0xFF202A25),
    surf = Color(0xFF0F1713), card = Color(0xFF18201B),
    line = Color(0xFF22302A), line2 = Color(0xFF283630), line3 = Color(0xFF22302A),
    edge = Color(0xFF3E5148), edge2 = Color(0xFF33433B),
    ring = Color(0xFF28352E), ring2 = Color(0xFF2F3E36),
)

val NavyLimeDarkColors = TimeColors(
    acc = Color(0xFFB4C5FF), accInk = Color(0xFFDAE2FF),
    accC = Color(0xFF29418F), accC2 = Color(0xFF22377B), accC3 = Color(0xFF1C2E68),
    accDim = Color(0xFF8399DB), block = Color(0xFF263C86),
    accS = Color(0xFF1D2029), accS2 = Color(0xFF1A1D25), accS3 = Color(0xFF23262F),
    surf = Color(0xFF111319), card = Color(0xFF1A1D24),
    line = Color(0xFF262A34), line2 = Color(0xFF2C313B), line3 = Color(0xFF262A34),
    edge = Color(0xFF444A5A), edge2 = Color(0xFF373C4A),
    ring = Color(0xFF2D313C), ring2 = Color(0xFF343946),
)

fun colorsFor(c: Colorway, dark: Boolean = false): TimeColors = if (dark) {
    when (c) {
        Colorway.Violet -> VioletDarkColors
        Colorway.InkOrange -> InkOrangeDarkColors
        Colorway.CyanAmber -> CyanAmberDarkColors
        Colorway.ForestCoral -> ForestCoralDarkColors
        Colorway.NavyLime -> NavyLimeDarkColors
    }
} else {
    when (c) {
        Colorway.Violet -> VioletColors
        Colorway.InkOrange -> InkOrangeColors
        Colorway.CyanAmber -> CyanAmberColors
        Colorway.ForestCoral -> ForestCoralColors
        Colorway.NavyLime -> NavyLimeColors
    }
}

/**
 * Status colours — semantic, and unlike [TimeColors] identical across all five colorways,
 * so they live in their own palette rather than being repeated ten times.
 *
 * Screens used to hardcode these as raw hex (`Color(0xFFFCE4E6)` for an overdue pill,
 * `Color(0xFFFDEBD2)` for a warning banner, and so on). That broke rule 1 and, more
 * importantly, broke dark mode: the *ink* stayed legible on its own container, so a
 * contrast test passed, but the containers themselves are pale pinks and creams that
 * render as glaring bright blocks on a dark page — some of them full-width banners.
 * The dark variants below keep the same hue and meaning at a surface-appropriate depth.
 *
 * `*Ink` is always the on-container colour for the container above it.
 */
@Immutable
data class StatusColors(
    /** Overdue, destructive warnings, over-budget. */
    val danger: Color, val dangerInk: Color,
    /** Paused, partly paid, "needs attention but nothing is wrong". */
    val warn: Color, val warnInk: Color,
    /** Tracking, paid, income, under budget. */
    val ok: Color, val okInk: Color,
    /** Done/unscheduled — present but not calling for action. */
    val neutral: Color, val neutralInk: Color,
    /** Unselected control outlines (the old `Color(0xFF79747E)`). */
    val controlOutline: Color,
    /** Dimmed icons and disabled text (the old `Color(0xFF8E8798)`). */
    val dim: Color,
)

val LightStatusColors = StatusColors(
    danger = Color(0xFFFCE4E6), dangerInk = Color(0xFF8C1D2E),
    warn = Color(0xFFFBE7C2), warnInk = Color(0xFF7A5411),
    ok = Color(0xFFD8EFDE), okInk = Color(0xFF12643A),
    // Ink3 rather than the design's original 0xFF6B6577: that pairing was 4.34:1 on the
    // neutral container, just under AA, which the contrast test caught once these moved
    // out of the screens and into tokens.
    neutral = Color(0xFFE7E0EC), neutralInk = Ink3,
    controlOutline = Color(0xFF79747E),
    dim = Color(0xFF8E8798),
)

val DarkStatusColors = StatusColors(
    danger = Color(0xFF4A2429), dangerInk = Color(0xFFFFB3BC),
    warn = Color(0xFF453423), warnInk = Color(0xFFF5C77E),
    ok = Color(0xFF1E3A2B), okInk = Color(0xFF8FD9AC),
    neutral = Color(0xFF322F37), neutralInk = Color(0xFFCAC4D0),
    controlOutline = Color(0xFF938F99),
    dim = Color(0xFF9A93A6),
)

fun statusColorsFor(dark: Boolean): StatusColors = if (dark) DarkStatusColors else LightStatusColors

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }


val LocalTimeColors = staticCompositionLocalOf { VioletColors }

/**
 * Pure black (Phase 15, dark mode only): the page goes to true black for OLED screens, and every
 * raised surface keeps the *same step above it* it had over the colorway's dark surface, so cards
 * and containers stay distinguishable. Accents and inks are untouched, so their contrast only
 * improves; ThemeContrastTest checks it for all five colorways.
 */
fun TimeColors.pureBlack(): TimeColors {
    fun lift(c: Color) = Color(
        red = (c.red - surf.red).coerceIn(0f, 1f),
        green = (c.green - surf.green).coerceIn(0f, 1f),
        blue = (c.blue - surf.blue).coerceIn(0f, 1f),
        alpha = c.alpha,
    )
    return copy(
        surf = Color.Black,
        card = lift(card), accS = lift(accS), accS2 = lift(accS2), accS3 = lift(accS3),
        line = lift(line), line2 = lift(line2), line3 = lift(line3),
        ring = lift(ring), ring2 = lift(ring2),
    )
}

/**
 * Wallpaper colours (Phase 15, Android 12+): the app's tokens taken from the system's dynamic
 * Material scheme, so every screen that reads TimeThemeTokens follows the wallpaper too — not
 * only the M3 components. Status colours stay the app's own (a dynamic palette can't promise
 * overdue still reads as overdue).
 */
fun timeColorsFrom(s: androidx.compose.material3.ColorScheme, dark: Boolean): TimeColors = TimeColors(
    acc = s.primary, accInk = s.onPrimaryContainer,
    accC = s.primaryContainer, accC2 = s.secondaryContainer, accC3 = s.tertiaryContainer,
    accDim = s.inversePrimary, block = s.primaryContainer,
    accS = s.surfaceContainer, accS2 = s.surfaceContainerHigh, accS3 = s.surfaceContainerLow,
    surf = s.surface, card = if (dark) s.surfaceContainer else s.surfaceContainerLowest,
    line = s.outlineVariant, line2 = s.surfaceContainerHigh, line3 = s.outlineVariant,
    edge = s.outline, edge2 = s.outlineVariant,
    ring = s.surfaceContainerHighest, ring2 = s.surfaceVariant,
)
