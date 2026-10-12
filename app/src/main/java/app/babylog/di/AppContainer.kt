package app.babylog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.babylog.data.local.AppDatabase
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. No Hilt/Koin: the graph is five objects deep and never changes shape,
 * so a framework would be ceremony that also slows the build down.
 */
class AppContainer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy { AppDatabase.build(context) }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("settings")
        }
    }

    val events: EventRepository by lazy {
        EventRepository(
            eventDao = database.eventDao(),
            outboxDao = database.outboxDao(),
            foodDao = database.foodDao(),
        )
    }

    val children: ChildRepository by lazy { ChildRepository(database.childDao()) }

    val settings: SettingsRepository by lazy { SettingsRepository(dataStore) }
}
