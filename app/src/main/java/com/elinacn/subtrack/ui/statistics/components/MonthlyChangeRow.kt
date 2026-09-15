package com.elinacn.subtrack.ui.statistics.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.usecase.MonthlyChange
import com.elinacn.subtrack.domain.usecase.TrendDirection
import com.elinacn.subtrack.ui.common.MoneyFormatter
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.util.Locale

/**
 * How this month compares with last month: an arrow, and a sentence that says the same thing.
 *
 * **The direction is in the words, not in a colour.** Red and green is the obvious way to draw
 * this and it is the wrong instrument here, now more than before. Since phase 14a the palette's
 * neutral ink **is** green: emerald says "this app", not "this went well", so a green arrow would
 * mean nothing and a red one would say "something is broken" about an ordinary month. Colour alone
 * carries no meaning for a reader who cannot tell two hues apart either, and "spending went up" is
 * not obviously bad news: a user who has just added a subscription on purpose is not being warned
 * about anything.
 *
 * So the arrow and the text are both `primary`, and what changed is written out: "Geçen aya göre
 * 150,00 TL arttı". The arrow is decorative - [Icon] with a null description - because the
 * sentence beside it already says which way it points, and a screen reader should hear the fact
 * once.
 *
 * **Nothing is drawn when there is no last month.** No placeholder, no dash, no "—" (phase 10a's
 * rule for a subscription with no date). This composable is only called with a change in hand;
 * absence is the caller's business.
 */
@Composable
fun MonthlyChangeRow(
    change: MonthlyChange,
    amount: String,
    modifier: Modifier = Modifier
) {
    val text = when (change.direction) {
        TrendDirection.UP -> stringResource(id = R.string.statistics_change_up, amount)
        TrendDirection.DOWN -> stringResource(id = R.string.statistics_change_down, amount)
        // No amount in this one: "unchanged by 0,00" is not a sentence anybody says. Worth being
        // explicit about, because passing an argument a resource has no placeholder for is
        // silently ignored rather than reported.
        TrendDirection.UNCHANGED -> stringResource(id = R.string.statistics_change_same)
    }
    val arrow: ImageVector? = when (change.direction) {
        TrendDirection.UP -> Icons.Default.ArrowUpward
        TrendDirection.DOWN -> Icons.Default.ArrowDownward
        // Nothing points sideways in this icon set, and a dash would read as a minus - that is,
        // as a decrease. The sentence carries it alone.
        TrendDirection.UNCHANGED -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (arrow != null) {
            Icon(
                imageVector = arrow,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(Dimens.SpacerSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlyChangeRowPreview() {
    val formatter = MoneyFormatter(Locale("tr", "TR"))
    SubTrackTheme {
        Column {
            TrendDirection.entries.forEach { direction ->
                val change = MonthlyChange(
                    amount = if (direction == TrendDirection.UNCHANGED) Money.ZERO else Money(15_000),
                    direction = direction
                )
                MonthlyChangeRow(
                    change = change,
                    amount = formatter.format(change.amount, Currency.TRY)
                )
            }
        }
    }
}
