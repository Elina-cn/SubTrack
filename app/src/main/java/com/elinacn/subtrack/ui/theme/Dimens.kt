package com.elinacn.subtrack.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing, corner and elevation values shared across the app. */
object Dimens {

    /** Horizontal inset of cards from the screen edge. */
    val ScreenPadding = 16.dp

    /** Vertical gap between two list rows. */
    val RowSpacing = 8.dp

    /** Padding inside a subscription card. */
    val CardPadding = 16.dp

    /** Padding inside the dashboard card. */
    val DashboardPadding = 24.dp

    /** Horizontal padding of the add sheet. */
    val SheetPadding = 24.dp

    /** Extra room under the sheet content so it clears the system navigation bar. */
    val SheetBottomPadding = 40.dp

    /** Free space under the list so the floating action button never covers the last row. */
    val ListBottomSpacing = 80.dp

    /** Leading inset of the section heading. */
    val SectionTitleStart = 20.dp

    /** Space above the section heading. */
    val SectionTitleTop = 16.dp

    /** Space below the section heading. */
    val SectionTitleBottom = 8.dp

    /** Corner radius of subscription cards and the save button. */
    val CardCorner = 12.dp

    /** Corner radius of the dashboard card. */
    val DashboardCorner = 24.dp

    /** Corner radius of the floating action button. */
    val FabCorner = 16.dp

    /** Resting elevation of a subscription card. */
    val CardElevation = 2.dp

    /** Resting elevation of the dashboard card. */
    val DashboardElevation = 8.dp

    /** Smallest square a finger can be asked to hit; the Material accessibility floor. */
    val MinTouchTarget = 48.dp

    /** Size of the leading icon on a subscription card. */
    val IconSize = 24.dp

    /** The glyph above an empty-state message. Large enough to read as an illustration. */
    val EmptyStateIconSize = 72.dp

    /** Breathing room around an empty-state message, which sits alone on the screen. */
    val EmptyStatePadding = 32.dp

    /**
     * Thickness of a breakdown bar.
     *
     * Thick enough to read as a quantity at a glance, thin enough that four of them plus their
     * labels stay above the fold on a 360dp screen.
     */
    val BarHeight = 12.dp

    /** Corner radius of a breakdown bar. Half its height, so the ends are round. */
    val BarCorner = 6.dp

    /** Gap between a breakdown bar and the label above it. */
    val BarLabelSpacing = 6.dp

    /**
     * Height of the trend chart's plot area.
     *
     * Tall enough that a month worth half of another is visibly half of it, short enough that the
     * chart, its scale label and its month labels all fit under the sections above it on a 360dp
     * screen without the reader losing the section heading off the top.
     */
    val TrendChartHeight = 120.dp

    /**
     * Corner radius of a trend column.
     *
     * Smaller than [BarCorner]: a column can be a few pixels tall, and a 6dp radius on a 2dp
     * column draws a lens rather than a bar.
     */
    val TrendBarCorner = 3.dp

    /**
     * The least a column may be drawn as while still standing for a real amount.
     *
     * A month that cost a fraction of the peak rounds to no pixels at all, and a column of nothing
     * is how this chart says "recorded as zero". Anything above zero gets at least this much so the
     * two cannot be confused.
     */
    val TrendBarMinHeight = 2.dp

    /** Gap between an icon and the text that follows it. */
    val IconSpacing = 16.dp

    /** Small vertical gap, e.g. between a label and its value. */
    val SpacerSmall = 8.dp

    /** Gap between two form fields. */
    val SpacerMedium = 12.dp

    /** Gap under the sheet title. */
    val SpacerLarge = 20.dp

    /** Gap above the sheet's primary action. */
    val SpacerXLarge = 24.dp
}
