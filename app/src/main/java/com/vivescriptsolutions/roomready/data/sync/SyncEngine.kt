package com.vivescriptsolutions.roomready.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.OutboxEntry
import com.vivescriptsolutions.roomready.data.local.OutboxKind
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.RoomTypeEntity
import com.vivescriptsolutions.roomready.data.local.StaffMemberEntity
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.local.SyncStateEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import org.json.JSONObject

sealed interface BootstrapOutcome {
  data object Applied : BootstrapOutcome

  data class UpdateRequired(val foundVersion: Int?) : BootstrapOutcome

  data class Failed(val reason: String) : BootstrapOutcome

  data object Unauthorized : BootstrapOutcome
}

sealed interface SyncOutcome {
  data object NotConfigured : SyncOutcome

  data object Offline : SyncOutcome

  data object AuthRevoked : SyncOutcome

  data class Completed(
    val pushed: Int,
    val dropped: Int,
    val errors: List<String>,
    val needsRetry: Boolean,
  ) : SyncOutcome
}

/** Fixed app ⟷ PMS state glossary (quoted exactly from the P7 contract). */
/**
 * `floor` predates sync and stays non-null, so PMS null floors map here.
 * Never 0: that already means the ground wing.
 */
const val UNKNOWN_FLOOR = -1

fun mapUnitToStatus(unit: PmsUnit): RoomStatus =
  if (unit.unitStatus == "OUT_OF_ORDER") {
    RoomStatus.OUT_OF_SERVICE
  } else {
    when (unit.housekeeping) {
      "DIRTY" -> RoomStatus.DIRTY
      "CLEAN" -> RoomStatus.CLEANED
      "INSPECTED" -> RoomStatus.READY
      else -> error("Unmapped PMS housekeeping: ${unit.housekeeping}")
    }
  }

fun mapLocalToPms(status: RoomStatus): String? =
  when (status) {
    RoomStatus.DIRTY -> "DIRTY"
    RoomStatus.CLEANED -> "CLEAN"
    RoomStatus.READY -> "INSPECTED"
    RoomStatus.CLEANING -> "DIRTY" // app-local only; PMS still DIRTY
    RoomStatus.OUT_OF_SERVICE -> null // PMS-driven, read-only in the app
  }

