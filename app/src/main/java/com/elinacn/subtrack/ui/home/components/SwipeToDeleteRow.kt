package com.elinacn.subtrack.ui.home.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.roundToInt

/** Fraction of the row width a swipe must cover before it deletes. */
private const val DeleteThresholdFraction = 0.5f

/**
 * A row that deletes itself when swiped past half its width toward the end edge.
 *
 * Written by hand instead of using SwipeToDismissBox: that component settles a below-threshold
 * swipe with an animation that a new gesture cancels at whatever offset it had reached, and the
 * offsets accumulate across successive swipes until the row crosses the anchor and disappears.
 * Owning the animation lets the gesture start from a guaranteed zero.
 *
 * Velocity is deliberately ignored - distance alone decides. A fast flick that covers little
 * ground must not delete, which is what repeatedly went wrong with the library component.
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Plain state, not an Animatable. Animatable guards snapTo and animateTo with one mutex, and
    // the drag deltas had to reach it through scope.launch, so a queued snapTo landed after the
    // dismissal animation had started and cancelled it - taking the onDelete call down with it.
    // Driving the offset synchronously removes the race entirely.
    var offsetX by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableIntStateOf(0) }

    // Swiping goes toward the end edge: leftwards in LTR, rightwards in RTL.
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f

    // Reading offsetX straight in the condition would recompose on every animation frame.
    val isSwiping by remember { derivedStateOf { offsetX != 0f } }

    // Hoisted out of the semantics lambda, which is not a composable scope.
    val deleteLabel = stringResource(id = R.string.delete)

    val dragState = rememberDraggableState { delta ->
        val dragged = offsetX + delta
        val limit = rowWidth.toFloat()
        // Only allow travel toward the end edge; the other direction stays pinned at rest.
        offsetX = if (towardsEnd < 0f) dragged.coerceIn(-limit, 0f) else dragged.coerceIn(0f, limit)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width }
            // mergeDescendants is what makes this reachable: without it the card's own text nodes
            // take the accessibility focus and the action stays on an unfocusable parent, so
            // TalkBack never offers it. Merging turns the row into one focusable node.
            .semantics(mergeDescendants = true) {
                // Swiping is unreachable with TalkBack, so expose deletion as an explicit action.
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
            }
    ) {
        if (isSwiping) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        RoundedCornerShape(Dimens.CardCorner)
                    ),
                contentAlignment = if (towardsEnd < 0f) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    // Every gesture starts from rest. This is what makes accumulation impossible:
                    // a leftover offset from an interrupted settle is wiped before the drag reads it.
                    onDragStarted = { offsetX = 0f },
                    onDragStopped = {
                        val travelled = abs(offsetX)
                        if (rowWidth > 0 && travelled >= rowWidth * DeleteThresholdFraction) {
                            animate(offsetX, towardsEnd * rowWidth) { value, _ -> offsetX = value }
                            onDelete()
                        } else {
                            animate(offsetX, 0f) { value, _ -> offsetX = value }
                        }
                    }
                )
        ) {
            content()
        }
    }
}
