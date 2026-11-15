package com.vivescriptsolutions.roomready.domain

import androidx.annotation.StringRes
import com.vivescriptsolutions.roomready.R

enum class RoomStatus(@StringRes val labelRes: Int) {
  DIRTY(R.string.status_dirty),
  CLEANING(R.string.status_cleaning),
  CLEANED(R.string.status_cleaned),
  READY(R.string.status_ready),
  OUT_OF_SERVICE(R.string.status_out_of_service),
}

enum class StaffRole(
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
) {
  HOUSEKEEPER(R.string.role_housekeeper, R.string.role_housekeeper_desc),
  SUPERVISOR(R.string.role_supervisor, R.string.role_supervisor_desc),
  // Attribution for PMS sync merge decisions. In no transition's allowed-role
  // set, so it can never act; never offered in any role picker.
  SYSTEM(R.string.role_system, R.string.role_system_desc),
}

enum class TicketPriority(@StringRes val labelRes: Int) {
  LOW(R.string.ticket_priority_low),
  NORMAL(R.string.ticket_priority_normal),
  URGENT(R.string.ticket_priority_urgent),
}

enum class TicketStatus(@StringRes val labelRes: Int) {
  OPEN(R.string.ticket_status_open),
  DONE(R.string.ticket_status_done),
}

enum class ChecklistStepKey(
  val key: String,
  val defaultTitle: String,
  @StringRes val labelRes: Int,
) {
  BATHROOM("bathroom", "Bathroom", R.string.step_bathroom),
  BEDS_LINEN("beds_linen", "Beds & linen", R.string.step_beds_linen),
  DUSTING("dusting", "Dusting", R.string.step_dusting),
  FLOORS("floors", "Floors", R.string.step_floors),
  AMENITIES_RESTOCK("amenities_restock", "Amenities restock", R.string.step_amenities_restock),
  FRIDGE_MINIBAR("fridge_minibar", "Fridge/minibar", R.string.step_fridge_minibar),
  WINDOWS_CURTAINS("windows_curtains", "Windows & curtains", R.string.step_windows_curtains),
  FINAL_LOOK("final_look", "Final look", R.string.step_final_look);

  companion object {
    fun fromKeyOrTitle(value: String): ChecklistStepKey? =
      entries.firstOrNull {
        it.key.equals(value, ignoreCase = true) ||
          it.defaultTitle.equals(value, ignoreCase = true)
      }

    val standardTemplateKeys: List<String>
      get() = entries.map { it.key }
  }
}

data class ChecklistStepState(
  val stepIndex: Int,
  val key: String,
  val title: String,
  val isDone: Boolean,
  val note: String = "",
)

data class RoomTransitionAction(
  val fromStatus: RoomStatus,
  val toStatus: RoomStatus,
  @StringRes val labelRes: Int,
  val isPrimary: Boolean,
  val allowedRoles: Set<StaffRole>,
)

object RoomTransitionEngine {
  // Soft gating: this is a trust-based staff tool, so every legal status
  // transition is allowed for both roles. The UI leads each role to its
  // own queue instead (supervisors to inspection, housekeepers to their
  // flagged returns), and every transition logs the acting name.
  private val allRoles = setOf(StaffRole.HOUSEKEEPER, StaffRole.SUPERVISOR)

  val transitionTable: List<RoomTransitionAction> =
    listOf(
      RoomTransitionAction(
        fromStatus = RoomStatus.DIRTY,
        toStatus = RoomStatus.CLEANING,
        labelRes = R.string.action_start_cleaning,
        isPrimary = true,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.DIRTY,
        toStatus = RoomStatus.OUT_OF_SERVICE,
        labelRes = R.string.action_mark_out_of_service,
        isPrimary = false,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.CLEANING,
        toStatus = RoomStatus.CLEANED,
        labelRes = R.string.action_finish_to_inspection,
        isPrimary = true,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.CLEANING,
        toStatus = RoomStatus.OUT_OF_SERVICE,
        labelRes = R.string.action_mark_out_of_service,
        isPrimary = false,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.CLEANED,
        toStatus = RoomStatus.READY,
        labelRes = R.string.action_mark_ready,
        isPrimary = true,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.CLEANED,
        toStatus = RoomStatus.CLEANING,
        labelRes = R.string.action_return_to_cleaning,
        isPrimary = false,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.CLEANED,
        toStatus = RoomStatus.DIRTY,
        labelRes = R.string.action_return_to_dirty,
        isPrimary = false,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.CLEANED,
        toStatus = RoomStatus.OUT_OF_SERVICE,
        labelRes = R.string.action_mark_out_of_service,
        isPrimary = false,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.READY,
        toStatus = RoomStatus.DIRTY,
        labelRes = R.string.action_mark_dirty_turnover,
        isPrimary = true,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.READY,
        toStatus = RoomStatus.OUT_OF_SERVICE,
        labelRes = R.string.action_mark_out_of_service,
        isPrimary = false,
        allowedRoles = allRoles,
      ),
      RoomTransitionAction(
        fromStatus = RoomStatus.OUT_OF_SERVICE,
        toStatus = RoomStatus.DIRTY,
        labelRes = R.string.action_back_in_service,
        isPrimary = true,
        allowedRoles = allRoles,
      ),
    )

  fun legalActionsFor(status: RoomStatus, role: StaffRole): List<RoomTransitionAction> =
    transitionTable.filter { it.fromStatus == status && role in it.allowedRoles }

  fun isLegalTransition(from: RoomStatus, to: RoomStatus, role: StaffRole): Boolean =
    transitionTable.any {
      it.fromStatus == from && it.toStatus == to && role in it.allowedRoles
    }
}
