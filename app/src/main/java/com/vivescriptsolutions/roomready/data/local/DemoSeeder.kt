package com.vivescriptsolutions.roomready.data.local

import androidx.room.withTransaction
import com.vivescriptsolutions.roomready.domain.ChecklistStepKey
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus

object DemoSeeder {
  const val TYPE_DELUXE_KING = "Deluxe King"
  const val TYPE_FAMILY_SUITE = "Family Suite"
  const val TYPE_GARDEN_COTTAGE = "Garden Cottage"

  suspend fun seedIfEmpty(db: AppDatabase, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (db.roomDao().count() > 0) {
      return false
    }
    seedDemoData(db, nowMillis)
    return true
  }

  suspend fun resetAndSeed(db: AppDatabase, nowMillis: Long = System.currentTimeMillis()) {
    db.withTransaction {
      db.statusLogDao().deleteAll()
      db.ticketDao().deleteAll()
      db.checklistRunDao().deleteAll()
      db.roomDao().deleteAll()
      db.roomTypeDao().deleteAll()
      insertDemoRecords(db, nowMillis)
    }
  }

  private suspend fun seedDemoData(db: AppDatabase, nowMillis: Long) {
    db.withTransaction {
      if (db.roomDao().count() > 0) return@withTransaction
      insertDemoRecords(db, nowMillis)
    }
  }

