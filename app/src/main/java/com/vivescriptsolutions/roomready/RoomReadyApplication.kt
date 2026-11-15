package com.vivescriptsolutions.roomready

import android.app.Application
import com.vivescriptsolutions.roomready.data.connectivity.ConnectivityMonitor
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository

class AppContainer(application: Application) {
  val connectivityMonitor: ConnectivityMonitor by lazy { ConnectivityMonitor(application) }
  val database: AppDatabase by lazy { AppDatabase.build(application) }
  val preferencesRepository: UserPreferencesRepository by lazy {
    UserPreferencesRepository(application)
  }
  val housekeepingRepository: HousekeepingRepository by lazy {
    HousekeepingRepository(database, preferencesRepository)
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
