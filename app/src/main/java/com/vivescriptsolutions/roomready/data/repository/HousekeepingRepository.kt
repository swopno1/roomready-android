package com.vivescriptsolutions.roomready.data.repository

import androidx.room.withTransaction
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.RoomTypeEntity
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import kotlinx.coroutines.flow.Flow

class HousekeepingRepository(
  private val db: AppDatabase,
  private val prefs: UserPreferencesRepository,
) {
  val sessionFlow: Flow<UserSession> = prefs.sessionFlow

  fun observeAllRooms(): Flow<List<RoomEntity>> = db.roomDao().observeAllRooms()

  fun observeRoom(roomId: Long): Flow<RoomEntity?> = db.roomDao().observeRoomById(roomId)

  fun observeRoomTypes(): Flow<List<RoomTypeEntity>> = db.roomTypeDao().observeAll()

  fun observeLatestChecklistRun(roomId: Long): Flow<ChecklistRunEntity?> =
    db.checklistRunDao().observeLatestForRoom(roomId)

  fun observeTicketsForRoom(roomId: Long): Flow<List<TicketEntity>> =
    db.ticketDao().observeTicketsForRoom(roomId)

  fun observeAllTickets(): Flow<List<TicketEntity>> = db.ticketDao().observeAllTickets()

  fun observeLogsForRoom(roomId: Long): Flow<List<StatusLogEntity>> =
    db.statusLogDao().observeLogsForRoom(roomId)

  fun observeRecentLogs(limit: Int = 25): Flow<List<StatusLogEntity>> =
    db.statusLogDao().observeRecentLogs(limit)

  suspend fun ensureDemoDataSeeded() {
    val seededNow = DemoSeeder.seedIfEmpty(db)
    if (seededNow) {
      prefs.markDemoSeeded()
    }
  }

  suspend fun resetDemoData() {
    DemoSeeder.resetAndSeed(db)
    prefs.markDemoSeeded()
  }

  suspend fun saveRoleAndStaff(role: StaffRole, staffName: String) {
    prefs.saveRoleAndStaff(role, staffName)
  }

  suspend fun switchRole(role: StaffRole) {
    prefs.setRole(role)
  }

  suspend fun switchStaffName(staffName: String) {
    prefs.setStaffName(staffName)
  }

  suspend fun transitionRoomStatus(
    roomId: Long,
    toStatus: RoomStatus,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    return runCatching {
      db.withTransaction {
        val room =
          db.roomDao().getRoomById(roomId)
            ?: error("Room $roomId not found")
        require(RoomTransitionEngine.isLegalTransition(room.status, toStatus, role)) {
          "Illegal status transition from ${room.status} to $toStatus for role $role"
        }

        // Run lifecycle transitions delegate to the gated Tx bodies (which
        // update the room and write the log); all other targets use the
        // generic update + log below.
        if (toStatus == RoomStatus.CLEANING) {
          // The generic transition always starts a full turnover. Stayover runs
          // go through startChecklistRun(STAYOVER), which enforces occupancy.
          startChecklistRunTx(room, ChecklistRunKind.FULL, staffName, role, nowMillis)
          return@withTransaction
        }
        if (toStatus == RoomStatus.INSPECTED) {
          // Gated: the generic transition cannot bypass the finish checklist.
          finishChecklistRunTx(room, staffName, role, nowMillis)
          return@withTransaction
        }

        val updatedAssignedTo = if (toStatus == RoomStatus.DIRTY) null else staffName
        db.roomDao()
          .update(
            room.copy(
              status = toStatus,
              assignedTo = updatedAssignedTo,
              updatedAt = nowMillis,
            )
          )
        insertStatusLog(room, toStatus, staffName, role, nowMillis)
      }
    }
  }

  /**
   * Starts a cleaning run: creates the ChecklistRun from the right template
   * (room type's 8-step template for FULL, the fixed 4-step light template
   * for STAYOVER), moves the room DIRTY -> CLEANING, and logs it.
   * Stayover is allowed only on occupied rooms. Returns the run id; an
   * unfinished run is resumed (reused) instead of duplicated.
   */
  suspend fun startChecklistRun(
    roomId: Long,
    kind: ChecklistRunKind,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Long> {
    return runCatching {
      db.withTransaction {
        val room = db.roomDao().getRoomById(roomId) ?: error("Room $roomId not found")
        require(RoomTransitionEngine.isLegalTransition(room.status, RoomStatus.CLEANING, role)) {
          "Cannot start cleaning from ${room.status} for role $role"
        }
        startChecklistRunTx(room, kind, staffName, role, nowMillis)
      }
    }
  }

  /**
   * Finishes a cleaning run: requires CLEANING with an unfinished run whose
   * every step is checked (no override), then stamps finishedAt, moves the
   * room to INSPECTED, and logs it.
   */
  suspend fun finishChecklistRun(
    roomId: Long,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    return runCatching {
      db.withTransaction {
        val room = db.roomDao().getRoomById(roomId) ?: error("Room $roomId not found")
        require(RoomTransitionEngine.isLegalTransition(room.status, RoomStatus.INSPECTED, role)) {
          "Cannot finish cleaning from ${room.status} for role $role"
        }
        finishChecklistRunTx(room, staffName, role, nowMillis)
      }
    }
  }

  private suspend fun startChecklistRunTx(
    room: RoomEntity,
    kind: ChecklistRunKind,
    staffName: String,
    role: StaffRole,
    nowMillis: Long,
  ): Long {
    require(kind == ChecklistRunKind.FULL || room.isOccupied) {
      "Stayover refresh is allowed only on occupied rooms"
    }
    val unfinished = db.checklistRunDao().getLatestForRoom(room.id)
    val runId =
      if (unfinished != null && unfinished.finishedAt == null) {
        unfinished.id
      } else {
        val roomType = db.roomTypeDao().getById(room.roomTypeId)
        val templateKeys =
          ChecklistTemplates.templateKeysFor(kind, roomType?.checklistTemplateSteps.orEmpty())
        db.checklistRunDao()
          .insert(
            ChecklistRunEntity(
              roomId = room.id,
              performedBy = staffName,
              performedByRole = role,
              startedAt = nowMillis,
              finishedAt = null,
              steps = ChecklistTemplates.buildSteps(templateKeys),
              runKind = kind,
            )
          )
      }
    db.roomDao()
      .update(room.copy(status = RoomStatus.CLEANING, assignedTo = staffName, updatedAt = nowMillis))
    insertStatusLog(room, RoomStatus.CLEANING, staffName, role, nowMillis)
    return runId
  }

  private suspend fun finishChecklistRunTx(
    room: RoomEntity,
    staffName: String,
    role: StaffRole,
    nowMillis: Long,
  ) {
    val latest = db.checklistRunDao().getLatestForRoom(room.id)
    require(latest != null && latest.finishedAt == null) {
      "No active checklist run for room ${room.name}"
    }
    val doneCount = ChecklistTemplates.doneCount(latest.steps)
    require(ChecklistTemplates.canFinish(latest.steps)) {
      "Checklist incomplete: $doneCount of ${latest.steps.size} steps done"
    }
    db.checklistRunDao().update(latest.copy(finishedAt = nowMillis))
    db.roomDao()
      .update(room.copy(status = RoomStatus.INSPECTED, assignedTo = staffName, updatedAt = nowMillis))
    insertStatusLog(room, RoomStatus.INSPECTED, staffName, role, nowMillis)
  }

  private suspend fun insertStatusLog(
    room: RoomEntity,
    toStatus: RoomStatus,
    staffName: String,
    role: StaffRole,
    nowMillis: Long,
  ) {
    db.statusLogDao()
      .insert(
        StatusLogEntity(
          roomId = room.id,
          roomName = room.name,
          fromStatus = room.status,
          toStatus = toStatus,
          changedBy = staffName,
          changedByRole = role,
          changedAt = nowMillis,
        )
      )
  }

  suspend fun toggleChecklistStep(
    roomId: Long,
    stepIndex: Int,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    db.withTransaction {
      val existing = db.checklistRunDao().getLatestForRoom(roomId)
      if (existing != null) {
        val updatedSteps =
          existing.steps.map { step ->
            if (step.stepIndex == stepIndex) step.copy(isDone = !step.isDone) else step
          }
        db.checklistRunDao().update(existing.copy(steps = updatedSteps))
      } else {
        val room = db.roomDao().getRoomById(roomId) ?: return@withTransaction
        val roomType = db.roomTypeDao().getById(room.roomTypeId)
        val initialSteps =
          DemoSeeder.buildTemplateSteps(roomType?.checklistTemplateSteps.orEmpty()).map { step ->
            if (step.stepIndex == stepIndex) step.copy(isDone = true) else step
          }
        db.checklistRunDao()
          .insert(
            ChecklistRunEntity(
              roomId = roomId,
              performedBy = staffName,
              performedByRole = role,
              startedAt = nowMillis,
              finishedAt = null,
              steps = initialSteps,
            )
          )
      }
    }
  }

  suspend fun updateChecklistStepNote(
    roomId: Long,
    stepIndex: Int,
    note: String,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    db.withTransaction {
      val existing = db.checklistRunDao().getLatestForRoom(roomId)
      if (existing != null) {
        val updatedSteps =
          existing.steps.map { step ->
            if (step.stepIndex == stepIndex) step.copy(note = note.trim()) else step
          }
        db.checklistRunDao().update(existing.copy(steps = updatedSteps))
      } else {
        val room = db.roomDao().getRoomById(roomId) ?: return@withTransaction
        val roomType = db.roomTypeDao().getById(room.roomTypeId)
        val initialSteps =
          DemoSeeder.buildTemplateSteps(roomType?.checklistTemplateSteps.orEmpty()).map { step ->
            if (step.stepIndex == stepIndex) step.copy(note = note.trim()) else step
          }
        db.checklistRunDao()
          .insert(
            ChecklistRunEntity(
              roomId = roomId,
              performedBy = staffName,
              performedByRole = role,
              startedAt = nowMillis,
              finishedAt = null,
              steps = initialSteps,
            )
          )
      }
    }
  }

  suspend fun createTicket(
    roomId: Long,
    roomName: String,
    title: String,
    priority: TicketPriority,
    createdBy: String,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return
    db.ticketDao()
      .insert(
        TicketEntity(
          roomId = roomId,
          roomName = roomName,
          title = cleanTitle,
          priority = priority,
          status = TicketStatus.OPEN,
          createdBy = createdBy,
          createdAt = nowMillis,
          isDemo = false,
        )
      )
  }

  suspend fun toggleTicketStatus(ticket: TicketEntity) {
    val nextStatus =
      if (ticket.status == TicketStatus.OPEN) TicketStatus.DONE else TicketStatus.OPEN
    db.ticketDao().update(ticket.copy(status = nextStatus))
  }
}
