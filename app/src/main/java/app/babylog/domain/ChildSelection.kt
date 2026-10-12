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
        val default: ChildSelection = Both
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
