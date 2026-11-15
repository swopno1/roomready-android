package com.vivescriptsolutions.roomready.data.repository

import androidx.room.withTransaction
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.RoomTypeEntity
import com.vivescriptsolutions.roomready.data.local.RunReturnFlag
import com.vivescriptsolutions.roomready.data.local.StatusCount
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.local.TicketPriorityCount
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.failedStepKeysForReason
import com.vivescriptsolutions.roomready.domain.formatReturnReason
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

  fun observeStatusCounts(): Flow<List<StatusCount>> = db.roomDao().observeStatusCounts()

  fun observeRoomsByStatus(status: RoomStatus): Flow<List<RoomEntity>> =
    db.roomDao().observeRoomsByStatus(status)

  fun observeCountByPriority(status: TicketStatus): Flow<List<TicketPriorityCount>> =
    db.ticketDao().observeCountByPriority(status)

  fun observeLogsSince(toStatus: RoomStatus, sinceMillis: Long): Flow<List<StatusLogEntity>> =
    db.statusLogDao().observeLogsSince(toStatus, sinceMillis)

  fun observeReturnedRunFlags(): Flow<List<RunReturnFlag>> =
    db.checklistRunDao().observeReturnedRunFlags()

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

  /**
   * Generic transition entry point. Every local transition is shaped as
   * (room, from, to, actorName, reason?) so P7 can mirror Pass to the PMS
   * INSPECT_PASS action and Return to INSPECT_FAIL (reason as the API note)
   * without refactoring this code.
   */
  suspend fun transitionRoomStatus(
    roomId: Long,
    toStatus: RoomStatus,
    staffName: String,
    role: StaffRole,
    reason: String? = null,
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
        if (toStatus == RoomStatus.CLEANING && room.status == RoomStatus.DIRTY) {
          // The generic transition always starts a full turnover. Stayover runs
          // go through startChecklistRun(STAYOVER), which enforces occupancy.
          startChecklistRunTx(room, ChecklistRunKind.FULL, staffName, role, nowMillis)
          return@withTransaction
        }
        if (toStatus == RoomStatus.CLEANED) {
          // Gated: the generic transition cannot bypass the finish checklist.
          finishChecklistRunTx(room, staffName, role, nowMillis)
          return@withTransaction
        }
        if (room.status == RoomStatus.CLEANED && toStatus == RoomStatus.READY) {
          inspectPassTx(room, staffName, role, nowMillis)
          return@withTransaction
        }
        if (room.status == RoomStatus.CLEANED && toStatus == RoomStatus.CLEANING) {
          // Returns must carry a reason chip (it unchecks the failed steps),
          // so the generic entry point refuses them: callers use inspectReturn.
          error("Use inspectReturn with a reason to return a cleaned room to cleaning")
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
        insertStatusLog(room, toStatus, staffName, role, nowMillis, reason)
      }
    }
  }

  /**
   * Inspection pass: CLEANED room with a finished run moves to READY.
   * Mirrors to PMS INSPECT_PASS in P7.
   */
  suspend fun inspectPass(
    roomId: Long,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    return runCatching {
      db.withTransaction {
        val room = db.roomDao().getRoomById(roomId) ?: error("Room $roomId not found")
        require(RoomTransitionEngine.isLegalTransition(room.status, RoomStatus.READY, role)) {
          "Cannot pass inspection from ${room.status} for role $role"
        }
        inspectPassTx(room, staffName, role, nowMillis)
      }
    }
  }

  /**
   * Inspection return: CLEANED room goes back to CLEANING with a required
   * reason. The run reopens (finishedAt cleared) with the failed steps
   * unchecked, and the reason travels on the run, the log, and (in P7)
   * the PMS INSPECT_FAIL API note.
   */
  suspend fun inspectReturn(
    roomId: Long,
    reason: ReturnReason?,
    note: String,
    staffName: String,
    role: StaffRole,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    return runCatching {
      db.withTransaction {
        val room = db.roomDao().getRoomById(roomId) ?: error("Room $roomId not found")
        require(RoomTransitionEngine.isLegalTransition(room.status, RoomStatus.CLEANING, role)) {
          "Cannot return room from ${room.status} for role $role"
        }
        require(reason != null) { "A return reason is required" }
        inspectReturnTx(room, reason, note, staffName, role, nowMillis)
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
   * room to CLEANED, and logs it.
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
        require(RoomTransitionEngine.isLegalTransition(room.status, RoomStatus.CLEANED, role)) {
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
      .update(room.copy(status = RoomStatus.CLEANED, assignedTo = staffName, updatedAt = nowMillis))
    insertStatusLog(room, RoomStatus.CLEANED, staffName, role, nowMillis)
  }

  private suspend fun insertStatusLog(
    room: RoomEntity,
    toStatus: RoomStatus,
    staffName: String,
    role: StaffRole,
    nowMillis: Long,
    reason: String? = null,
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
          reason = reason,
        )
      )
  }

  private suspend fun inspectPassTx(
    room: RoomEntity,
    staffName: String,
    role: StaffRole,
    nowMillis: Long,
  ) {
    val latest = db.checklistRunDao().getLatestForRoom(room.id)
    require(latest != null && latest.finishedAt != null) {
      "Only a room with a completed checklist can pass inspection"
    }
    db.roomDao()
      .update(room.copy(status = RoomStatus.READY, assignedTo = staffName, updatedAt = nowMillis))
    insertStatusLog(room, RoomStatus.READY, staffName, role, nowMillis)
  }

  private suspend fun inspectReturnTx(
    room: RoomEntity,
    reason: ReturnReason,
    note: String,
    staffName: String,
    role: StaffRole,
    nowMillis: Long,
  ) {
    val latest = db.checklistRunDao().getLatestForRoom(room.id)
    require(latest != null && latest.finishedAt != null) {
      "Only a room with a completed checklist can be returned"
    }
    val failedKeys = failedStepKeysForReason(reason)
    val reopenedSteps =
      latest.steps.map { step ->
        if (step.key in failedKeys) step.copy(isDone = false) else step
      }
    db.checklistRunDao()
      .update(
        latest.copy(
          finishedAt = null,
          steps = reopenedSteps,
          returnReasonKey = reason.name,
          returnNote = note.trim(),
        )
      )
    db.roomDao()
      .update(room.copy(status = RoomStatus.CLEANING, assignedTo = staffName, updatedAt = nowMillis))
    insertStatusLog(
      room, RoomStatus.CLEANING, staffName, role, nowMillis,
      reason = formatReturnReason(reason.defaultLabel, note),
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

  fun observeTicket(ticketId: Long): Flow<TicketEntity?> =
    db.ticketDao().observeTicketById(ticketId)

  suspend fun createTicket(
    roomId: Long,
    roomName: String,
    title: String,
    description: String,
    priority: TicketPriority,
    createdBy: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Long? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null
    return db.ticketDao()
      .insert(
        TicketEntity(
          roomId = roomId,
          roomName = roomName,
          title = cleanTitle,
          description = description.trim(),
          priority = priority,
          status = TicketStatus.OPEN,
          createdBy = createdBy,
          createdAt = nowMillis,
          isDemo = false,
        )
      )
  }

  suspend fun resolveTicket(
    ticketId: Long,
    resolvedBy: String,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    val ticket = db.ticketDao().getTicketById(ticketId) ?: return
    if (ticket.status == TicketStatus.DONE) return
    db.ticketDao()
      .update(ticket.copy(status = TicketStatus.DONE, resolvedBy = resolvedBy, resolvedAt = nowMillis))
  }

  suspend fun reopenTicket(ticketId: Long) {
    val ticket = db.ticketDao().getTicketById(ticketId) ?: return
    if (ticket.status == TicketStatus.OPEN) return
    db.ticketDao().update(ticket.copy(status = TicketStatus.OPEN, resolvedBy = null, resolvedAt = null))
  }

  suspend fun toggleTicketStatus(ticket: TicketEntity, staffName: String) {
    if (ticket.status == TicketStatus.OPEN) {
      resolveTicket(ticket.id, resolvedBy = staffName)
    } else {
      reopenTicket(ticket.id)
    }
  }
}
