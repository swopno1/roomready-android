package com.vivescriptsolutions.roomready

import android.app.Application
import android.content.Context
import com.vivescriptsolutions.roomready.data.connectivity.ConnectivityMonitor
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.notifications.RoomDeepLink
import com.vivescriptsolutions.roomready.data.notifications.RoomReadyNotifier
import com.vivescriptsolutions.roomready.data.sync.ApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.KeystoreApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.SyncRepository
import com.vivescriptsolutions.roomready.domain.AppLanguage
import com.vivescriptsolutions.roomready.ui.locale.wrapWithLanguage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

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
  val notifier: RoomReadyNotifier by lazy { RoomReadyNotifier(application) }
  val deepLinkChannel: Channel<RoomDeepLink> = Channel(Channel.BUFFERED)
  val housekeepingRepository: HousekeepingRepository by lazy {
    HousekeepingRepository(
      database,
      preferencesRepository,
      onOutboxAppended = { syncRepository.enqueueSync() },
      onNotify = { event -> notifier.notify(event) },
    )
  }
}

class RoomReadyApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun attachBaseContext(base: Context) {
    // One small blocking read so every activity starts in the saved
    // language; SYSTEM leaves the device locale untouched.
    val language =
      runBlocking {
        runCatching { UserPreferencesRepository(base).appLanguage() }
          .getOrDefault(AppLanguage.SYSTEM)
      }
    super.attachBaseContext(base.wrapWithLanguage(language))
  }

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    container.connectivityMonitor.start()
    container.notifier.ensureChannels()
  }
}
