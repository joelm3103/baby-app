package app.babylog.domain

import app.babylog.domain.ChildSelection.Both
import app.babylog.domain.ChildSelection.One

/**
 * Which child the UI is currently showing. Twins are the default case here, not an edge case: `Both`
 * is the default selection and it interleaves both children's data.
 */
sealed interface ChildSelection {
    data object Both : ChildSelection
    data class One(val childId: String) : ChildSelection

    /**
     * The child ids a query should use. If the selected child no longer exists — deleted, or an id
     * from a stale preference — fall back to Both rather than showing an empty screen with no
     * explanation.
     */
    fun resolve(available: List<String>): List<String> = when (this) {
        is Both -> available
        is One -> if (childId in available) listOf(childId) else available
    }

    companion object {
        /**
         * COMPUTED, not stored. Do not "simplify" this back to `val default: ChildSelection = Both`.
         *
         * Storing it crashes the app on launch. On Android, ART initialises this interface - and
         * therefore this companion - *while* `Both` is still being constructed, so the initialiser
         * reads `Both`'s static INSTANCE field before it has been assigned and captures **null**
         * permanently. `decodeChildSelection` then returns null for every absent preference, and the
         * dashboard's `resolve()` call dies with an NPE.
         *
         * HotSpot initialises superinterfaces more lazily and sees `Both` already assigned, so this
         * bug is invisible to JVM and Robolectric tests. It cost a crash-on-launch that 97 green
         * tests did not catch. A getter reads the field at call time, after initialisation.
         */
        val default: ChildSelection get() = Both
    }
}

/** Stable wire format for storage: "BOTH" or "ONE:<childId>". */
fun encodeChildSelection(selection: ChildSelection): String = when (selection) {
    is Both -> "BOTH"
    is One -> "ONE:${selection.childId}"
}

fun decodeChildSelection(raw: String?): ChildSelection = when {
    raw == null -> ChildSelection.default
    raw == "BOTH" -> Both
    raw.startsWith("ONE:") -> raw.removePrefix("ONE:").takeIf { it.isNotBlank() }?.let { One(it) }
        ?: ChildSelection.default
    else -> ChildSelection.default
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }
