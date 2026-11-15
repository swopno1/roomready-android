package com.vivescriptsolutions.roomready

import android.app.Application
import com.vivescriptsolutions.roomready.data.connectivity.ConnectivityMonitor
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.sync.ApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.KeystoreApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.SyncRepository

class AppContainer(application: Application) {
  val connectivityMonitor: ConnectivityMonitor by lazy { ConnectivityMonitor(application) }
  val database: AppDatabase by lazy { AppDatabase.build(application) }
  val preferencesRepository: UserPreferencesRepository by lazy {
    UserPreferencesRepository(application)
  }
  val apiKeyStore: ApiKeyStore by lazy { KeystoreApiKeyStore(application) }
  val syncRepository: SyncRepository by lazy {
    val prefs = preferencesRepository
    val keys = apiKeyStore
    val api =
      PmsApi(
        baseUrlProvider = { prefs.connection().baseUrl },
        keyProvider = { keys.getKey() },
      )
    SyncRepository(application, database, prefs, keys, api)
  }
  val housekeepingRepository: HousekeepingRepository by lazy {
    HousekeepingRepository(database, preferencesRepository) { syncRepository.enqueueSync() }
  }
}

class RoomReadyApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    container.connectivityMonitor.start()
  }
}
