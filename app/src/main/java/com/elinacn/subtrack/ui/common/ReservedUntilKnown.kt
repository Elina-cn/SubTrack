package com.elinacn.subtrack.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Keeps an element's place in the layout but draws nothing and announces nothing until [isKnown].
 *
 * For a value that is still being read from storage. Drawing a default in its place puts a wrong
 * value on screen for as long as the read takes, and leaving the element out makes everything
 * under it jump when the value arrives; laid out but invisible does neither. The semantics go
 * with the pixels, so a screen reader cannot announce the placeholder either. See ARCHITECTURE
 * section 29.
 *
 * Touch is not blocked here - the caller hands the element a no-op or a disabled state while the
 * value is unknown, because only the caller knows what the element does.
 */
fun Modifier.reservedUntilKnown(isKnown: Boolean): Modifier =
    if (isKnown) this else this.alpha(0f).clearAndSetSemantics { }

/**
 * Starts with null - "not read yet" - and then carries the stored value.
 *
 * The ViewModel half of [reservedUntilKnown]. Without it a combine over the store waits for every
 * source before emitting anything, and the screen is left on its initial value for as long as the
 * slowest one takes; with it each value turns up on its own.
 */
fun <T : Any> Flow<T>.startingUnknown(): Flow<T?> = map<T, T?> { it }.onStart { emit(null) }
