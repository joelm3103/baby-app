package app.babylog.domain

import java.util.Locale

/**
 * How a dose is measured. Not a unit family with conversions — nothing here converts, and nothing here
 * is clinical advice: a dose is a record of what a parent gave, in the words and measures the bottle,
 * the syringe or the sachet in their hand actually uses.
 *
 * The list is deliberately short and inclusive rather than exhaustive. `OTHER` exists so that a parent
 * is never forced to mis-describe a dose to satisfy a picker, and it is a real answer rather than a
 * failure — the alternative is a free-text unit field, which turns "2.5 ml" and "2.5ML" and "2.5 mls"
 * into three different things in a log that may later be read by someone else.
 *
 * Three words per unit because they are three different jobs, and collapsing them was a bug: [label] is
 * what the picker offers ("drops"), while a FORMATTED dose needs the word that fits the amount ("1 drop",
 * "2 drops"). The first version had only [label], and consequently wrote "1 drops" onto a fever card.
 */
enum class DoseUnit(val label: String, val singular: String, val plural: String) {
    MILLILITRES("ml", "ml", "ml"),
    MILLIGRAMS("mg", "mg", "mg"),
    DROPS("drops", "drop", "drops"),
    SACHET("sachet", "sachet", "sachets"),
    TABLET("tablet", "tablet", "tablets"),
    OTHER("other", "other", "other");

    /**
     * The word that fits [amount]. Up to one takes the singular, because a FRACTION of a measure is still
     * one measure — "half a sachet", "0.5 sachet" — while anything above one takes the plural
     * ("1.5 sachets", "2 sachets").
     */
    fun wordFor(amount: Double): String = if (amount <= 1.0) singular else plural
}

/**
 * "2.5 ml", "1 drop", "2 sachets". Null when either half is missing, because half a dose is not a dose.
 */
fun formatDose(amount: Double?, unit: DoseUnit?): String? {
    if (amount == null || unit == null) return null
    return "${doseNumber(amount)} ${unit.wordFor(amount)}"
}

/**
 * The amount alone, as editable text: "2.5" for 2.5, "5" for 5.0.
 *
 * A trailing ".0" is dropped the way the rest of the app drops it, and fractional amounts are trimmed
 * rather than padded, so the dose box opens on the number the row shows.
 */
fun doseNumber(amount: Double): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        String.format(Locale.ENGLISH, "%.2f", amount).trimEnd('0').trimEnd('.')
    }
