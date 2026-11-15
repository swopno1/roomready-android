package com.vivescriptsolutions.roomready.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.local.SyncStateEntity
import com.vivescriptsolutions.roomready.data.preferences.PmsConnection
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.domain.ConnectionMode
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-facing sync facade: connection state, one-shot bootstrap/sync calls,
 * and outbox scheduling. A 401 anywhere wipes the key and raises
 * [authRevoked] so navigation can return to the Connection screen.
 */
class SyncRepository(
  private val appContext: Context,
  private val db: AppDatabase,
  private val prefs: UserPreferencesRepository,
  private val keys: ApiKeyStore,
  private val api: PmsApi,
  private val engine: SyncEngine = SyncEngine(db, api, keys, prefs),
) {
  private val _authRevoked = MutableStateFlow(false)
  val authRevoked: StateFlow<Boolean> = _authRevoked.asStateFlow()

  fun observeConnection(): Flow<PmsConnection> = prefs.observeConnection()

  suspend fun connection(): PmsConnection = prefs.connection()

  fun observeSyncState(): Flow<SyncStateEntity?> = db.syncStateDao().observe()

  fun observeOutboxCount(): Flow<Int> = db.outboxDao().observeCount()

  suspend fun setMode(mode: ConnectionMode) = prefs.setConnectionMode(mode)

  suspend fun saveConnection(baseUrl: String, rawKey: String): BootstrapOutcome {
    val url = baseUrl.trim().trimEnd('/')
    if (!url.startsWith("https://")) return BootstrapOutcome.Failed("Enter an https:// PMS origin")
    if (rawKey.isBlank()) return BootstrapOutcome.Failed("Paste the property API key")
    prefs.setBaseUrl(url)
    keys.saveKey(rawKey)
    prefs.setConnectionMode(ConnectionMode.LIVE)
    return when (val outcome = engine.bootstrap()) {
      is BootstrapOutcome.Applied -> {
        _authRevoked.value = false
        outcome
      }
      is BootstrapOutcome.Unauthorized -> {
        _authRevoked.value = true
        outcome
      }
      else -> outcome
    }
  }

  suspend fun bootstrap(): BootstrapOutcome =
    when (val outcome = engine.bootstrap()) {
      is BootstrapOutcome.Unauthorized -> {
        _authRevoked.value = true
        outcome
      }
      else -> outcome
    }

  suspend fun syncNow(): SyncOutcome {
    if (prefs.connection().mode != ConnectionMode.LIVE) return SyncOutcome.NotConfigured
    return when (val outcome = engine.syncOnce()) {
      is SyncOutcome.AuthRevoked -> {
        _authRevoked.value = true
        outcome
      }
      else -> outcome
    }
  }

  suspend fun disconnect() {
    keys.clear()
    // Demo mode IS the bundled snapshot: reseed it so no stale Live
    // data (or queued PMS writes) lingers after disconnect.
    DemoSeeder.resetAndSeed(db)
    prefs.setConnectionMode(ConnectionMode.DEMO)
    _authRevoked.value = false
  }

  fun clearAuthRevoked() {
    _authRevoked.value = false
  }

  fun enqueueSync() {
    WorkManager.getInstance(appContext)
      .enqueueUniqueWork(PMS_SYNC_WORK, ExistingWorkPolicy.APPEND, buildPmsSyncWorkRequest())
  }

  companion object {
    const val PMS_SYNC_WORK = "pms-sync"
    const val PMS_SYNC_TAG = "pms-sync"
  }
}

/** Separate for tests: the exact request [SyncRepository.enqueueSync] enqueues. */
internal fun buildPmsSyncWorkRequest(): OneTimeWorkRequest =
  OneTimeWorkRequestBuilder<PmsSyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .addTag(SyncRepository.PMS_SYNC_TAG)
    .build()
