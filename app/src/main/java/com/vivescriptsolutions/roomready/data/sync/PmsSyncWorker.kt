package com.vivescriptsolutions.roomready.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.vivescriptsolutions.roomready.RoomReadyApplication

/**
 * Pushes the outbox in order, then pulls. Network-constrained with
 * exponential backoff; silent when offline or unconfigured.
 */
class PmsSyncWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val container = (applicationContext as RoomReadyApplication).container
    return resultForSyncOutcome(container.syncRepository.syncNow())
  }
}

/**
 * Pure outcome mapping, separated for tests. Only a completed sync with
 * work still queued retries; auth revoked needs the user, not a retry,
 * and offline/unconfigured work never runs thanks to constraints (belts
 * included).
 */
internal fun resultForSyncOutcome(outcome: SyncOutcome): ListenableWorker.Result =
  when (outcome) {
    is SyncOutcome.Completed ->
      if (outcome.needsRetry) ListenableWorker.Result.retry()
      else ListenableWorker.Result.success()
    is SyncOutcome.AuthRevoked,
    is SyncOutcome.NotConfigured,
    is SyncOutcome.Offline,
    -> ListenableWorker.Result.success()
  }
