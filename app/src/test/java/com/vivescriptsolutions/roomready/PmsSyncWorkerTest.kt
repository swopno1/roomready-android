package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.sync.InMemoryApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.PmsSyncWorker
import com.vivescriptsolutions.roomready.data.sync.SyncOutcome
import com.vivescriptsolutions.roomready.data.sync.SyncRepository
import com.vivescriptsolutions.roomready.data.sync.buildPmsSyncWorkRequest
import com.vivescriptsolutions.roomready.data.sync.resultForSyncOutcome
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P7 worker contract: the sync worker retries only when the engine
 * still has queued work, never retries auth/offline outcomes, and is
 * scheduled network-constrained with exponential backoff.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PmsSyncWorkerTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context,
      Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .setExecutor(Executors.newSingleThreadExecutor())
        .build(),
    )
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `completed without retry maps to success`() {
    assertEquals(
      ListenableWorker.Result.success(),
      resultForSyncOutcome(
        SyncOutcome.Completed(pushed = 2, dropped = 0, errors = emptyList(), needsRetry = false)
      ),
    )
  }

  @Test
  fun `completed with queued work maps to retry`() {
    assertEquals(
      ListenableWorker.Result.retry(),
      resultForSyncOutcome(
        SyncOutcome.Completed(pushed = 0, dropped = 0, errors = listOf("timeout"), needsRetry = true)
      ),
    )
  }

  @Test
  fun `auth revoked never retries`() {
    assertEquals(
      ListenableWorker.Result.success(),
      resultForSyncOutcome(SyncOutcome.AuthRevoked),
    )
  }

  @Test
  fun `offline and unconfigured never retry`() {
    assertEquals(
      ListenableWorker.Result.success(),
      resultForSyncOutcome(SyncOutcome.Offline),
    )
    assertEquals(
      ListenableWorker.Result.success(),
      resultForSyncOutcome(SyncOutcome.NotConfigured),
    )
  }

  @Test
  fun `sync work request is network-constrained with exponential backoff`() {
    val spec = buildPmsSyncWorkRequest().workSpec
    assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
    assertEquals(30_000L, spec.backoffDelayDuration)
    assertEquals(PmsSyncWorker::class.java.name, spec.workerClassName)
  }

  @Test
  fun `enqueueSync schedules network-constrained work with backoff`() {
    val sync =
      SyncRepository(
        appContext = context,
        db = db,
        prefs = UserPreferencesRepository(context),
        keys = InMemoryApiKeyStore(),
        api = PmsApi(baseUrlProvider = { null }, keyProvider = { null }),
      )
    sync.enqueueSync()

    val workInfos =
      WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(SyncRepository.PMS_SYNC_WORK)
        .get()
    assertEquals(1, workInfos.size)
    val info = workInfos.single()
    assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    assertTrue(info.tags.contains(SyncRepository.PMS_SYNC_TAG))
    assertEquals(PmsSyncWorker::class.java.name, info.workerClassName)
  }
}
