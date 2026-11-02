package app.babylog.ui.home

import app.babylog.data.repo.EventDraft
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.EventType

/**
 * The draft one tap on a bottle preset writes, or null when the amount is not a bottle.
 *
 * Extracted from the ViewModel so the one thing worth testing about the quick-log path is reachable without a
 * database: `HomeViewModel.state` is Room-backed, and Robolectric cannot collect a Room-backed Flow (the paused
 * main looper never pumps the invalidation callbacks), so a test of `logBottle` itself would hang rather than
 * fail. What this returns is the whole of the decision — which activity, which amount, what time — and the
 * "one row per child with an identical timestamp" half of it lives in `EventRepository.log`, which is already
 * covered against a real database.
 *
 * The range is the repository's own cap, not a second copy of the number: a preset the settings screen would
 * refuse to store is a preset this refuses to log. Zero and negatives are refused for the same reason — a
 * nought-millilitre feed is not a record, it is a mis-tap.
 */
internal fun quickBottleDraft(amountMl: Int, at: Long): EventDraft? {
    if (amountMl !in 1..SettingsRepository.MAX_BOTTLE_PRESET_ML) return null
    return EventDraft(type = EventType.BOTTLE, startedAt = at, amountMl = amountMl)
}
