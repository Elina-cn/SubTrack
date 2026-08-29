package com.elinacn.subtrack.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import kotlinx.coroutines.delay

/** How long loading has to last before it is worth telling the user about. */
private const val ShowAfterMillis = 300L

/**
 * A spinner that only appears once loading has lasted long enough to be noticed.
 *
 * Reading a local Room table usually finishes in a few dozen milliseconds. Showing a spinner for
 * that long is worse than showing nothing: the flash reads as a glitch rather than as progress.
 * Waiting [ShowAfterMillis] means the fast path stays silent and only a genuinely slow load
 * explains itself.
 *
 * The delay is presentation timing, not a decision about the data - the screen still renders
 * whatever state it is handed.
 *
 * [showAfterMillis] exists so a preview can render the spinner without waiting for it.
 */
@Composable
fun DelayedLoadingIndicator(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    showAfterMillis: Long = ShowAfterMillis
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isVisible = false
            return@LaunchedEffect
        }
        delay(showAfterMillis)
        isVisible = true
    }

    if (!isVisible) return

    val label = stringResource(id = R.string.loading)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.DashboardPadding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            // An indeterminate spinner carries no text of its own, so a screen reader would
            // otherwise announce nothing at all while the app is busy.
            modifier = Modifier.semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DelayedLoadingIndicatorPreview() {
    SubTrackTheme {
        DelayedLoadingIndicator(isLoading = true, showAfterMillis = 0)
    }
}
