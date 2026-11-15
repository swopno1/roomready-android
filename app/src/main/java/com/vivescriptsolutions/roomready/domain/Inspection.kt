package com.vivescriptsolutions.roomready.domain

import androidx.annotation.StringRes
import com.vivescriptsolutions.roomready.R

/**
 * Supervisor return reasons. Each chip maps to the checklist step keys it
 * fails so a returned run reopens with exactly those steps unchecked.
 * P7 mirrors Return to the PMS INSPECT_FAIL action with the formatted
 * reason as the API note.
 */
enum class ReturnReason(
  val defaultLabel: String,
  @StringRes val labelRes: Int,
) {
  BATHROOM("Bathroom", R.string.return_reason_bathroom),
  BEDS("Beds", R.string.return_reason_beds),
  DUSTING("Dusting", R.string.return_reason_dusting),
  AMENITIES("Amenities", R.string.return_reason_amenities),
  OTHER("Other", R.string.return_reason_other),
}

fun failedStepKeysForReason(reason: ReturnReason): Set<String> =
  when (reason) {
    ReturnReason.BATHROOM -> setOf("bathroom", "stayover_bathroom_quick")
    ReturnReason.BEDS -> setOf("beds_linen", "stayover_beds_made")
    ReturnReason.DUSTING -> setOf("dusting")
    ReturnReason.AMENITIES -> setOf("amenities_restock", "stayover_towels", "stayover_bins")
    ReturnReason.OTHER -> emptySet()
  }

fun formatReturnReason(chipLabel: String, note: String): String {
  val cleanNote = note.trim()
  return if (cleanNote.isEmpty()) chipLabel else "$chipLabel: $cleanNote"
}
