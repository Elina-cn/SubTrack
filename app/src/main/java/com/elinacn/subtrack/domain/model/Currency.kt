package com.elinacn.subtrack.domain.model

/**
 * A currency a subscription can be priced in.
 *
 * The constant name is the ISO 4217 code, so the same value is what gets stored and what the UI
 * hands to the platform formatter.
 *
 * All four carry two minor digits, which is what [Money] assumes. A zero-decimal currency such as
 * JPY would mean revisiting [Money] first, not just adding a line here.
 *
 * [symbol] is the mark the amount is written with, and it is here rather than in `strings.xml`
 * because it is not copy: a lira is a lira in every language, and a translation file is an
 * invitation to change it. It is not read from the platform either - `java.util.Currency` answers
 * with the ISO code whenever the reader's locale has no glyph for the currency, which is the exact
 * behaviour phase 14b removed. See ARCHITECTURE section 6.
 */
enum class Currency(val symbol: String) {
    TRY("₺"),
    USD("$"),
    EUR("€"),
    GBP("£");

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