  private suspend fun insertDemoRecords(db: AppDatabase, nowMillis: Long) {
    val standardKeys = ChecklistStepKey.standardTemplateKeys
    val typeIds =
      db.roomTypeDao()
        .insertAll(
          listOf(
            RoomTypeEntity(
              name = TYPE_DELUXE_KING,
              checklistTemplateSteps = standardKeys,
              isDemo = true,
            ),
            RoomTypeEntity(
              name = TYPE_FAMILY_SUITE,
              checklistTemplateSteps = standardKeys,
              isDemo = true,
            ),
            RoomTypeEntity(
              name = TYPE_GARDEN_COTTAGE,
              checklistTemplateSteps = standardKeys,
              isDemo = true,
            ),
          )
        )

    val deluxeId = typeIds[0]
    val suiteId = typeIds[1]
    val cottageId = typeIds[2]

    val minute = 60_000L
    val seedRooms =
      listOf(
        // Floor 1 — Deluxe King (4 rooms)
        RoomEntity(
          name = "101",
          roomTypeId = deluxeId,
          roomTypeName = TYPE_DELUXE_KING,
          floor = 1,
          status = RoomStatus.DIRTY,
          assignedTo = null,
          updatedAt = nowMillis - 42 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "102",
          roomTypeId = deluxeId,
          roomTypeName = TYPE_DELUXE_KING,
          floor = 1,
          status = RoomStatus.CLEANING,
          assignedTo = "Amina Rahman",
          updatedAt = nowMillis - 18 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "103",
          roomTypeId = deluxeId,
          roomTypeName = TYPE_DELUXE_KING,
          floor = 1,
          status = RoomStatus.INSPECTED,
          assignedTo = "Tariq Hasan",
          updatedAt = nowMillis - 12 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "104",
          roomTypeId = deluxeId,
          roomTypeName = TYPE_DELUXE_KING,
          floor = 1,
          status = RoomStatus.READY,
          assignedTo = "Amina Rahman",
          updatedAt = nowMillis - 65 * minute,
          isDemo = true,
        ),
        // Floor 2 — Family Suite (4 rooms)
        RoomEntity(
          name = "201",
          roomTypeId = suiteId,
          roomTypeName = TYPE_FAMILY_SUITE,
          floor = 2,
          status = RoomStatus.DIRTY,
          assignedTo = null,
          updatedAt = nowMillis - 55 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "202",
          roomTypeId = suiteId,
          roomTypeName = TYPE_FAMILY_SUITE,
          floor = 2,
          status = RoomStatus.CLEANING,
          assignedTo = "Maya Devi",
          updatedAt = nowMillis - 9 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "203",
          roomTypeId = suiteId,
          roomTypeName = TYPE_FAMILY_SUITE,
          floor = 2,
          status = RoomStatus.READY,
          assignedTo = "Maya Devi",
          updatedAt = nowMillis - 90 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "204",
          roomTypeId = suiteId,
          roomTypeName = TYPE_FAMILY_SUITE,
          floor = 2,
          status = RoomStatus.OUT_OF_SERVICE,
          assignedTo = "Rafiq Islam",
          updatedAt = nowMillis - 140 * minute,
          isDemo = true,
        ),
        // Ground Wing (floor = 0) — Garden Cottage (4 rooms)
        RoomEntity(
          name = "G01",
          roomTypeId = cottageId,
          roomTypeName = TYPE_GARDEN_COTTAGE,
          floor = 0,
          status = RoomStatus.CLEANING,
          assignedTo = "Nadia Chowdhury",
          updatedAt = nowMillis - 15 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "G02",
          roomTypeId = cottageId,
          roomTypeName = TYPE_GARDEN_COTTAGE,
          floor = 0,
          status = RoomStatus.INSPECTED,
          assignedTo = "Sajib Mia",
          updatedAt = nowMillis - 24 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "G03",
          roomTypeId = cottageId,
          roomTypeName = TYPE_GARDEN_COTTAGE,
          floor = 0,
          status = RoomStatus.READY,
          assignedTo = "Nadia Chowdhury",
          updatedAt = nowMillis - 110 * minute,
          isDemo = true,
        ),
        RoomEntity(
          name = "G04",
          roomTypeId = cottageId,
          roomTypeName = TYPE_GARDEN_COTTAGE,
          floor = 0,
          status = RoomStatus.DIRTY,
          assignedTo = null,
          updatedAt = nowMillis - 33 * minute,
          isDemo = true,
        ),
      )

    val roomIds = db.roomDao().insertAll(seedRooms)
    val roomIdByName = seedRooms.mapIndexed { idx, room -> room.name to roomIds[idx] }.toMap()

    // Seed active/recent ChecklistRuns for rooms in CLEANING, INSPECTED, READY
    val checklistDao = db.checklistRunDao()
    checklistDao.insert(
      ChecklistRunEntity(
        roomId = roomIdByName.getValue("102"),
        performedBy = "Amina Rahman",
        performedByRole = StaffRole.HOUSEKEEPER,
        startedAt = nowMillis - 22 * minute,
        finishedAt = null,
        steps = buildSteps(doneCount = 5, noteAtIndex = 4 to "Added 2 extra bath towels"),
      )
    )
    checklistDao.insert(
      ChecklistRunEntity(
        roomId = roomIdByName.getValue("202"),
        performedBy = "Maya Devi",
        performedByRole = StaffRole.HOUSEKEEPER,
        startedAt = nowMillis - 14 * minute,
        finishedAt = null,
        steps = buildSteps(doneCount = 3),
      )
    )
    checklistDao.insert(
      ChecklistRunEntity(
        roomId = roomIdByName.getValue("G01"),
        performedBy = "Nadia Chowdhury",
        performedByRole = StaffRole.HOUSEKEEPER,
        startedAt = nowMillis - 19 * minute,
        finishedAt = null,
        steps = buildSteps(doneCount = 6),
      )
    )
    checklistDao.insert(
      ChecklistRunEntity(
        roomId = roomIdByName.getValue("103"),
        performedBy = "Tariq Hasan",
        performedByRole = StaffRole.HOUSEKEEPER,
        startedAt = nowMillis - 45 * minute,
        finishedAt = nowMillis - 12 * minute,
        steps = buildSteps(doneCount = 8),
      )
    )
    checklistDao.insert(
      ChecklistRunEntity(
        roomId = roomIdByName.getValue("G02"),
        performedBy = "Sajib Mia",
        performedByRole = StaffRole.HOUSEKEEPER,
        startedAt = nowMillis - 58 * minute,
        finishedAt = nowMillis - 24 * minute,
        steps = buildSteps(doneCount = 8),
      )
    )

    // Seed Demo Tickets
    db.ticketDao()
      .insertAll(
        listOf(
          TicketEntity(
            roomId = roomIdByName.getValue("204"),
            roomName = "204",
            title = "AC unit dripping onto balcony floor",
            priority = TicketPriority.URGENT,
            status = TicketStatus.OPEN,
            createdBy = "Rafiq Islam",
            createdAt = nowMillis - 140 * minute,
            isDemo = true,
          ),
          TicketEntity(
            roomId = roomIdByName.getValue("102"),
            roomName = "102",
            title = "Vanity mirror warm bulb flickering",
            priority = TicketPriority.NORMAL,
            status = TicketStatus.OPEN,
            createdBy = "Amina Rahman",
            createdAt = nowMillis - 16 * minute,
            isDemo = true,
          ),
        )
      )

    // Seed initial StatusLog entries matching starting statuses
    db.statusLogDao()
      .insertAll(
        listOf(
          StatusLogEntity(
            roomId = roomIdByName.getValue("102"),
            roomName = "102",
            fromStatus = RoomStatus.DIRTY,
            toStatus = RoomStatus.CLEANING,
            changedBy = "Amina Rahman",
            changedByRole = StaffRole.HOUSEKEEPER,
            changedAt = nowMillis - 18 * minute,
          ),
          StatusLogEntity(
            roomId = roomIdByName.getValue("103"),
            roomName = "103",
            fromStatus = RoomStatus.CLEANING,
            toStatus = RoomStatus.INSPECTED,
            changedBy = "Tariq Hasan",
            changedByRole = StaffRole.HOUSEKEEPER,
            changedAt = nowMillis - 12 * minute,
          ),
          StatusLogEntity(
            roomId = roomIdByName.getValue("104"),
            roomName = "104",
            fromStatus = RoomStatus.INSPECTED,
            toStatus = RoomStatus.READY,
            changedBy = "Rafiq Islam",
            changedByRole = StaffRole.SUPERVISOR,
            changedAt = nowMillis - 65 * minute,
          ),
          StatusLogEntity(
            roomId = roomIdByName.getValue("204"),
            roomName = "204",
            fromStatus = RoomStatus.DIRTY,
            toStatus = RoomStatus.OUT_OF_SERVICE,
            changedBy = "Rafiq Islam",
            changedByRole = StaffRole.SUPERVISOR,
            changedAt = nowMillis - 140 * minute,
          ),
          StatusLogEntity(
            roomId = roomIdByName.getValue("G02"),
            roomName = "G02",
            fromStatus = RoomStatus.CLEANING,
            toStatus = RoomStatus.INSPECTED,
            changedBy = "Sajib Mia",
            changedByRole = StaffRole.HOUSEKEEPER,
            changedAt = nowMillis - 24 * minute,
          ),
        )
      )
  }

  fun buildTemplateSteps(templateKeys: List<String> = ChecklistStepKey.standardTemplateKeys): List<ChecklistStepState> {
    val keys = if (templateKeys.isEmpty()) ChecklistStepKey.standardTemplateKeys else templateKeys
    return keys.mapIndexed { index, rawKey ->
      val known = ChecklistStepKey.fromKeyOrTitle(rawKey)
      ChecklistStepState(
        stepIndex = index,
        key = known?.key ?: rawKey,
        title = known?.defaultTitle ?: rawKey,
        isDone = false,
        note = "",
      )
    }
  }

  private fun buildSteps(
    doneCount: Int,
    noteAtIndex: Pair<Int, String>? = null,
  ): List<ChecklistStepState> =
    ChecklistStepKey.entries.mapIndexed { index, step ->
      ChecklistStepState(
        stepIndex = index,
        key = step.key,
        title = step.defaultTitle,
        isDone = index < doneCount,
        note = if (noteAtIndex?.first == index) noteAtIndex.second else "",
      )
    }
}
