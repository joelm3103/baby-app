package app.babylog.ui.history

import app.babylog.domain.EventType

/**
 * What the Summary filter row's one bulk chip reads and does, from the current filter state.
 *
 * The row had six activity chips and no bulk action, so clearing the screen meant six taps. One control
 * now carries two meanings, and the case worth stating is a PARTIAL selection — `hidden` neither empty
 * nor full — where the chip reads `All`. Reading `None` there would make "show every activity"
 * unreachable from a mixed row without first turning the hidden chips back on, and the three states
 * (all shown / partial / none shown) would stop cycling through the one control.
 *
 * Kept out of the composable for the usual reason: this is the only decision in that file that can be
 * wrong in a way a screenshot does not show, and it is testable on the JVM without a device.
 */
data class BulkFilterAction(val label: String, val showAll: Boolean)

/** Everything shown -> offers `None`. Anything hidden, a partial selection included -> offers `All`. */
fun bulkFilterAction(hidden: Set<EventType>): BulkFilterAction =
    if (hidden.isEmpty()) BulkFilterAction(label = "None", showAll = false)
    else BulkFilterAction(label = "All", showAll = true)
