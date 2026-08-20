package com.elinacn.subtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.util.Locale
import com.elinacn.subtrack.ui.theme.*

// Double is wrong for money and CLAUDE.md forbids it; phase 2 replaces this with a Money
// value class holding Long minor units.
data class Subscription(
    val id: Int,
    val name: String,
    val price: Double
)

/**
 * Keeps the subscription list alive across configuration changes by flattening each item into
 * Bundle-native values. Temporary: Room replaces this in phase 5.
 */
private val SubscriptionListSaver: Saver<SnapshotStateList<Subscription>, *> = listSaver(
    save = { list -> list.flatMap { listOf(it.id, it.name, it.price) } },
    restore = { flat ->
        flat.chunked(3)
            .map { (id, name, price) -> Subscription(id as Int, name as String, price as Double) }
            .toMutableStateList()
    }
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubTrackTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    // Screen state
    var showBottomSheet by remember { mutableStateOf(false) }
    var subscriptionName by remember { mutableStateOf("") }
    var subscriptionPrice by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Session-local subscription list. Room takes over in phase 5.
    val subscriptionList = rememberSaveable(saver = SubscriptionListSaver) {
        mutableStateListOf(
            Subscription(1, "Netflix", 159.99),
            Subscription(2, "Spotify", 59.90)
        )
    }

    // Monotonic id source. Deriving ids from max+1 handed a removed row's id straight back to
    // the next one, so the LazyColumn key stopped being unique and swipe state leaked across
    // rows. Starts past the seed ids above. Temporary: Room's autoGenerate replaces it in phase 5.
    var nextSubscriptionId by rememberSaveable { mutableIntStateOf(3) }

    // Monthly total, recomputed only when the list actually changes.
    val totalMonthlyPrice by remember(subscriptionList.size) {
        derivedStateOf {
            subscriptionList.sumOf { it.price }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Dimens.FabCorner)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_subscription)
                )
            }
        }
    ) { paddingValues ->
        // LazyColumn rather than Column so long lists only compose what is on screen.
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimens.ListBottomSpacing) // room for the FAB
        ) {
            // Fixed header
            item {
                DashboardCard(
                    totalAmount = String.format(
                        Locale.US,
                        stringResource(id = R.string.price_format),
                        totalMonthlyPrice
                    )
                )

                Text(
                    text = stringResource(id = R.string.my_subscriptions),
                    modifier = Modifier.padding(
                        start = Dimens.SectionTitleStart,
                        top = Dimens.SectionTitleTop,
                        bottom = Dimens.SectionTitleBottom
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Subscription rows
            items(
                items = subscriptionList,
                key = { it.id } // Stable identity, so a deletion does not shuffle state between rows
            ) { sub ->
                SwipeToDeleteRow(onDelete = { subscriptionList.remove(sub) }) {
                    SubscriptionCard(
                        name = sub.name,
                        price = String.format(
                            Locale.US,
                            stringResource(id = R.string.price_format),
                            sub.price
                        )
                    )
                }
            }
        }
    }

    // Add sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SheetPadding)
                    .padding(bottom = Dimens.SheetBottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.add_subscription_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(Dimens.SpacerLarge))

                OutlinedTextField(
                    value = subscriptionName,
                    onValueChange = { subscriptionName = it },
                    label = { Text(stringResource(id = R.string.subscription_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Dimens.SpacerMedium))

                OutlinedTextField(
                    value = subscriptionPrice,
                    onValueChange = { subscriptionPrice = it },
                    label = { Text(stringResource(id = R.string.subscription_price_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))

                Button(
                    onClick = {
                        if (subscriptionName.isNotBlank()) {
                            val priceDouble = subscriptionPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
                            subscriptionList.add(
                                Subscription(
                                    id = nextSubscriptionId,
                                    name = subscriptionName,
                                    price = priceDouble
                                )
                            )
                            nextSubscriptionId++
                            subscriptionName = ""
                            subscriptionPrice = ""
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) showBottomSheet = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.CardCorner)
                ) {
                    Text(stringResource(id = R.string.save))
                }
            }
        }
    }
}

// Building blocks

/** Fraction of the row width a swipe must cover before it deletes. */
private const val DeleteThresholdFraction = 0.5f

/**
 * A row that deletes itself when swiped past half its width toward the end edge.
 *
 * Written by hand instead of using SwipeToDismissBox: that component settles a below-threshold
 * swipe with an animation that a new gesture cancels at whatever offset it had reached, and the
 * offsets accumulate across successive swipes until the row crosses the anchor and disappears.
 * Owning the Animatable lets the gesture start from a guaranteed zero.
 *
 * Velocity is deliberately ignored - distance alone decides. A fast flick that covers little
 * ground must not delete, which is what repeatedly went wrong with the library component.
 */
@Composable
private fun SwipeToDeleteRow(
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

@Composable
fun getIconForSubscription(name: String): ImageVector {
    return when (name.lowercase()) {
        "netflix" -> Icons.Default.PlayArrow
        "spotify" -> Icons.AutoMirrored.Filled.List
        "youtube" -> Icons.Default.PlayArrow
        "icloud", "drive" -> Icons.Default.Cloud
        else -> Icons.Default.Star
    }
}

@Composable
fun DashboardCard(totalAmount: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPadding),
        shape = RoundedCornerShape(Dimens.DashboardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.DashboardElevation)
    ) {
        Column(modifier = Modifier.padding(Dimens.DashboardPadding)) {
            Text(
                text = stringResource(id = R.string.total_monthly),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(Dimens.SpacerSmall))
            Text(
                text = totalAmount,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
fun SubscriptionCard(name: String, price: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RowSpacing),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getIconForSubscription(name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconSize)
            )
            Spacer(modifier = Modifier.width(Dimens.IconSpacing))
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = price,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SubTrackPreview() {
    SubTrackTheme { MainScreen() }
}