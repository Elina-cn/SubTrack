package com.elinacn.subtrack.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette: deep emerald and gold.
 *
 * **Why these two.** The app tracks money, and the palette says so. The pastel blue-cyan it
 * replaces said nothing in particular (ARCHITECTURE §12).
 *
 * **The rule the whole scheme turns on, and the measurement behind it.** Gold is a beautiful fill
 * and a terrible ink: [Gold] on white is **2.42:1**, short even of the 3:1 a graphical object
 * needs, never mind the 4.5:1 for text. Emerald is the other way round: [EmeraldDeep] on white is
 * **8.02:1**. So the two swap jobs between the schemes:
 *
 * - **Light:** emerald is text, icons and charts; gold only ever fills a shape.
 * - **Dark:** gold is text, icons and charts ([GoldBright] on [EmeraldCard] is 5.66:1); the
 *   emerald family becomes the surfaces.
 *
 * That is the same reasoning that split the old blue into two roles, one tone for ink and one for
 * fill - only now the split runs across the two schemes as well.
 *
 * **Names.** Hue first, then how light it is, darkest first: Ink, Deep, Mid, Muted, Edge, Soft,
 * Pale, Mist. The dark scheme's own surfaces are named for what they are - Night, Card, Raised -
 * because there a "light" emerald is a surface rather than an accent.
 *
 * Every pair that carries text or meaning is measured; the table lives in ARCHITECTURE §12 and
 * nothing here is chosen by eye.
 */

// --- Emerald: ink in the light scheme, surfaces in the dark one -------------------------------

/** Darkest emerald. Text on every light surface, and the ink laid over gold. 14.45:1 on white. */
val EmeraldInk = Color(0xFF08301F)

/** The light scheme's accent: text, icons and chart fills. 8.02:1 on white. */
val EmeraldDeep = Color(0xFF0B5C3F)

/** A second, lighter accent for supporting text. 6.29:1 carrying white. */
val EmeraldMid = Color(0xFF2F6B4F)

/** Labels that are present but not the point - an unselected chip, a settings subtitle. */
val EmeraldMuted = Color(0xFF35594A)

/** Borders: a chip's outline, a text field's edge. 4.46:1 on white, 3.32:1 on the backdrop. */
val EmeraldEdge = Color(0xFF5C7F6C)

/** Dividers and the empty part of a chart bar. Decorative, never carries text. */
val EmeraldSoft = Color(0xFFA8C7B6)

/** The light scheme's filled surface: the dashboard card, the save button, a selected chip. */
val EmeraldPale = Color(0xFFCDE8DA)

/** A second fill, for anything that should read as filled but not as the primary action. */
val EmeraldMist = Color(0xFFDCEFE4)

/** A surface that is a shade off white - a grouped field, a variant panel. */
val EmeraldHaze = Color(0xFFDCEAE1)

/** The light backdrop. White cards sit on it at 1.34:1, separated without a hard edge. */
val EmeraldBackdrop = Color(0xFFD3E2D8)

/** The dimmest light surface, for a panel that recedes. */
val EmeraldDim = Color(0xFFC2D6C9)

// Light surface containers, lowest to highest. Material components reach for these by name.
val EmeraldContainerLow = Color(0xFFF3F9F5)
val EmeraldContainer = Color(0xFFEAF3EE)
val EmeraldContainerHigh = Color(0xFFE1EDE6)
val EmeraldContainerHighest = Color(0xFFD8E7DE)

// --- Emerald in the dark: these are surfaces, not accents --------------------------------------

/** The dark backdrop. */
val EmeraldNight = Color(0xFF0D1A14)

/** The dark card. 1.50:1 against [EmeraldNight] - the separation the old scheme lacked. */
val EmeraldCard = Color(0xFF1F3D2D)

/** A raised dark surface, one step above the card. */
val EmeraldRaised = Color(0xFF2A4E3A)

/** The dark scheme's filled surface: dashboard card, save button, selected chip. */
val EmeraldFill = Color(0xFF14523A)

/** A second dark fill, beside [EmeraldFill] the way [EmeraldMist] sits beside [EmeraldPale]. */
val EmeraldFillHigh = Color(0xFF26543D)

/** Dividers in the dark, and the empty part of a chart bar there. */
val EmeraldLine = Color(0xFF3A5A48)

/** Borders in the dark. 4.46:1 on the card. */
val EmeraldEdgeLight = Color(0xFF84A694)

/** A supporting accent in the dark, where a light tone is what reads. */
val EmeraldTint = Color(0xFF8FC9AC)

/** Present-but-not-the-point labels in the dark. 7.24:1 on the card. */
val EmeraldFrost = Color(0xFFB9CFC2)

/** Body text in the dark. 10.05:1 on the card. */
val EmeraldSnow = Color(0xFFE8EDE9)

/** Text on a filled dark surface. 8.07:1 on [EmeraldFill]. */
val EmeraldWhisper = Color(0xFFEAF3EE)

// Dark surface containers, lowest to highest.
val EmeraldNightLow = Color(0xFF081410)
val EmeraldCardLow = Color(0xFF162C21)
val EmeraldCardBase = Color(0xFF1A3325)
val EmeraldCardHigh = Color(0xFF234531)

// --- Gold: fill in the light scheme, ink in the dark one ---------------------------------------

/**
 * The light scheme's gold. **Filled surfaces only.**
 *
 * 2.42:1 on white: it cannot be text, an icon or a chart line there, and nothing in the light
 * scheme asks it to be. [EmeraldInk] on top of it is 5.97:1.
 */
val Gold = Color(0xFFC9A227)

/** The dark scheme's accent - text, icons and charts. 5.66:1 on [EmeraldCard]. */
val GoldBright = Color(0xFFD4AF37)

/** A lighter gold for a dark filled surface that should still read as gold. */
val GoldLight = Color(0xFFE0C15C)

/** Ink for a pale gold surface. 10.83:1 on [GoldPale]. */
val GoldInk = Color(0xFF3A2E06)

/** A pale gold fill for the light scheme. */
val GoldPale = Color(0xFFF6E7B8)

/** A deep gold fill for the dark scheme, carrying [GoldPale] at 7.03:1. */
val GoldDeep = Color(0xFF5A4A12)

// --- Crimson: the one hue outside the two families, and only for failure -----------------------

/**
 * Error red. Not part of the emerald-gold story on purpose: a failure has to look unlike
 * everything that is going well, and a red-free palette would have to say "wrong" in the same
 * colour it says "money".
 */
val Crimson = Color(0xFF9B2226)

val CrimsonInk = Color(0xFF5C1417)
val CrimsonPale = Color(0xFFF7DCDC)
val CrimsonSoft = Color(0xFFF2A0A0)
val CrimsonDeep = Color(0xFF7A1C1F)
val CrimsonNight = Color(0xFF3A0C0D)
