package com.vivescriptsolutions.roomready.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vivescriptsolutions.roomready.RoomReadyApplication
import java.util.concurrent.TimeUnit

/**
 * Half-hourly local sweep for cleans stuck over 60 minutes. No
 * constraints (pure on-device query) and no retry semantics: a miss
 * just waits for the next tick.
 */
class StuckReminderWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val container = (applicationContext as RoomReadyApplication).container
    container.housekeepingRepository.checkStuckCleans(System.currentTimeMillis())
    return Result.success()
  }
}

/** Separate for tests: the exact request [enqueueStuckReminders] uses. */
fun buildStuckReminderRequest(): PeriodicWorkRequest =
  PeriodicWorkRequestBuilder<StuckReminderWorker>(30, TimeUnit.MINUTES)
    .addTag(STUCK_REMINDER_TAG)
    .build()

fun enqueueStuckReminders(context: Context) {
  WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork(
      STUCK_REMINDER_WORK,
      ExistingPeriodicWorkPolicy.KEEP,
      buildStuckReminderRequest(),
    )
}

const val STUCK_REMINDER_WORK = "stuck-reminders"
const val STUCK_REMINDER_TAG = "stuck-reminders"
