package app.babylog.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What a press feels like, decided in one place.
 *
 * Haptics are a language, not a decoration: if every surface buzzed the same way, the buzz would carry no
 * information and a parent would turn it off. So there are five, and each means one thing:
 *
 * - [tap] — something acted: a chip, a card button, a quick pill, a child pill;
 * - [toggle] — something changed state: a switch, a row with an on/off answer;
 * - [confirm] — something was committed: a sheet's Save;
 * - [longPress] — the sustained press that starts a delete;
 * - [reject] — the request was refused: a manual sync that is cooling down, already running, or signed out.
 *
 * [reject] was added in Phase 30 for the pull on Today, where the answer arrives after the gesture and
 * nothing has visibly happened until it does. It is `HapticFeedbackType.Reject` — a real constant in Compose
 * UI 1.12.1, not a repurposed one — and a fifth verb rather than a second meaning for [toggle], because a
 * shared verb is what makes a vibrate uninformative. The platform has no `REJECT` haptic constant before API
 * 30, so on API 26-29 this may be silent; the snackbar that accompanies it is still the message.
 *
 * `LongPress` is the one constant every Compose version has, so it is the fallback the others degrade to
 * rather than each call site inventing its own. The device's own "touch feedback" setting still wins:
 * Compose forwards to `View.performHapticFeedback`, which respects it, so there is no preference in this
 * app — a parent who wants silence turns it off once, system-wide.
 */
class Haptics internal constructor(private val feedback: HapticFeedback) {

    fun tap() = vibrate(HapticFeedbackType.TextHandleMove)

    fun toggle() = vibrate(HapticFeedbackType.ToggleOn)

    fun confirm() = vibrate(HapticFeedbackType.Confirm)

    fun longPress() = vibrate(HapticFeedbackType.LongPress)

    /** The request was refused. See the class comment for why this is its own verb. */
    fun reject() = vibrate(HapticFeedbackType.Reject)

    private fun vibrate(type: HapticFeedbackType) {
        // A device without a vibrator, or one that refuses the call, must not take a screen down: every
        // call site is a plain tap handler, and a haptic is the least important thing it does.
        runCatching { feedback.performHapticFeedback(type) }
    }
}

/** The helper every screen uses. One instance per composition, so the feedback handle is not re-read per tap. */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
