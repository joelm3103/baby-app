package app.babylog

import android.app.Application
import app.babylog.di.AppContainer

/**
 * The application object. Deliberately does almost nothing.
 *
 * Reminders are armed from the first frame of the UI (`AppScaffold`) and from the boot receiver, NOT from
 * `onCreate`: constructing the container here would make the whole graph eager, and the graph is `by lazy` on
 * purpose so that a process started for a broadcast does not open the database and the preferences file unless
 * it needs them. It also keeps the "one AppContainer per test class" contract `AppStartupTest` documents.
 */
class BabyLogApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
