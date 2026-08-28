package com.elinacn.subtrack.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A message on its way to the screen, carried without a Context.
 *
 * A ViewModel names the text it wants shown; only [asString] touches resources, and that runs in
 * composition. This is why the type lives in ui/ rather than domain/: resolving it is a Compose
 * concern, and putting it in domain would drag androidx into a package that currently compiles
 * with no imports at all.
 */
sealed interface UiText {

    /** A string resource, resolved against whatever locale is active when it is read. */
    data class Resource(@param:StringRes val id: Int) : UiText

    /** Text that is already final, e.g. a message from a caught exception. */
    data class Raw(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(id)
        is Raw -> value
    }
}
