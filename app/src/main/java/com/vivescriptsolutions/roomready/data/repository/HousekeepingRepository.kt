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
        val fromStatus = room.status
        require(RoomTransitionEngine.isLegalTransition(fromStatus, toStatus, role)) {
          "Illegal status transition from $fromStatus to $toStatus for role $role"
        }

        val updatedAssignedTo =
          when (toStatus) {
            RoomStatus.DIRTY -> null
            RoomStatus.CLEANING,
            RoomStatus.INSPECTED,
            RoomStatus.READY,
            RoomStatus.OUT_OF_SERVICE -> staffName
          }

        db.roomDao()
          .update(
            room.copy(
              status = toStatus,
              assignedTo = updatedAssignedTo,
              updatedAt = nowMillis,
            )
          )

        when (toStatus) {
          RoomStatus.CLEANING -> {
            val roomType = db.roomTypeDao().getById(room.roomTypeId)
            val templateKeys = roomType?.checklistTemplateSteps.orEmpty()
            db.checklistRunDao()
              .insert(
                ChecklistRunEntity(
                  roomId = roomId,
                  performedBy = staffName,
                  performedByRole = role,
                  startedAt = nowMillis,
                  finishedAt = null,
                  steps = DemoSeeder.buildTemplateSteps(templateKeys),
                )
              )
          }
          RoomStatus.INSPECTED -> {
            val latest = db.checklistRunDao().getLatestForRoom(roomId)
            if (latest != null && latest.finishedAt == null) {
              db.checklistRunDao().update(latest.copy(finishedAt = nowMillis))
            }
          }
          else -> Unit
        }

        db.statusLogDao()
          .insert(
            StatusLogEntity(
              roomId = roomId,
              roomName = room.name,
              fromStatus = fromStatus,
              toStatus = toStatus,
              changedBy = staffName,
              changedByRole = role,
              changedAt = nowMillis,
            )
          )
      }
    }
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
