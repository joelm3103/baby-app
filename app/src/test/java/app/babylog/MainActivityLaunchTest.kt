package app.babylog

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Best-effort reproduction of a launch crash on the JVM: build the Activity, drive it through
 * create/start/resume, then force a measure + layout so Compose actually composes.
 *
 * `setContent` alone does not compose — composition happens during the first layout pass — so without
 * the explicit measure/layout this test would pass on an app that crashes the moment a real device
 * tries to draw it.
 *
 * If this test fails on Robolectric infrastructure rather than on our code, delete it; it is a
 * diagnostic, not a contract. The real evidence for a device crash is `adb logcat -b crash -d`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MainActivityLaunchTest {

    @Test
    fun `main activity composes its first frame without throwing`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.setup().get()

        // Force composition: measure and lay out the whole decor view at a plausible phone size.
        val decor = activity.window.decorView
        decor.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, 1080, 2340)

        assertFalse("the activity must not have finished itself", activity.isFinishing)
    }
}
