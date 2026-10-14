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
     * The child ids a query should use. A stale selection must never leave an empty screen, so `Both`
     * is honoured only when it means what it says (exactly two children), and a `One` whose child no
     * longer exists falls back to the first child in sort order.
     */
    fun resolve(available: List<String>): List<String> = when (this) {
        is Both -> if (available.size == 2) available else available.take(1)
        is One -> if (childId in available) listOf(childId) else available.take(1)
    }

    /**
     * Whether this selection still makes sense for [children].
     *
     * `Both` is valid ONLY for exactly two children: it is a twins view, so for one child it is a
     * redundant pill and for three it silently means "all of them", which nobody asked for.
     */
    fun isValidFor(children: List<Child>): Boolean = when (this) {
        is Both -> children.size == 2
        is One -> children.any { it.id == childId }
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

        /**
         * The selection to use when the current one is invalid: `Both` at exactly two children,
         * otherwise the first child in sort order.
         *
         * Falls back rather than remembering, so a hidden `Both` is never silently restored when a
         * third child is added and later removed.
         */
        fun fallbackFor(children: List<Child>): ChildSelection =
            if (children.size == 2) Both else children.firstOrNull()?.let { One(it.id) } ?: Both
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
