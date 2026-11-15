package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.ui.screens.ChecklistRunScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChecklistRunFlowTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var repository: HousekeepingRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repository = HousekeepingRepository(db, UserPreferencesRepository(context))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `start full clean on dirty room creates 8-step run and moves to cleaning`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val dirtyRoom =
        db.roomDao().observeAllRooms().first().first {
          it.status == RoomStatus.DIRTY && !it.isOccupied
        }

      val result =
        repository.startChecklistRun(
          roomId = dirtyRoom.id,
          kind = ChecklistRunKind.FULL,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(result.isSuccess)

      val updated = db.roomDao().getRoomById(dirtyRoom.id)!!
      assertEquals(RoomStatus.CLEANING, updated.status)
      assertEquals("Amina Rahman", updated.assignedTo)

      val run = db.checklistRunDao().getLatestForRoom(dirtyRoom.id)
      assertNotNull(run)
      assertEquals(ChecklistRunKind.FULL, run!!.runKind)
      assertEquals(8, run.steps.size)
      assertNull(run.finishedAt)

      val logs = db.statusLogDao().observeLogsForRoom(dirtyRoom.id).first()
      assertEquals(1, logs.size)
      assertEquals(RoomStatus.DIRTY, logs[0].fromStatus)
      assertEquals(RoomStatus.CLEANING, logs[0].toStatus)
    }
  }

  @Test
  fun `stayover selectable on occupied room creates 4-step run`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val occupiedDirty =
        db.roomDao().observeAllRooms().first().first {
          it.status == RoomStatus.DIRTY && it.isOccupied
        }

      val result =
        repository.startChecklistRun(
          roomId = occupiedDirty.id,
          kind = ChecklistRunKind.STAYOVER,
          staffName = "Maya Devi",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(result.isSuccess)

      val run = db.checklistRunDao().getLatestForRoom(occupiedDirty.id)
      assertNotNull(run)
      assertEquals(ChecklistRunKind.STAYOVER, run!!.runKind)
      assertEquals(listOf("Bathroom quick", "Beds made", "Towels", "Bins"), run.steps.map { it.title })
    }
  }

  @Test
  fun `stayover rejected on vacant room`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val vacantDirty =
        db.roomDao().observeAllRooms().first().first {
          it.status == RoomStatus.DIRTY && !it.isOccupied
        }

      val result =
        repository.startChecklistRun(
          roomId = vacantDirty.id,
          kind = ChecklistRunKind.STAYOVER,
          staffName = "Maya Devi",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(result.isFailure)
      assertEquals(RoomStatus.DIRTY, db.roomDao().getRoomById(vacantDirty.id)!!.status)
      assertNull(db.checklistRunDao().getLatestForRoom(vacantDirty.id))
    }
  }

  @Test
  fun `finish blocked at 7 of 8`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val dirtyRoom = db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
      repository.startChecklistRun(
        roomId = dirtyRoom.id,
        kind = ChecklistRunKind.FULL,
        staffName = "Amina Rahman",
        role = StaffRole.HOUSEKEEPER,
      )
      repeat(7) { index ->
        repository.toggleChecklistStep(
          roomId = dirtyRoom.id,
          stepIndex = index,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      }

      val result =
        repository.finishChecklistRun(
          roomId = dirtyRoom.id,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(result.isFailure)

      assertEquals(RoomStatus.CLEANING, db.roomDao().getRoomById(dirtyRoom.id)!!.status)
      assertNull(db.checklistRunDao().getLatestForRoom(dirtyRoom.id)!!.finishedAt)
      val logs = db.statusLogDao().observeLogsForRoom(dirtyRoom.id).first()
      assertTrue(logs.none { it.toStatus == RoomStatus.CLEANED })
    }
  }

  @Test
  fun `finish at 8 of 8 stamps run moves to cleaned and logs`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val dirtyRoom = db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
      repository.startChecklistRun(
        roomId = dirtyRoom.id,
        kind = ChecklistRunKind.FULL,
        staffName = "Amina Rahman",
        role = StaffRole.HOUSEKEEPER,
      )
      repeat(8) { index ->
        repository.toggleChecklistStep(
          roomId = dirtyRoom.id,
          stepIndex = index,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      }

      repository.setRunPhoto(dirtyRoom.id, "run_after.jpg")
      val result =
        repository.finishChecklistRun(
          roomId = dirtyRoom.id,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(result.isSuccess)

      val run = db.checklistRunDao().getLatestForRoom(dirtyRoom.id)!!
      assertNotNull(run.finishedAt)
      assertEquals(RoomStatus.CLEANED, db.roomDao().getRoomById(dirtyRoom.id)!!.status)

      val logs = db.statusLogDao().observeLogsForRoom(dirtyRoom.id).first()
      val finishLog = logs.first { it.toStatus == RoomStatus.CLEANED }
      assertEquals(RoomStatus.CLEANING, finishLog.fromStatus)
      assertEquals("Amina Rahman", finishLog.changedBy)
    }
  }

  @Test
  fun `run survives process death via room store`() {
    val dbName = "process_death_test.db"
    context.deleteDatabase(dbName)
    val roomId =
      runBlocking {
        val fileDb =
          Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val fileRepo = HousekeepingRepository(fileDb, UserPreferencesRepository(context))
        DemoSeeder.seedIfEmpty(fileDb, nowMillis = 1_700_000_000_000L)
        val dirtyRoom =
          fileDb.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
        val runId =
          fileRepo.startChecklistRun(
            roomId = dirtyRoom.id,
            kind = ChecklistRunKind.FULL,
            staffName = "Amina Rahman",
            role = StaffRole.HOUSEKEEPER,
          ).getOrThrow()
        fileRepo.toggleChecklistStep(
          roomId = dirtyRoom.id,
          stepIndex = 0,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
        fileRepo.toggleChecklistStep(
          roomId = dirtyRoom.id,
          stepIndex = 2,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
        fileRepo.updateChecklistStepNote(
          roomId = dirtyRoom.id,
          stepIndex = 2,
          note = "Restock shampoo",
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
        val beforeDeath = fileDb.checklistRunDao().getLatestForRoom(dirtyRoom.id)!!
        assertEquals(runId, beforeDeath.id)
        fileDb.close()

        // Simulate process death: reopen the same database file from scratch.
        val reopenedDb =
          Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val reopenedRepo = HousekeepingRepository(reopenedDb, UserPreferencesRepository(context))
        val resumed = reopenedDb.checklistRunDao().getLatestForRoom(dirtyRoom.id)
        assertNotNull(resumed)
        assertEquals(runId, resumed!!.id)
        assertNull(resumed.finishedAt)
        assertEquals(2, resumed.steps.count { it.isDone })
        assertTrue(resumed.steps[0].isDone)
        assertTrue(resumed.steps[2].isDone)
        assertEquals("Restock shampoo", resumed.steps[2].note)
        assertEquals(RoomStatus.CLEANING, reopenedDb.roomDao().getRoomById(dirtyRoom.id)!!.status)

        // Resume and finish the surviving run.
        reopenedDb.checklistRunDao().getLatestForRoom(dirtyRoom.id)!!.steps
          .filter { !it.isDone }
          .forEach { step ->
            reopenedRepo.toggleChecklistStep(
              roomId = dirtyRoom.id,
              stepIndex = step.stepIndex,
              staffName = "Amina Rahman",
              role = StaffRole.HOUSEKEEPER,
            )
          }
        reopenedRepo.skipRunPhoto(dirtyRoom.id, " resumed on lab device without camera ")
        val finishResult =
          reopenedRepo.finishChecklistRun(
            roomId = dirtyRoom.id,
            staffName = "Amina Rahman",
            role = StaffRole.HOUSEKEEPER,
          )
        assertTrue(finishResult.isSuccess)
        assertEquals(RoomStatus.CLEANED, reopenedDb.roomDao().getRoomById(dirtyRoom.id)!!.status)
        reopenedDb.close()
        dirtyRoom.id
      }
    assertTrue(roomId > 0)
    context.deleteDatabase(dbName)
  }

  @Test
  fun `real room migration 2 to 3 preserves data and defaults new columns`() {
    val dbName = "migration_2_3_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(2) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rooms (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              name TEXT NOT NULL,
              roomTypeId INTEGER NOT NULL,
              roomTypeName TEXT NOT NULL,
              floor INTEGER NOT NULL,
              status TEXT NOT NULL,
              assignedTo TEXT,
              updatedAt INTEGER NOT NULL,
              isDemo INTEGER NOT NULL,
              notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS checklist_runs (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              roomId INTEGER NOT NULL,
              performedBy TEXT NOT NULL,
              performedByRole TEXT NOT NULL,
              startedAt INTEGER NOT NULL,
              finishedAt INTEGER,
              steps TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO rooms (id, name, roomTypeId, roomTypeName, floor, status, assignedTo, updatedAt, isDemo, notes) VALUES (1, '101', 1, 'Deluxe King', 1, 'CLEANING', 'Amina Rahman', 1000, 1, '')"
          )
          db.execSQL(
            "INSERT INTO checklist_runs (id, roomId, performedBy, performedByRole, startedAt, finishedAt, steps) VALUES (1, 1, 'Amina Rahman', 'HOUSEKEEPER', 900, NULL, '[]')"
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }

    val helper =
      factory.create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbName)
          .callback(callback)
          .build()
      )
    val sqliteDb = helper.writableDatabase
    AppDatabase.MIGRATION_2_3.migrate(sqliteDb)

    val roomCursor = sqliteDb.query("SELECT name, isOccupied FROM rooms WHERE id = 1")
    assertTrue(roomCursor.moveToFirst())
    assertEquals("101", roomCursor.getString(0))
    assertEquals(0, roomCursor.getInt(1))
    roomCursor.close()

    val runCursor = sqliteDb.query("SELECT performedBy, runKind FROM checklist_runs WHERE id = 1")
    assertTrue(runCursor.moveToFirst())
    assertEquals("Amina Rahman", runCursor.getString(0))
    assertEquals("FULL", runCursor.getString(1))
    runCursor.close()

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `real room migration 3 to 4 rewrites inspected values to cleaned`() {
    val dbName = "migration_3_4_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(3) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rooms (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              name TEXT NOT NULL,
              roomTypeId INTEGER NOT NULL,
              roomTypeName TEXT NOT NULL,
              floor INTEGER NOT NULL,
              status TEXT NOT NULL,
              assignedTo TEXT,
              updatedAt INTEGER NOT NULL,
              isDemo INTEGER NOT NULL,
              notes TEXT NOT NULL DEFAULT '',
              isOccupied INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS status_logs (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              roomId INTEGER NOT NULL,
              roomName TEXT NOT NULL,
              fromStatus TEXT NOT NULL,
              toStatus TEXT NOT NULL,
              changedBy TEXT NOT NULL,
              changedByRole TEXT NOT NULL,
              changedAt INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO rooms (id, name, roomTypeId, roomTypeName, floor, status, assignedTo, updatedAt, isDemo, notes, isOccupied) VALUES (1, '103', 1, 'Deluxe King', 1, 'INSPECTED', 'Tariq Hasan', 1000, 1, '', 0)"
          )
          db.execSQL(
            "INSERT INTO rooms (id, name, roomTypeId, roomTypeName, floor, status, assignedTo, updatedAt, isDemo, notes, isOccupied) VALUES (2, '101', 1, 'Deluxe King', 1, 'DIRTY', NULL, 1000, 1, '', 1)"
          )
          db.execSQL(
            "INSERT INTO status_logs (id, roomId, roomName, fromStatus, toStatus, changedBy, changedByRole, changedAt) VALUES (1, 1, '103', 'CLEANING', 'INSPECTED', 'Tariq Hasan', 'HOUSEKEEPER', 900)"
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }

    val helper =
      factory.create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbName)
          .callback(callback)
          .build()
      )
    val sqliteDb = helper.writableDatabase
    AppDatabase.MIGRATION_3_4.migrate(sqliteDb)

    val roomCursor = sqliteDb.query("SELECT status FROM rooms WHERE id = 1")
    assertTrue(roomCursor.moveToFirst())
    assertEquals("CLEANED", roomCursor.getString(0))
    roomCursor.close()

    val untouchedCursor = sqliteDb.query("SELECT status FROM rooms WHERE id = 2")
    assertTrue(untouchedCursor.moveToFirst())
    assertEquals("DIRTY", untouchedCursor.getString(0))
    untouchedCursor.close()

    val logCursor = sqliteDb.query("SELECT fromStatus, toStatus FROM status_logs WHERE id = 1")
    assertTrue(logCursor.moveToFirst())
    assertEquals("CLEANING", logCursor.getString(0))
    assertEquals("CLEANED", logCursor.getString(1))
    logCursor.close()

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  private fun runScreenState(doneCount: Int): RoomDetailUiState {
    val baseRoom =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first().first { it.name == "101" }
      }
    val steps =
      ChecklistTemplates.buildSteps(
        com.vivescriptsolutions.roomready.domain.ChecklistStepKey.standardTemplateKeys
      ).mapIndexed { index, step -> step.copy(isDone = index < doneCount) }
    return RoomDetailUiState(
      isLoading = false,
      room = baseRoom.copy(status = RoomStatus.CLEANING),
      latestChecklistRun =
        com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity(
          id = 7L,
          roomId = baseRoom.id,
          performedBy = "Amina Rahman",
          performedByRole = StaffRole.HOUSEKEEPER,
          startedAt = System.currentTimeMillis() - 150_000L,
          finishedAt = null,
          steps = steps,
        ),
      session =
        UserSession(
          role = StaffRole.HOUSEKEEPER,
          staffName = "Amina Rahman",
          isDemoMode = true,
          hasSeededDemo = true,
        ),
    )
  }

  @Test
  fun `run screen shows 8 rows with progress timer and finish disabled at 7 of 8`() {
    composeTestRule.setContent {
      RoomReadyTheme {
        ChecklistRunScreen(
          uiState = runScreenState(doneCount = 7),
          onBack = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onFinish = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("run_progress_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("run_elapsed_timer").assertIsDisplayed()
    repeat(8) { index ->
      composeTestRule.onNodeWithTag("run_steps_list")
        .performScrollToNode(hasTestTag("run_step_$index"))
      composeTestRule.onNodeWithTag("run_step_$index").assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("run_steps_list")
      .performScrollToNode(hasTestTag("run_finish_button"))
    composeTestRule.onNodeWithTag("run_finish_button").assertIsNotEnabled()
  }

  @Test
  fun `run screen finish enabled at 8 of 8`() {
    composeTestRule.setContent {
      RoomReadyTheme {
        ChecklistRunScreen(
          uiState = runScreenState(doneCount = 8),
          onBack = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onFinish = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("run_steps_list")
      .performScrollToNode(hasTestTag("run_finish_button"))
    composeTestRule.onNodeWithTag("run_finish_button").assertIsEnabled()
  }
}
