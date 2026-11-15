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
import com.vivescriptsolutions.roomready.data.local.OutboxEntry
import com.vivescriptsolutions.roomready.data.local.OutboxKind
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.local.TicketPriorityCount
import com.vivescriptsolutions.roomready.data.preferences.PmsConnection
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.ConnectionMode
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.failedStepKeysForReason
import com.vivescriptsolutions.roomready.domain.formatReturnReason
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONObject

class HousekeepingRepository(
  private val db: AppDatabase,
  private val prefs: UserPreferencesRepository,
  private val onOutboxAppended: () -> Unit = {},
) {
  /**
   * Session with the staff roster overridden by the synced PMS roster in
   * Live mode (Demo mode keeps the local list). Also reports Live mode as
   * non-demo so demo-only UI gives way to synced content.
   */
  val sessionFlow: Flow<UserSession> =
    combine(
      prefs.sessionFlow,
      db.staffDao().observeAll(),
      prefs.observeConnection(),
    ) { session, staff, connection ->
      if (connection.mode == ConnectionMode.LIVE) {
        session.copy(
          staffRoster = staff.map { it.name }.distinct().sorted(),
          isDemoMode = false,
        )
      } else {
        session
      }
    }

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

  fun observeConnection(): Flow<PmsConnection> = prefs.observeConnection()

  fun observeOutboxCount(): Flow<Int> = db.outboxDao().observeCount()

  /**
   * Start-of-"today" for dashboard cleans. In Live mode with a synced
   * business date this is midnight of that date in the property timezone;
   * otherwise the device-local day start.
   */
  suspend fun todayStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    if (prefs.connection().mode == ConnectionMode.LIVE) {
      val sync = db.syncStateDao().get()
      val businessDate = sync?.businessDate
      val timezone = sync?.propertyTimezone
      if (businessDate != null && timezone != null) {
        val parsed =
          runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
              .apply { timeZone = TimeZone.getTimeZone(timezone) }
              .parse(businessDate)?.time
          }.getOrNull()
        if (parsed != null) return parsed
      }
    }
    return startOfDayMillis(nowMillis)
  }

  suspend fun ensureDemoDataSeeded() {
    if (prefs.connection().mode != ConnectionMode.DEMO) return
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
      // OUT_OF_SERVICE is PMS-driven and read-only in Live mode (the VM
      // hides the buttons too; this guard is the enforcement point).
      if (prefs.connection().mode == ConnectionMode.LIVE &&
        (toStatus == RoomStatus.OUT_OF_SERVICE)
      ) {
        error("Out-of-service is managed by the PMS in Live mode")
      }
      db.withTransaction {
        val room =
          db.roomDao().getRoomById(roomId)
            ?: error("Room $roomId not found")
        require(RoomTransitionEngine.isLegalTransition(room.status, toStatus, role)) {
          "Illegal status transition from ${room.status} to $toStatus for role $role"
        }
        if (prefs.connection().mode == ConnectionMode.LIVE &&
          room.status == RoomStatus.OUT_OF_SERVICE
        ) {
          error("Out-of-service is managed by the PMS in Live mode")
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
        // Only settled→DIRTY marks mirror (OUT_OF_SERVICE is PMS-driven).
        if (toStatus == RoomStatus.DIRTY && room.status != RoomStatus.OUT_OF_SERVICE) {
          val expected =
            when (room.status) {
              RoomStatus.READY -> "INSPECTED"
              RoomStatus.CLEANED -> "CLEAN"
              else -> null
            }
          if (expected != null) {
            enqueueStatusIfLive(room, action = "MARK_DIRTY", expectedPms = expected, staffName, note = null, nowMillis)
          }
        }
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
    require(!latest.photoPath.isNullOrBlank() || !latest.photoSkippedReason.isNullOrBlank()) {
      "An after photo or a skip reason is required to finish"
    }
    db.checklistRunDao().update(latest.copy(finishedAt = nowMillis))
    db.roomDao()
      .update(room.copy(status = RoomStatus.CLEANED, assignedTo = staffName, updatedAt = nowMillis))
    insertStatusLog(room, RoomStatus.CLEANED, staffName, role, nowMillis)
    enqueueStatusIfLive(room, action = "MARK_CLEAN", expectedPms = "DIRTY", staffName, note = null, nowMillis)
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
    enqueueStatusIfLive(room, action = "INSPECT_PASS", expectedPms = "CLEAN", staffName, note = null, nowMillis)
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
    val displayReason = formatReturnReason(reason.defaultLabel, note)
    insertStatusLog(
      room, RoomStatus.CLEANING, staffName, role, nowMillis,
      reason = displayReason,
    )
    enqueueStatusIfLive(room, action = "INSPECT_FAIL", expectedPms = "CLEAN", staffName, note = displayReason, nowMillis)
  }

  /**
   * Mirrors a local status transition to the PMS outbox. No-op unless Live
   * mode with a PMS-mapped room. Called inside the mutation transaction so
   * the mirror can never be lost.
   */
  private suspend fun enqueueStatusIfLive(
    room: RoomEntity,
    action: String,
    expectedPms: String,
    staffName: String,
    note: String?,
    nowMillis: Long,
  ) {
    if (prefs.connection().mode != ConnectionMode.LIVE) return
    val unitPmsId = room.pmsUnitId ?: return
    val payload = JSONObject().put("expected", expectedPms).put("action", action).put("staffName", staffName)
    if (!note.isNullOrBlank()) payload.put("note", note)
    db.outboxDao().insert(
      OutboxEntry(
        createdAt = nowMillis,
        kind = OutboxKind.STATUS,
        unitPmsId = unitPmsId,
        ticketLocalId = null,
        payloadJson = payload.toString(),
      )
    )
    onOutboxAppended()
  }

  /**
   * Records the after-photo filename on the latest run. The file itself is
   * written by the capture screen into [PhotoStore]; this only stores the ref.
   */
  suspend fun setRunPhoto(roomId: Long, photoName: String) {
    val latest = db.checklistRunDao().getLatestForRoom(roomId) ?: return
    db.checklistRunDao().update(latest.copy(photoPath = photoName, photoSkippedReason = null))
  }

  /**
   * Records a skip reason instead of a photo. Blank reasons are rejected —
   * the escape hatch always carries a one-line explanation.
   */
  suspend fun skipRunPhoto(roomId: Long, reason: String): Result<Unit> {
    return runCatching {
      require(reason.isNotBlank()) { "A one-line reason is required to skip the photo" }
      val latest = db.checklistRunDao().getLatestForRoom(roomId)
        ?: error("No checklist run for room $roomId")
      db.checklistRunDao().update(latest.copy(photoPath = null, photoSkippedReason = reason.trim()))
    }
  }

  suspend fun setTicketPhoto(ticketId: Long, photoName: String) {
    val ticket = db.ticketDao().getTicketById(ticketId) ?: return
    db.ticketDao().update(ticket.copy(photoPath = photoName))
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
    return db.withTransaction {
      val id =
        db.ticketDao()
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
      if (prefs.connection().mode == ConnectionMode.LIVE) {
        db.roomDao().getRoomById(roomId)?.pmsUnitId?.let { unitPmsId ->
          val payload =
            JSONObject()
              .put("title", cleanTitle)
              .put("description", description.trim())
              .put("priority", priority.name)
              .put("staffName", createdBy)
          db.outboxDao().insert(
            OutboxEntry(
              createdAt = nowMillis,
              kind = OutboxKind.TICKET_CREATE,
              unitPmsId = unitPmsId,
              ticketLocalId = id,
              payloadJson = payload.toString(),
            )
          )
          onOutboxAppended()
        }
      }
      id
    }
  }

  suspend fun resolveTicket(
    ticketId: Long,
    resolvedBy: String,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    val ticket = db.ticketDao().getTicketById(ticketId) ?: return
    if (ticket.status == TicketStatus.DONE) return
    db.withTransaction {
      db.ticketDao()
        .update(ticket.copy(status = TicketStatus.DONE, resolvedBy = resolvedBy, resolvedAt = nowMillis))
      enqueueTicketPatchIfLive(ticketId, TicketStatus.DONE, resolvedBy, nowMillis)
    }
  }

  suspend fun reopenTicket(ticketId: Long) {
    val ticket = db.ticketDao().getTicketById(ticketId) ?: return
    if (ticket.status == TicketStatus.OPEN) return
    // Attribution comes from the caller session where available; the worker
    // falls back to the stored creator.
    db.withTransaction {
      db.ticketDao().update(ticket.copy(status = TicketStatus.OPEN, resolvedBy = null, resolvedAt = null))
      enqueueTicketPatchIfLive(
        ticketId, TicketStatus.OPEN, ticket.createdBy, System.currentTimeMillis(),
      )
    }
  }

  private suspend fun enqueueTicketPatchIfLive(
    ticketId: Long,
    status: TicketStatus,
    staffName: String,
    nowMillis: Long,
  ) {
    if (prefs.connection().mode != ConnectionMode.LIVE) return
    val payload = JSONObject().put("status", status.name).put("staffName", staffName)
    db.outboxDao().insert(
      OutboxEntry(
        createdAt = nowMillis,
        kind = OutboxKind.TICKET_PATCH,
        unitPmsId = null,
        ticketLocalId = ticketId,
        payloadJson = payload.toString(),
      )
    )
    onOutboxAppended()
  }

  suspend fun toggleTicketStatus(ticket: TicketEntity, staffName: String) {
    if (ticket.status == TicketStatus.OPEN) {
      resolveTicket(ticket.id, resolvedBy = staffName)
    } else {
      reopenTicket(ticket.id)
    }
  }
}
