package com.elinacn.subtrack.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.usecase.TrendPoint
import com.elinacn.subtrack.ui.common.MoneyFormatter
import com.elinacn.subtrack.ui.common.MonthFormatter
import com.elinacn.subtrack.ui.common.rememberMoneyFormatter
import com.elinacn.subtrack.ui.common.rememberMonthFormatter
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min

/**
 * What the months cost, drawn as one column each and said in a sentence underneath the drawing.
 *
 * **Columns rather than a line.** A line has to do something with a month that has no figure, and
 * both answers are wrong: a break in the line reads as a rendering fault, and a straight segment
 * across the gap draws a number nobody recorded. A column chart simply leaves the slot empty. It
 * also gives this chart the three states it needs, which a line cannot show at all: a month with a
 * figure (track and fill), a month recorded as **zero** (track, no fill), and a month with **no
 * row** (nothing). Phase 12a writes an empty list as zero on purpose so those last two stay apart
 * (ARCHITECTURE §19), and a chart that drew them alike would throw that away.
 *
 * **One colour, as in the breakdown.** Our scheme defines primary and primaryContainer and little
 * else (§12), and the track is the bar's own colour faded - measured in phase 13a, where
 * primaryContainer turned out to be the same PastelBlue as primary in the dark scheme and made
 * every bar look full.
 *
 * The fade cannot be deepened to make an empty track easier to see. Measured on the device, a
 * column reads 3.96:1 against the light background and 9.25:1 against the dark one, and 3.01:1
 * against its own track in the light scheme - already at the 3:1 floor a graphical object is asked
 * for. More alpha would move the track towards the column and take that below the floor. So the
 * empty track stays faint, and the difference between "recorded as zero" and "no record" is
 * carried in full by the sentence, which says one or the other in words.
 *
 * **Two sets of labels, neither of which can collide.** The scale is written once, above the plot,
 * as the highest month in the window; the months are written once each, under their own column.
 * An amount over every column would need six amounts across 328dp, which does not fit at any font
 * scale, and an amount axis down the left would take a third of the plot width at font scale 2.0.
 *
 * **Accessibility: the Canvas is invisible, the block speaks.** A Canvas contributes nothing to the
 * accessibility tree, so the whole chart is one focus stop reading one sentence - the same shape as
 * a breakdown row in 13a, and `clearAndSetSemantics` rather than merging for the same reason: the
 * delegate walks the unmerged tree, so merging would leave the scale label and six month
 * abbreviations behind as stops of their own.
 */
@Composable
fun MonthlyTrendChart(
    points: List<TrendPoint>,
    peak: Money?,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val moneyFormatter = rememberMoneyFormatter()
    val monthFormatter = rememberMonthFormatter()
    val description = trendDescription(points, currency, moneyFormatter, monthFormatter)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = barColor.copy(alpha = TRACK_ALPHA)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        if (peak != null) {
            Text(
                text = stringResource(
                    id = R.string.statistics_trend_scale,
                    moneyFormatter.format(peak, currency)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Dimens.BarLabelSpacing))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.TrendChartHeight)
        ) {
            val slotWidth = size.width / points.size
            // Capped, not just a share of the slot: two months would otherwise be drawn as two
            // 98dp blocks - measured, not guessed - and a column's width would mean "how many
            // months do you have" rather than nothing at all.
            val barWidth = min(slotWidth * BAR_WIDTH_RATIO, Dimens.TrendBarMaxWidth.toPx())
            val corner = CornerRadius(Dimens.TrendBarCorner.toPx())
            val minimumHeight = Dimens.TrendBarMinHeight.toPx()
            val peakCents = peak?.cents ?: 0L

            points.forEachIndexed { index, point ->
                // No row for this month: no track either. An empty slot is what "no figure" looks
                // like, and it is the one thing a track would turn into "zero".
                val total = point.total ?: return@forEachIndexed
                val left = index * slotWidth + (slotWidth - barWidth) / 2f

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = corner
                )

                // Float here is geometry, not money: it decides how many pixels tall the column is
                // and never touches an amount. Scaled against the tallest month rather than against
                // a round number, so the shape of the window fills the height it is given.
                val scaled = if (peakCents > 0L) size.height * (total.cents.toFloat() / peakCents) else 0f
                val height = if (total > Money.ZERO) max(scaled, minimumHeight) else scaled
                if (height > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.BarLabelSpacing))

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = monthFormatter.short(point.period),
                    // The same equal share the columns are drawn in, so a label sits under its own
                    // column however wide the screen is.
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The whole chart as one sentence: every month in order, with its figure or with "no record".
 *
 * Built in a loop rather than with joinToString because `stringResource` can only be called from a
 * composable, and joinToString's transform is not one.
 */
@Composable
private fun trendDescription(
    points: List<TrendPoint>,
    currency: Currency,
    moneyFormatter: MoneyFormatter,
    monthFormatter: MonthFormatter
): String {
    val spoken = mutableListOf<String>()
    for (point in points) {
        val month = monthFormatter.full(point.period)
        val total = point.total
        spoken += if (total == null) {
            stringResource(id = R.string.statistics_trend_point_missing, month)
        } else {
            stringResource(
                id = R.string.statistics_trend_point,
                month,
                moneyFormatter.format(total, currency)
            )
        }
    }
    val separator = stringResource(id = R.string.statistics_trend_separator)
    return stringResource(id = R.string.statistics_trend_description, spoken.joinToString(separator))
}

/**
 * How much of its slot a column takes up.
 *
 * The rest is the gap between columns. Wide enough to read as a quantity, narrow enough that six
 * of them on a 360dp screen are six things rather than one striped block.
 */
private const val BAR_WIDTH_RATIO = 0.6f

/** The same faded primary the breakdown bars use, measured in both schemes in phase 13a. */
private const val TRACK_ALPHA = 0.24f

@Preview(showBackground = true)
@Composable
private fun MonthlyTrendChartPreview() {
    val september = YearMonth.of(2026, 9)
    SubTrackTheme {
        MonthlyTrendChart(
            points = listOf(
                TrendPoint(september.minusMonths(5), Money(120_000)),
                TrendPoint(september.minusMonths(4), Money(145_000)),
                // No row for this month at all.
                TrendPoint(september.minusMonths(3), null),
                // Recorded, and it was nothing.
                TrendPoint(september.minusMonths(2), Money.ZERO),
                TrendPoint(september.minusMonths(1), Money(90_000)),
                TrendPoint(september, Money(200_200))
            ),
            peak = Money(200_200),
            currency = Currency.TRY
        )
    }
}
