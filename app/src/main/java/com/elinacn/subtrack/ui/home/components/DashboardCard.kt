package com.elinacn.subtrack.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/** The monthly total, already formatted by the caller. */
@Composable
fun DashboardCard(
    totalAmount: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPadding),
        shape = RoundedCornerShape(Dimens.DashboardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.DashboardElevation)
    ) {
        Column(modifier = Modifier.padding(Dimens.DashboardPadding)) {
            Text(
                text = stringResource(id = R.string.total_monthly),
                // No alpha: dimming this to 0.7 dropped it to 3.6:1. The size and weight gap
                // against the amount below already carries the hierarchy.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
            Text(
                text = totalAmount,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardCardPreview() {
    SubTrackTheme {
        DashboardCard(totalAmount = "219.89 TL")
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardCardEmptyPreview() {
    SubTrackTheme {
        DashboardCard(totalAmount = "0.00 TL")
    }
}
