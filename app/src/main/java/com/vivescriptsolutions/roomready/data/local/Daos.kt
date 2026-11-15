package com.vivescriptsolutions.roomready.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import kotlinx.coroutines.flow.Flow

data class StatusCount(val status: RoomStatus, val count: Int)

data class TicketPriorityCount(val priority: TicketPriority, val count: Int)

data class RunReturnFlag(val roomId: Long, val returnReasonKey: String, val returnNote: String)

@Dao
interface RoomTypeDao {
  @Query("SELECT * FROM room_types ORDER BY id ASC")
  fun observeAll(): Flow<List<RoomTypeEntity>>

  @Query("SELECT * FROM room_types WHERE id = :id")
  suspend fun getById(id: Long): RoomTypeEntity?

  @Query("SELECT COUNT(*) FROM room_types")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(roomTypes: List<RoomTypeEntity>): List<Long>

  @Query("DELETE FROM room_types")
  suspend fun deleteAll()
}

@Dao
interface RoomDao {
  @Query("SELECT * FROM rooms ORDER BY floor ASC, name ASC")
  fun observeAllRooms(): Flow<List<RoomEntity>>

  @Query("SELECT * FROM rooms WHERE id = :roomId")
  fun observeRoomById(roomId: Long): Flow<RoomEntity?>

  @Query("SELECT * FROM rooms WHERE id = :roomId")
  suspend fun getRoomById(roomId: Long): RoomEntity?

  @Query("SELECT status, COUNT(*) AS count FROM rooms GROUP BY status")
  fun observeStatusCounts(): Flow<List<StatusCount>>

  @Query("SELECT * FROM rooms WHERE status = :status ORDER BY updatedAt ASC")
  fun observeRoomsByStatus(status: RoomStatus): Flow<List<RoomEntity>>

  @Query("SELECT COUNT(*) FROM rooms")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(rooms: List<RoomEntity>): List<Long>

  @Update
  suspend fun update(room: RoomEntity)

  @Query("DELETE FROM rooms")
  suspend fun deleteAll()
}

@Dao
interface ChecklistRunDao {
  @Query("SELECT * FROM checklist_runs WHERE roomId = :roomId ORDER BY startedAt DESC LIMIT 1")
  fun observeLatestForRoom(roomId: Long): Flow<ChecklistRunEntity?>

  @Query("SELECT * FROM checklist_runs WHERE roomId = :roomId ORDER BY startedAt DESC LIMIT 1")
  suspend fun getLatestForRoom(roomId: Long): ChecklistRunEntity?

  @Query("SELECT * FROM checklist_runs ORDER BY startedAt DESC")
  suspend fun getAll(): List<ChecklistRunEntity>

  @Query("SELECT roomId, returnReasonKey, returnNote FROM checklist_runs WHERE finishedAt IS NULL AND returnReasonKey IS NOT NULL")
  fun observeReturnedRunFlags(): Flow<List<RunReturnFlag>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(run: ChecklistRunEntity): Long

  @Update
  suspend fun update(run: ChecklistRunEntity)

  @Query("DELETE FROM checklist_runs")
  suspend fun deleteAll()
}

@Dao
interface TicketDao {
  @Query("SELECT * FROM tickets ORDER BY status ASC, createdAt DESC")
  fun observeAllTickets(): Flow<List<TicketEntity>>

  @Query("SELECT * FROM tickets WHERE id = :ticketId")
  fun observeTicketById(ticketId: Long): Flow<TicketEntity?>

  @Query("SELECT * FROM tickets WHERE id = :ticketId")
  suspend fun getTicketById(ticketId: Long): TicketEntity?

  @Query("SELECT priority, COUNT(*) AS count FROM tickets WHERE status = :status GROUP BY priority")
  fun observeCountByPriority(status: TicketStatus): Flow<List<TicketPriorityCount>>

  @Query("SELECT * FROM tickets WHERE roomId = :roomId ORDER BY status ASC, createdAt DESC")
  fun observeTicketsForRoom(roomId: Long): Flow<List<TicketEntity>>

  @Query("SELECT COUNT(*) FROM tickets")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(ticket: TicketEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(tickets: List<TicketEntity>): List<Long>

  @Update
  suspend fun update(ticket: TicketEntity)

  @Query("DELETE FROM tickets")
  suspend fun deleteAll()
}

@Dao
interface StatusLogDao {
  @Query("SELECT * FROM status_logs WHERE roomId = :roomId ORDER BY changedAt DESC")
  fun observeLogsForRoom(roomId: Long): Flow<List<StatusLogEntity>>

  @Query("SELECT * FROM status_logs ORDER BY changedAt DESC LIMIT :limit")
  fun observeRecentLogs(limit: Int = 25): Flow<List<StatusLogEntity>>

  @Query("SELECT * FROM status_logs WHERE toStatus = :toStatus AND changedAt >= :sinceMillis ORDER BY changedAt DESC")
  fun observeLogsSince(toStatus: RoomStatus, sinceMillis: Long): Flow<List<StatusLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(log: StatusLogEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(logs: List<StatusLogEntity>): List<Long>

  @Query("DELETE FROM status_logs")
  suspend fun deleteAll()
}
