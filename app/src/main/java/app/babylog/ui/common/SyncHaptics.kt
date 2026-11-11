package app.babylog.ui.common

import app.babylog.data.sync.ManualSyncOutcome

/**
 * What a pull on Today feels like when it is answered.
 *
 * The gesture itself is silent on purpose: `PullToRefreshBox` calls back at threshold, before anything has
 * happened, and a buzz there would say "working" in a language that only has "done" and "refused". The answer
 * is where it earns its keep — a refusal especially, because until the snackbar appears the pull looks inert.
 *
 * A tested value rather than a `when` inside the handler, for the reason `SettingsNav.back()` and
 * `sheetSaveConfig` are: the mapping is the behaviour, and a mapping with no test is how `2 childs` shipped.
 *
 * Settings → `Sync now` deliberately does **not** call this. That button has the Sync card's own message line
 * beside it, and the todo that asked for the buzz scoped it to the pull.
 */
fun Haptics.report(outcome: ManualSyncOutcome) {
    when (outcome) {
        // Something was committed: the drain ran and the queue is what it should be.
        ManualSyncOutcome.SYNCED -> confirm()
        // Refusals: nothing was sent, and the parent's gesture needs an answer it can feel.
        ManualSyncOutcome.COOLDOWN,
        ManualSyncOutcome.ALREADY_RUNNING,
        ManualSyncOutcome.NOT_SIGNED_IN,
        -> reject()
    }
}
