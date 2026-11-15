package com.vivescriptsolutions.roomready.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus

@Entity(tableName = "room_types")
data class RoomTypeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val checklistTemplateSteps: List<String>,
  val pmsTypeId: String? = null,
)

@Entity(
  tableName = "rooms",
  foreignKeys =
    [
      ForeignKey(
        entity = RoomTypeEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomTypeId"],
        onDelete = ForeignKey.RESTRICT,
      )
    ],
  indices = [Index(value = ["roomTypeId"]), Index(value = ["name"], unique = true)],
)
data class RoomEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val roomTypeId: Long,
  val roomTypeName: String,
  val floor: Int,
  val status: RoomStatus,
  val assignedTo: String?,
  val updatedAt: Long,
  val notes: String = "",
  val isOccupied: Boolean = false,
  // PMS snapshot columns. A null PMS floor maps to UNKNOWN_FLOOR (-1);
  // floor stays non-null because the column predates sync.
  val pmsUnitId: String? = null,
  val block: String? = null,
  val departsToday: Boolean = false,
  val pmsUnitStatus: String? = null,
  val pmsHousekeepingAt: Long? = null,
)

@Entity(
  tableName = "checklist_runs",
  foreignKeys =
    [
      ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["roomId"])],
)
data class ChecklistRunEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val roomId: Long,
  val performedBy: String,
  val performedByRole: StaffRole,
  val startedAt: Long,
  val finishedAt: Long?,
  val steps: List<ChecklistStepState>,
  val runKind: ChecklistRunKind = ChecklistRunKind.FULL,
  val returnReasonKey: String? = null,
  val returnNote: String = "",
  val photoPath: String? = null,
  val photoSkippedReason: String? = null,
)

@Entity(
  tableName = "tickets",
  foreignKeys =
    [
      ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["roomId"])],
)
data class TicketEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val roomId: Long,
  val roomName: String,
  val title: String,
  val description: String = "",
  val priority: TicketPriority,
  val status: TicketStatus,
  val createdBy: String,
  val createdAt: Long,
  val resolvedBy: String? = null,
  val resolvedAt: Long? = null,
  val photoPath: String? = null,
  val pmsTicketId: String? = null,
)

@Entity(
  tableName = "status_logs",
  foreignKeys =
    [
      ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["roomId"]), Index(value = ["roomId", "changedAt"])],
)
data class StatusLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val roomId: Long,
  val roomName: String,
  val fromStatus: RoomStatus,
  val toStatus: RoomStatus,
  val changedBy: String,
  val changedByRole: StaffRole,
  val changedAt: Long,
  val reason: String? = null,
  val source: String = SOURCE_LOCAL,
) {
  companion object {
    const val SOURCE_LOCAL = "local"
    const val SOURCE_PMS = "pms"
  }
}

@Entity(tableName = "staff_members")
data class StaffMemberEntity(
  @PrimaryKey val pmsId: String,
  val name: String,
  val role: StaffRole,
)

enum class OutboxKind {
  STATUS,
  TICKET_CREATE,
  TICKET_PATCH,
}

@Entity(
  tableName = "outbox_entries",
  indices = [Index(value = ["createdAt"])],
)
data class OutboxEntry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val createdAt: Long,
  val kind: OutboxKind,
  val unitPmsId: String?,
  val ticketLocalId: Long?,
  val payloadJson: String,
  val attempts: Int = 0,
  val lastError: String? = null,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
  @PrimaryKey val id: Int = 1,
  val propertyName: String? = null,
  val propertyTimezone: String? = null,
  val businessDate: String? = null,
  val contractVersion: Int? = null,
  val lastSyncAt: Long? = null,
  val lastError: String? = null,
)
