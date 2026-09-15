package com.elinacn.subtrack.domain.model

/**
 * Which colour scheme the app draws in, as the user asked for it.
 *
 * [SYSTEM] is not "light" with extra steps: it is a standing instruction to follow whatever the
 * device says, so the app changes with it. [LIGHT] and [DARK] are the user overriding that, and
 * the override has to hold even while the system flips underneath it.
 *
 * Stored by constant name rather than ordinal, for the same reason [Currency] is: the file
 * outlives the build that wrote it and an inserted constant would silently re-point every value.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {

        /** What an untouched install draws in. */
        val Default = SYSTEM

        /** Falls back to [Default] for a name this build does not know. */
        fun fromName(name: String): ThemeMode = entries.firstOrNull { it.name == name } ?: Default
    }
}
