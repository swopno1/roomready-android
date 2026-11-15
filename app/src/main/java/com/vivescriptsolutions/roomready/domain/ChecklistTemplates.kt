package com.vivescriptsolutions.roomready.domain

import androidx.annotation.StringRes
import com.vivescriptsolutions.roomready.R

enum class ChecklistRunKind {
  FULL,
  STAYOVER,
}

enum class StayoverStepKey(
  val key: String,
  val defaultTitle: String,
  @StringRes val labelRes: Int,
) {
  BATHROOM_QUICK("stayover_bathroom_quick", "Bathroom quick", R.string.step_stayover_bathroom_quick),
  BEDS_MADE("stayover_beds_made", "Beds made", R.string.step_stayover_beds_made),
  TOWELS("stayover_towels", "Towels", R.string.step_stayover_towels),
  BINS("stayover_bins", "Bins", R.string.step_stayover_bins);

  companion object {
    fun fromKeyOrTitle(value: String): StayoverStepKey? =
      entries.firstOrNull {
        it.key.equals(value, ignoreCase = true) ||
          it.defaultTitle.equals(value, ignoreCase = true)
      }

    val templateKeys: List<String>
      get() = entries.map { it.key }
  }
}

object ChecklistTemplates {
  const val FULL_STEP_COUNT = 8
  const val STAYOVER_STEP_COUNT = 4

  fun templateKeysFor(kind: ChecklistRunKind, roomTypeSteps: List<String>): List<String> =
    when (kind) {
      ChecklistRunKind.FULL -> roomTypeSteps.ifEmpty { ChecklistStepKey.standardTemplateKeys }
      ChecklistRunKind.STAYOVER -> StayoverStepKey.templateKeys
    }

  fun buildSteps(templateKeys: List<String>): List<ChecklistStepState> {
    val keys = templateKeys.ifEmpty { ChecklistStepKey.standardTemplateKeys }
    return keys.mapIndexed { index, rawKey ->
      val full = ChecklistStepKey.fromKeyOrTitle(rawKey)
      val stayover = StayoverStepKey.fromKeyOrTitle(rawKey)
      ChecklistStepState(
        stepIndex = index,
        key = full?.key ?: stayover?.key ?: rawKey,
        title = full?.defaultTitle ?: stayover?.defaultTitle ?: rawKey,
        isDone = false,
        note = "",
      )
    }
  }

  @StringRes
  fun labelResForKey(keyOrTitle: String): Int? =
    ChecklistStepKey.fromKeyOrTitle(keyOrTitle)?.labelRes
      ?: StayoverStepKey.fromKeyOrTitle(keyOrTitle)?.labelRes

  fun doneCount(steps: List<ChecklistStepState>): Int = steps.count { it.isDone }

  /**
   * A run may finish only when every step is checked. There is no override path:
   * callers must disable Finish until this returns true and the repository
   * re-checks it inside the finish transaction.
   */
  fun canFinish(steps: List<ChecklistStepState>): Boolean =
    steps.isNotEmpty() && steps.all { it.isDone }

  fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis.coerceAtLeast(0L) / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
      "$minutes:${seconds.toString().padStart(2, '0')}"
    }
  }
}
