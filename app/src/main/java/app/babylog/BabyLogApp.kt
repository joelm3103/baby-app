package app.babylog

import android.app.Application
import app.babylog.di.AppContainer

class BabyLogApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
