package com.vivescriptsolutions.roomready.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
  val isDemo: Boolean = true,
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
  val isDemo: Boolean = true,
  val notes: String = "",
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
  val priority: TicketPriority,
  val status: TicketStatus,
  val createdBy: String,
  val createdAt: Long,
  val isDemo: Boolean = true,
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
)
