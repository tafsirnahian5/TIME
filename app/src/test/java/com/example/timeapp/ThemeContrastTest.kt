package com.example.timeapp

import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.theme.Colorway
import com.example.timeapp.ui.theme.DarkInk
import com.example.timeapp.ui.theme.DarkInk2
import com.example.timeapp.ui.theme.DarkOnAccent
import com.example.timeapp.ui.theme.Ink
import com.example.timeapp.ui.theme.Ink2
import com.example.timeapp.ui.theme.TimeColors
import com.example.timeapp.ui.theme.colorsFor
import com.example.timeapp.ui.theme.statusColorsFor
import org.junit.Assert.assertTrue
import com.example.timeapp.ui.theme.pureBlack
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Dark mode was previously shipped disabled because it was "unreadable on-device" — the app's
 * own `TimeColors` stayed light while M3's scheme went dark, so near-white text landed on
 * near-white cards. These tests check the thing that actually went wrong, for every colorway
 * in both modes, so the palette can be trusted without a device to squint at.
 *
 * WCAG AA is 4.5:1 for body text and 3:1 for large text and UI boundaries.
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance. */
    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertReadable(label: String, fg: Color, bg: Color, minimum: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$label contrast is %.2f:1, below the required %.1f:1".format(ratio, minimum),
            ratio >= minimum,
        )
    }

    private fun eachColorway(block: (Colorway, TimeColors, Boolean) -> Unit) {
        Colorway.entries.forEach { way ->
            block(way, colorsFor(way, dark = false), false)
            block(way, colorsFor(way, dark = true), true)
            // Appearance › Pure black must pass every check the ordinary dark set does.
            block(way, colorsFor(way, dark = true).pureBlack(), true)
        }
    }

    @Test
    fun `body text is readable on every surface tier`() {
        eachColorway { way, t, dark ->
            val ink = if (dark) DarkInk else Ink
            assertReadable("$way dark=$dark ink on surf", ink, t.surf)
            assertReadable("$way dark=$dark ink on card", ink, t.card)
            assertReadable("$way dark=$dark ink on accS", ink, t.accS)
            assertReadable("$way dark=$dark ink on accS2", ink, t.accS2)
            assertReadable("$way dark=$dark ink on accS3", ink, t.accS3)
        }
    }

    @Test
    fun `secondary text is readable on cards`() {
        eachColorway { way, t, dark ->
            val ink2 = if (dark) DarkInk2 else Ink2
            assertReadable("$way dark=$dark ink2 on card", ink2, t.card)
            assertReadable("$way dark=$dark ink2 on surf", ink2, t.surf)
        }
    }

    /** The regression that made dark mode unusable: a card that isn't distinguishable from
     * the page it sits on, or is the wrong side of the background entirely. */
    @Test
    fun `cards sit on the correct side of the background`() {
        eachColorway { way, t, dark ->
            val cardLum = luminance(t.card)
            val surfLum = luminance(t.surf)
            if (dark) {
                assertTrue("$way dark card must not be lighter than mid-grey", cardLum < 0.5)
                assertTrue("$way dark card must be raised above the page", cardLum > surfLum)
            } else {
                assertTrue("$way light card must be light", cardLum > 0.5)
                assertTrue("$way light card must be at least as light as the page", cardLum >= surfLum - 0.01)
            }
        }
    }

    @Test
    fun `text on an accent container is readable`() {
        eachColorway { way, t, dark ->
            assertReadable("$way dark=$dark accInk on accC", t.accInk, t.accC)
            assertReadable("$way dark=$dark accInk on accC2", t.accInk, t.accC2)
            assertReadable("$way dark=$dark accInk on accC3", t.accInk, t.accC3)
        }
    }

    /** White icons/labels are drawn on the filled accent in light mode (FABs, the balance
     * card); dark mode uses a dark ink on its lighter accent instead. */
    @Test
    fun `content on a filled accent is readable`() {
        eachColorway { way, t, dark ->
            val onAccent = if (dark) DarkOnAccent else Color.White
            assertReadable("$way dark=$dark content on acc", onAccent, t.acc)
        }
    }

    /** Borders and progress tracks only need to be perceivable, not legible. */
    @Test
    fun `borders are visible against their surface`() {
        eachColorway { way, t, dark ->
            assertReadable("$way dark=$dark edge on card", t.edge, t.card, minimum = 1.3)
            assertReadable("$way dark=$dark ring on card", t.ring, t.card, minimum = 1.1)
        }
    }

    /**
     * Status containers (overdue pink, paused amber, tracking green, done grey) used to be
     * raw hex literals in the screens, with no dark variant at all. Their *ink* was always
     * readable on its own container, so a naive contrast check passed while the containers
     * themselves stayed pale — bright blocks on a dark page. These assert both halves:
     * the ink is legible on its container, and the container actually belongs to the mode
     * it is used in (dark containers must be darker than the page they sit on is light).
     */
    @Test
    fun `status ink is readable on its own container`() {
        listOf(false, true).forEach { dark ->
            val s = statusColorsFor(dark)
            assertReadable("dark=$dark dangerInk on danger", s.dangerInk, s.danger)
            assertReadable("dark=$dark warnInk on warn", s.warnInk, s.warn)
            assertReadable("dark=$dark okInk on ok", s.okInk, s.ok)
            assertReadable("dark=$dark neutralInk on neutral", s.neutralInk, s.neutral)
        }
    }

    /** The regression that made these worth tokenising: a status container must sit on the
     * same side of the brightness range as the surface it is drawn on, or it reads as a
     * glaring patch. Checked against every colorway's real card colour. */
    @Test
    fun `status containers match the brightness of the mode they belong to`() {
        eachColorway { way, t, dark ->
            val s = statusColorsFor(dark)
            val cardL = luminance(t.card)
            listOf("danger" to s.danger, "warn" to s.warn, "ok" to s.ok, "neutral" to s.neutral)
                .forEach { (name, container) ->
                    val ratio = contrast(container, t.card)
                    assertTrue(
                        "$way dark=$dark $name container contrasts %.2f:1 against the card — ".format(ratio) +
                            "too far from the surface it sits on, which is what made these read as bright blocks",
                        ratio <= 4.0,
                    )
                    // And it must not invert the mode: no pale container in dark mode.
                    if (dark) {
                        assertTrue("$way $name container is lighter than a dark card", luminance(container) < 0.5)
                    }
                    assertTrue("$way dark=$dark $name luminance sanity", cardL >= 0.0)
                }
        }
    }

    /** Unselected control outlines have to be perceivable in both modes — they are the only
     * thing distinguishing an unchecked checkbox from empty space. */
    @Test
    fun `control outlines are perceivable`() {
        eachColorway { way, t, dark ->
            val s = statusColorsFor(dark)
            assertReadable("$way dark=$dark controlOutline on card", s.controlOutline, t.card, minimum = 1.8)
            assertReadable("$way dark=$dark dim text on card", s.dim, t.card, minimum = 2.5)
        }
    }
}