class SyncEngine(
  private val db: AppDatabase,
  private val api: PmsApi,
  private val keys: ApiKeyStore,
  private val prefs: UserPreferencesRepository,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  suspend fun bootstrap(): BootstrapOutcome {
    if (!keys.hasKey()) return BootstrapOutcome.Failed("Connect a property first")
    return when (val result = api.bootstrap()) {
      is ApiOutcome.Ok -> applyBootstrap(result.value)
      is ApiOutcome.Http ->
        if (result.code == 401) {
          keys.clear()
          BootstrapOutcome.Unauthorized
        } else {
          BootstrapOutcome.Failed("Server error ${result.code}: ${result.error ?: "no detail"}")
        }
      is ApiOutcome.Network -> BootstrapOutcome.Failed("Network error: ${result.exception.message}")
      is ApiOutcome.Malformed -> BootstrapOutcome.Failed(result.message)
      is ApiOutcome.NotConfigured -> BootstrapOutcome.Failed("Connect a property first")
    }
  }

  private suspend fun applyBootstrap(payload: BootstrapPayload): BootstrapOutcome {
    if (payload.contractVersion != PMS_CONTRACT_VERSION) {
      return BootstrapOutcome.UpdateRequired(payload.contractVersion)
    }
    return try {
      db.withTransaction {
        // In-progress cleans on PMS-mapped rooms are never touched by a
        // snapshot. Rows without a PMS id are always replaced wholesale —
        // a snapshot is the complete unit list.
        val unfinishedIds = db.checklistRunDao().unfinishedRoomIds().toSet()
        val inProgress =
          db.roomDao().getAllRooms()
            .filter { it.id in unfinishedIds && it.pmsUnitId != null }
            .map { it.id }
            .toSet()
        // Types first (rooms FK-reference them).
        payload.roomTypes.forEach { type ->
          val existing = db.roomTypeDao().getByPmsTypeId(type.id)
          if (existing == null) {
            db.roomTypeDao().insertAll(
              listOf(
                RoomTypeEntity(
                  name = type.title,
                  checklistTemplateSteps = type.checklistSteps,
                  pmsTypeId = type.id,
                )
              )
            )
          } else {
            db.roomTypeDao().update(
              existing.copy(name = type.title, checklistTemplateSteps = type.checklistSteps)
            )
          }
        }
        val typeLocalIds = mutableMapOf<String, Long>()
        payload.roomTypes.forEach { type ->
          typeLocalIds[type.id] = db.roomTypeDao().getByPmsTypeId(type.id)!!.id
        }
        // Units: match-update, insert, or delete — except in-progress rooms,
        // which a snapshot never touches.
        val seenUnits = mutableSetOf<String>()
        payload.units.forEach { unit ->
          seenUnits += unit.id
          val typeId =
            typeLocalIds[unit.roomTypeId]
              ?: throw MalformedPayloadException("Unit references unknown room type")
          val existing = db.roomDao().getRoomByPmsUnitId(unit.id)
          if (existing != null && existing.id in inProgress) return@forEach
          val mapped = mapUnitToStatus(unit)
          if (existing == null) {
            db.roomDao().insertAll(
              listOf(
                RoomEntity(
                  name = unit.unitNumber,
                  roomTypeId = typeId,
                  roomTypeName = payload.roomTypes.first { it.id == unit.roomTypeId }.title,
                  floor = unit.floor ?: UNKNOWN_FLOOR,
                  status = mapped,
                  assignedTo = null,
                  updatedAt = unit.housekeepingAtMillis ?: clock(),
                  pmsUnitId = unit.id,
                  block = unit.block,
                  departsToday = unit.departsToday,
                  pmsUnitStatus = unit.unitStatus,
                  pmsHousekeepingAt = unit.housekeepingAtMillis,
                )
              )
            )
          } else {
            db.roomDao().update(
              existing.copy(
                name = unit.unitNumber,
                roomTypeId = typeId,
                roomTypeName = payload.roomTypes.first { it.id == unit.roomTypeId }.title,
                floor = unit.floor ?: UNKNOWN_FLOOR,
                status = mapped,
                block = unit.block,
                departsToday = unit.departsToday,
                pmsUnitStatus = unit.unitStatus,
                pmsHousekeepingAt = unit.housekeepingAtMillis,
                updatedAt = unit.housekeepingAtMillis ?: existing.updatedAt,
              )
            )
          }
        }
        db.roomDao().getPmsRooms()
          .filter { it.pmsUnitId !in seenUnits && it.id !in inProgress }
          .forEach { db.roomDao().deleteById(it.id) }
        db.roomDao().getAllRooms()
          .filter { it.pmsUnitId == null && it.id !in inProgress }
          .forEach { db.roomDao().deleteById(it.id) }
        db.roomTypeDao().deletePmsTypesNotIn(payload.roomTypes.map { it.id })
        db.roomTypeDao().deleteUnreferencedTypes()
        // Roster wholesale (no FK references staff).
        db.staffDao().deleteAll()
        db.staffDao().insertAll(
          payload.staff.map { StaffMemberEntity(pmsId = it.id, name = it.name, role = it.role) }
        )
        // Runs and outbox are deliberately untouched.
        db.syncStateDao().upsert(
          (db.syncStateDao().get() ?: SyncStateEntity()).copy(
            propertyName = payload.propertyName,
            propertyTimezone = payload.propertyTimezone,
            businessDate = payload.businessDate,
            contractVersion = payload.contractVersion,
            lastSyncAt = clock(),
            lastError = null,
          )
        )
      }
      BootstrapOutcome.Applied
    } catch (e: MalformedPayloadException) {
      BootstrapOutcome.Failed(e.message ?: "Malformed snapshot")
    }
  }

  suspend fun syncOnce(): SyncOutcome {
    val connection = prefs.connection()
    if (!connection.isConfigured || !keys.hasKey() || connection.baseUrl == null) {
      return SyncOutcome.NotConfigured
    }
    var pushed = 0
    var dropped = 0
    val errors = mutableListOf<String>()
    var rebootstrap = false
    var repull = false
    for (entry in db.outboxDao().getAllOrdered()) {
      when (val result = pushEntry(entry)) {
        is PushResult.Pushed -> {
          db.outboxDao().deleteById(entry.id)
          pushed++
        }
        is PushResult.Dropped -> {
          db.outboxDao().deleteById(entry.id)
          dropped++
          if (result.rebootstrap) rebootstrap = true
          if (result.repull) repull = true
          result.note?.let { errors += it }
        }
        is PushResult.KeepQueued -> {
          errors += result.note
        }
        is PushResult.RetryLater -> {
          db.outboxDao().update(entry.copy(attempts = entry.attempts + 1, lastError = result.note))
          errors += result.note
          return SyncOutcome.Completed(pushed, dropped, errors, needsRetry = true)
        }
        is PushResult.AuthRevoked -> {
          keys.clear()
          setSyncError("API key rejected (401) — enter a new key in Settings → Connection")
          return SyncOutcome.AuthRevoked
        }
        is PushResult.Offline -> return SyncOutcome.Offline
      }
    }
    if (rebootstrap) {
      return when (val outcome = bootstrap()) {
        is BootstrapOutcome.Applied -> SyncOutcome.Completed(pushed, dropped, errors, needsRetry = false)
        is BootstrapOutcome.Unauthorized -> {
          SyncOutcome.AuthRevoked
        }
        is BootstrapOutcome.UpdateRequired ->
          SyncOutcome.Completed(pushed, dropped, errors + "App update needed", needsRetry = false)
        is BootstrapOutcome.Failed ->
          SyncOutcome.Completed(pushed, dropped, errors + outcome.reason, needsRetry = true)
      }
    }
    when (val pull = pullAndMerge()) {
      is PullResult.Ok -> { /* continue to tickets */ }
      is PullResult.AuthRevoked -> return SyncOutcome.AuthRevoked
      is PullResult.Offline -> return SyncOutcome.Offline
      is PullResult.Failed -> {
        errors += pull.reason
        setSyncError(pull.reason)
        return SyncOutcome.Completed(pushed, dropped, errors, needsRetry = true)
      }
    }
    if (repull) {
      // A 409 already triggered the pull above; nothing extra to fetch.
    }
    when (val tickets = pullTickets()) {
      is PullResult.Ok -> { /* done */ }
      is PullResult.AuthRevoked -> return SyncOutcome.AuthRevoked
      is PullResult.Offline -> return SyncOutcome.Offline
      is PullResult.Failed -> {
        errors += tickets.reason
        return SyncOutcome.Completed(pushed, dropped, errors, needsRetry = true)
      }
    }
    val current = db.syncStateDao().get() ?: SyncStateEntity()
    db.syncStateDao().upsert(current.copy(lastSyncAt = clock(), lastError = errors.firstOrNull()))
    return SyncOutcome.Completed(pushed, dropped, errors, needsRetry = false)
  }

  private sealed interface PushResult {
    data object Pushed : PushResult

    data class Dropped(val rebootstrap: Boolean = false, val repull: Boolean = false, val note: String? = null) : PushResult

    data class KeepQueued(val note: String) : PushResult

    data class RetryLater(val note: String) : PushResult

    data object AuthRevoked : PushResult

    data object Offline : PushResult
  }

  private sealed interface PullResult {
    data object Ok : PullResult

    data object AuthRevoked : PullResult

    data object Offline : PullResult

    data class Failed(val reason: String) : PullResult
  }

  private suspend fun pushEntry(entry: OutboxEntry): PushResult {
    return when (entry.kind) {
      OutboxKind.STATUS -> pushStatus(entry)
      OutboxKind.TICKET_CREATE -> pushTicketCreate(entry)
      OutboxKind.TICKET_PATCH -> pushTicketPatch(entry)
    }
  }

  private suspend fun pushStatus(entry: OutboxEntry): PushResult {
    val unitPmsId = entry.unitPmsId ?: return PushResult.Dropped(note = "Status entry without unit")
    val payload = JSONObject(entry.payloadJson)
    val result =
      api.postStatus(
        unitId = unitPmsId,
        expected = payload.getString("expected"),
        action = payload.getString("action"),
        staffName = payload.getString("staffName"),
        note = payload.optString("note", null)?.takeIf { it.isNotBlank() },
      )
    return when (result) {
      is ApiOutcome.Ok -> {
        val room = db.roomDao().getRoomByPmsUnitId(unitPmsId)
        if (room != null) {
          db.roomDao().update(room.copy(pmsHousekeepingAt = result.value.housekeepingAtMillis))
        }
        PushResult.Pushed
      }
      is ApiOutcome.Http ->
        when (result.code) {
          401 -> PushResult.AuthRevoked
          403 -> PushResult.KeepQueued("Room update refused (403 ${result.error ?: "forbidden"}); kept queued")
          404 -> PushResult.Dropped(rebootstrap = true, note = "Unknown unit; re-bootstrapping")
          409 -> {
            conflictLog(unitPmsId, "Sync conflict: PMS moved on; local change not forced")
            PushResult.Dropped(repull = true)
          }
          400 -> {
            // Malformed for this server (over-long name/note, bad enum):
            // retrying the identical body can never succeed.
            transitionLog(unitPmsId, "Sync rejected (400): ${result.error ?: "invalid_input"}")
            PushResult.Dropped(note = "Server rejected entry (400)")
          }
          422 -> {
            transitionLog(unitPmsId, "Sync rejected (422): ${result.error ?: "illegal"}")
            PushResult.Dropped(note = "Server rejected entry (422)")
          }
          else -> PushResult.RetryLater("Server error ${result.code}")
        }
      is ApiOutcome.Network -> PushResult.Offline
      is ApiOutcome.Malformed -> PushResult.RetryLater("Bad server response; will retry")
      is ApiOutcome.NotConfigured -> PushResult.RetryLater("Not configured")
    }
  }

  private suspend fun pushTicketCreate(entry: OutboxEntry): PushResult {
    val payload = JSONObject(entry.payloadJson)
    val result =
      api.postTicket(
        unitId = entry.unitPmsId ?: return PushResult.Dropped(note = "Ticket entry without unit"),
        title = payload.getString("title"),
        description = payload.optString("description", ""),
        priority = payload.getString("priority"),
        staffName = payload.getString("staffName"),
      )
    return when (result) {
      is ApiOutcome.Ok -> {
        val localId = entry.ticketLocalId
        if (localId != null) {
          db.ticketDao().getTicketById(localId)?.let { ticket ->
            db.ticketDao().update(ticket.copy(pmsTicketId = result.value.id))
          }
        }
        PushResult.Pushed
      }
      is ApiOutcome.Http ->
        when (result.code) {
          401 -> PushResult.AuthRevoked
          403 -> PushResult.KeepQueued("Ticket create refused (403 ${result.error ?: "forbidden"}); kept queued")
          404 -> PushResult.Dropped(rebootstrap = true, note = "Unknown unit; re-bootstrapping")
          409 -> PushResult.Dropped(note = "Ticket create conflicted; dropped")
          400 -> PushResult.Dropped(note = "Server rejected ticket (400)")
          422 -> PushResult.Dropped(note = "Server rejected ticket (422)")
          else -> PushResult.RetryLater("Server error ${result.code}")
        }
      is ApiOutcome.Network -> PushResult.Offline
      is ApiOutcome.Malformed -> PushResult.RetryLater("Bad server response; will retry")
      is ApiOutcome.NotConfigured -> PushResult.RetryLater("Not configured")
    }
  }

  private suspend fun pushTicketPatch(entry: OutboxEntry): PushResult {
    val localId = entry.ticketLocalId
    val pmsId = localId?.let { db.ticketDao().getTicketById(it)?.pmsTicketId }
    if (pmsId == null) {
      return PushResult.Dropped(note = "Ticket never reached the server; patch dropped")
    }
    val payload = JSONObject(entry.payloadJson)
    val result =
      api.patchTicket(
        id = pmsId,
        status = payload.getString("status"),
        staffName = payload.getString("staffName"),
      )
    return when (result) {
      is ApiOutcome.Ok -> PushResult.Pushed
      is ApiOutcome.Http ->
        when (result.code) {
          401 -> PushResult.AuthRevoked
          403 -> PushResult.KeepQueued("Ticket update refused (403 ${result.error ?: "forbidden"}); kept queued")
          404 -> PushResult.Dropped(note = "Unknown ticket; dropped")
          409 -> PushResult.Dropped(note = "Ticket patch conflicted; dropped")
          400 -> PushResult.Dropped(note = "Server rejected ticket patch (400)")
          422 -> PushResult.Dropped(note = "Server rejected ticket patch (422)")
          else -> PushResult.RetryLater("Server error ${result.code}")
        }
      is ApiOutcome.Network -> PushResult.Offline
      is ApiOutcome.Malformed -> PushResult.RetryLater("Bad server response; will retry")
      is ApiOutcome.NotConfigured -> PushResult.RetryLater("Not configured")
    }
  }

  private suspend fun conflictLog(unitPmsId: String, reason: String) {
    val room = db.roomDao().getRoomByPmsUnitId(unitPmsId) ?: return
    db.statusLogDao().insert(
      StatusLogEntity(
        roomId = room.id,
        roomName = room.name,
        fromStatus = room.status,
        toStatus = room.status,
        changedBy = "Sync merge",
        changedByRole = StaffRole.SYSTEM,
        changedAt = clock(),
        reason = reason,
        source = StatusLogEntity.SOURCE_PMS,
      )
    )
  }

  private suspend fun transitionLog(unitPmsId: String, reason: String) {
    val room = db.roomDao().getRoomByPmsUnitId(unitPmsId) ?: return
    db.statusLogDao().insert(
      StatusLogEntity(
        roomId = room.id,
        roomName = room.name,
        fromStatus = room.status,
        toStatus = room.status,
        changedBy = "Sync merge",
        changedByRole = StaffRole.SYSTEM,
        changedAt = clock(),
        reason = reason,
        source = StatusLogEntity.SOURCE_PMS,
      )
    )
  }

  private suspend fun setSyncError(message: String) {
    val current = db.syncStateDao().get() ?: SyncStateEntity()
    db.syncStateDao().upsert(current.copy(lastError = message))
  }

  private suspend fun pullAndMerge(): PullResult {
    return when (val result = api.refreshRooms()) {
      is ApiOutcome.Ok -> {
        mergeUnits(result.value.units, result.value.businessDate)
        PullResult.Ok
      }
      is ApiOutcome.Http ->
        if (result.code == 401) {
          keys.clear()
          setSyncError("API key rejected (401) — enter a new key in Settings → Connection")
          PullResult.AuthRevoked
        } else {
          PullResult.Failed("Rooms refresh failed (${result.code})")
        }
      is ApiOutcome.Network -> PullResult.Offline
      is ApiOutcome.Malformed -> PullResult.Failed(result.message)
      is ApiOutcome.NotConfigured -> PullResult.Failed("Not configured")
    }
  }

  private suspend fun mergeUnits(units: List<PmsUnit>, businessDate: String?) {
    db.withTransaction {
      val seen = mutableSetOf<String>()
      units.forEach { unit ->
        seen += unit.id
        val type = db.roomTypeDao().getByPmsTypeId(unit.roomTypeId) ?: return@forEach
        val mapped = mapUnitToStatus(unit)
        val existing = db.roomDao().getRoomByPmsUnitId(unit.id)
        if (existing == null) {
          db.roomDao().insertAll(
            listOf(
              RoomEntity(
                name = unit.unitNumber,
                roomTypeId = type.id,
                roomTypeName = type.name,
                floor = unit.floor ?: UNKNOWN_FLOOR,
                status = mapped,
                assignedTo = null,
                updatedAt = unit.housekeepingAtMillis ?: clock(),
                pmsUnitId = unit.id,
                block = unit.block,
                departsToday = unit.departsToday,
                pmsUnitStatus = unit.unitStatus,
                pmsHousekeepingAt = unit.housekeepingAtMillis,
              )
            )
          )
          return@forEach
        }
        val latestRun = db.checklistRunDao().getLatestForRoom(existing.id)
        val inProgress = latestRun != null && latestRun.finishedAt == null
        val pmsAt = unit.housekeepingAtMillis
        val refreshed =
          existing.copy(
            name = unit.unitNumber,
            roomTypeId = type.id,
            roomTypeName = type.name,
            floor = unit.floor ?: UNKNOWN_FLOOR,
            block = unit.block,
            departsToday = unit.departsToday,
            pmsUnitStatus = unit.unitStatus,
            pmsHousekeepingAt = pmsAt,
          )
        if (inProgress && mapped != RoomStatus.DIRTY) {
          // Both sides moved: last write wins by timestamp.
          if (pmsAt != null && pmsAt >= existing.updatedAt) {
            db.roomDao().update(
              refreshed.copy(status = mapped, updatedAt = pmsAt)
            )
            mergeLog(existing, mapped, "PMS update applied (newer than local touch)", StatusLogEntity.SOURCE_PMS)
          } else {
            db.roomDao().update(refreshed)
            mergeLog(
              existing, existing.status,
              "Kept local clean over older PMS state", StatusLogEntity.SOURCE_LOCAL,
            )
          }
        } else if (!inProgress && mapped != existing.status) {
          // PMS is source of truth for settled rooms (covers departed-dirty).
          db.roomDao().update(
            refreshed.copy(status = mapped, updatedAt = pmsAt ?: clock())
          )
          mergeLog(existing, mapped, "PMS update applied", StatusLogEntity.SOURCE_PMS)
        } else {
          db.roomDao().update(refreshed)
        }
      }
      // PMS wins existence: archive missing units unless a clean is in progress.
      val inProgressRooms = db.checklistRunDao().unfinishedRoomIds().toSet()
      db.roomDao().getPmsRooms()
        .filter { it.pmsUnitId !in seen && it.id !in inProgressRooms }
        .forEach { db.roomDao().deleteById(it.id) }
      if (businessDate != null) {
        val current = db.syncStateDao().get() ?: SyncStateEntity()
        db.syncStateDao().upsert(current.copy(businessDate = businessDate))
      }
    }
  }

  private suspend fun mergeLog(room: RoomEntity, to: RoomStatus, reason: String, source: String) {
    db.statusLogDao().insert(
      StatusLogEntity(
        roomId = room.id,
        roomName = room.name,
        fromStatus = room.status,
        toStatus = to,
        changedBy = "Sync merge",
        changedByRole = StaffRole.SYSTEM,
        changedAt = clock(),
        reason = reason,
        source = source,
      )
    )
  }

  private suspend fun pullTickets(): PullResult {
    return when (val result = api.getOpenTickets()) {
      is ApiOutcome.Ok -> {
        db.withTransaction {
          result.value.forEach { ticket ->
            val mappedPriority =
              runCatching { TicketPriority.valueOf(ticket.priority) }.getOrNull()
            val mappedStatus =
              runCatching { TicketStatus.valueOf(ticket.status) }.getOrNull()
            if (mappedPriority == null || mappedStatus == null) return@forEach
            val room = ticket.unitId.let { db.roomDao().getRoomByPmsUnitId(it) } ?: return@forEach
            val existing = db.ticketDao().getByPmsTicketId(ticket.id)
            if (existing == null) {
              db.ticketDao().insertAll(
                listOf(
                  TicketEntity(
                    roomId = room.id,
                    roomName = room.name,
                    title = ticket.title,
                    description = ticket.description,
                    priority = mappedPriority,
                    status = mappedStatus,
                    createdBy = ticket.createdBy.ifBlank { "PMS" },
                    createdAt = parseLenientInstant(ticket.createdAt, field = "createdAt", ticketId = ticket.id),
                    pmsTicketId = ticket.id,
                    )
                )
              )
            } else {
              db.ticketDao().update(
                existing.copy(
                  roomId = room.id,
                  roomName = room.name,
                  title = ticket.title,
                  description = ticket.description,
                  priority = mappedPriority,
                  status = mappedStatus,
                  resolvedBy = ticket.doneBy,
                  resolvedAt = ticket.doneAt?.let { parseLenientInstant(it, field = "doneAt", ticketId = ticket.id) },
                )
              )
            }
          }
        }
        PullResult.Ok
      }
      is ApiOutcome.Http ->
        if (result.code == 401) {
          keys.clear()
          setSyncError("API key rejected (401) — enter a new key in Settings → Connection")
          PullResult.AuthRevoked
        } else {
          PullResult.Failed("Ticket refresh failed (${result.code})")
        }
      is ApiOutcome.Network -> PullResult.Offline
      is ApiOutcome.Malformed -> PullResult.Failed(result.message)
      is ApiOutcome.NotConfigured -> PullResult.Failed("Not configured")
    }
  }

  private fun parseLenientInstant(raw: String, field: String, ticketId: String): Long =
    try {
      parseInstantMillis(raw)
    } catch (_: MalformedPayloadException) {
      runCatching { Log.d("SyncEngine", "Unparseable $field timestamp for ticket $ticketId") }
      clock()
    }
}
