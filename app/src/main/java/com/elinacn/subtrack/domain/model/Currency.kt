package com.elinacn.subtrack.domain.model

/**
 * A currency a subscription can be priced in.
 *
 * The constant name is the ISO 4217 code, so the same value is what gets stored and what the UI
 * hands to the platform formatter.
 *
 * All four carry two minor digits, which is what [Money] assumes. A zero-decimal currency such as
 * JPY would mean revisiting [Money] first, not just adding a line here.
 */
enum class Currency {
    TRY,
    USD,
    EUR,
    GBP;

    companion object {

        /**
         * The currency totals are shown in, and what a new subscription starts as.
         *
         * Phase 9b turns this into a stored preference. Until then everything reads it from here,
         * so switching it is a one-line change rather than a search.
         */
        val Base = TRY

        /** Falls back to [Base] for an unknown code, the way the mapper treats the other enums. */
        fun fromCode(code: String): Currency = entries.firstOrNull { it.name == code } ?: Base
    }
}
