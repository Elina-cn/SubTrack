package com.elinacn.subtrack.ui.statistics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * One line of the breakdown: what the category is, what it costs, and how much of the whole that
 * is - said in words above the bar and drawn in the bar below them.
 *
 * **Both the amount and the share.** The amount answers "how much", the percentage answers "how
 * much of it"; the bar draws the second one and nothing draws the first. Dropping the amount would
 * leave the reader with proportions of an unknown quantity, and dropping the percentage would
 * leave the bar as the only place its own value is written - which is the one place a screen
 * reader cannot go.
 *
 * **One colour for every category.** The palette is deliberately two hues, emerald and gold, and
 * only one of them may be ink in a given scheme (§12). Four distinguishable category colours would
 * mean four more hues invented for this one chart, and the label already carries the distinction -
 * the bar only has to carry the size.
 *
 * **The track is `outlineVariant`, a defined role.** It used to be the bar's own colour faded,
 * because in the old palette primary and primaryContainer were both the same pastel blue in the
 * dark scheme and a bar was indistinguishable from its track. Phase 14a's palette has no such
 * collision - gold against emerald is 4.35:1 - so the transparency is no longer buying anything,
 * and a decorative boundary is exactly what `outlineVariant` is for.
 *
 * Measured, against the background these bars actually sit on: bar to track 4.40:1 in the light
 * scheme and 3.65:1 in the dark one, both above the 3:1 a graphical object needs.
 */
@Composable
fun CategoryBarRow(
    label: String,
    amount: String,
    percent: Int,
    modifier: Modifier = Modifier
) {
    val percentText = stringResource(id = R.string.statistics_percent, percent)
    // Written out here rather than left to merging: mergeDescendants keeps the children in the
    // unmerged tree the accessibility delegate walks, so the row would still offer three stops and
    // one of them would be a bare percentage. The dashboard card hit exactly this in phase 8a.
    // The Canvas below is invisible either way - this sentence is what stands in for it.
    val description = stringResource(
        id = R.string.statistics_category_description,
        label,
        amount,
        percent
    )
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                // weight(1f) and a spacer, not SpaceBetween alone. At font scale 2.0 the two
                // texts fill the line, SpaceBetween has no space left to put between them, and
                // "Productivity" ran straight into "TRY 428,50". The weight makes the label wrap
                // inside its own share instead of pushing against the figure.
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(Dimens.SpacerSmall))
            Text(
                // The two figures the bar cannot say for itself, kept together at the end of the
                // line so the eye reads label first and quantity second.
                text = stringResource(id = R.string.subscription_row_description_more, amount, percentText),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(Dimens.BarLabelSpacing))

        Canvas(modifier = Modifier.fillMaxWidth().height(Dimens.BarHeight)) {
            val corner = CornerRadius(Dimens.BarCorner.toPx())
            // The track first, so every bar is read against the same full width.
            drawRoundRect(color = trackColor, cornerRadius = corner)
            // Float here is geometry, not money: it decides how many pixels wide the bar is and
            // never touches an amount. Driven by the printed percentage rather than the raw
            // cents, so the bar and the number beside it cannot disagree.
            val filled = size.width * (percent / PERCENT)
            if (filled > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(filled, size.height),
                    cornerRadius = corner
                )
            }
        }
    }
}

private const val PERCENT = 100f

@Preview(showBackground = true)
@Composable
private fun CategoryBarRowPreview() {
    SubTrackTheme {
        Column {
            CategoryBarRow(label = "Sağlık", amount = "₺300,00", percent = 50)
            CategoryBarRow(label = "Eğlence", amount = "₺200,00", percent = 33)
            CategoryBarRow(label = "Üretkenlik", amount = "₺100,00", percent = 17)
        }
    }
}
