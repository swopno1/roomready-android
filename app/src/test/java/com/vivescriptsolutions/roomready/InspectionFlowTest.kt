package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
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
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.ui.screens.InspectionScreen
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
class InspectionFlowTest {
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

  private suspend fun cleanedRoomWithFinishedRun(): Long {
    DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
    val dirtyRoom = db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
    repository.startChecklistRun(
      roomId = dirtyRoom.id, kind = ChecklistRunKind.FULL,
      staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
    ).getOrThrow()
    repeat(8) { index ->
      repository.toggleChecklistStep(
        roomId = dirtyRoom.id, stepIndex = index,
        staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
      )
    }
    repository.skipRunPhoto(dirtyRoom.id, "test fixture without camera").getOrThrow()
    repository.finishChecklistRun(
      roomId = dirtyRoom.id, staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
    ).getOrThrow()
    return dirtyRoom.id
  }

  @Test
  fun `pass moves cleaned room to ready and logs actor`() {
    runBlocking {
      val roomId = cleanedRoomWithFinishedRun()
      val result =
        repository.inspectPass(roomId = roomId, staffName = "Karim", role = StaffRole.SUPERVISOR)
      assertTrue(result.isSuccess)
      assertEquals(RoomStatus.READY, db.roomDao().getRoomById(roomId)!!.status)
      val log =
        db.statusLogDao().observeLogsForRoom(roomId).first()
          .first { it.toStatus == RoomStatus.READY }
      assertEquals(RoomStatus.CLEANED, log.fromStatus)
      assertEquals("Karim", log.changedBy)
      // Finished run stays stamped; nothing reopens.
      assertNotNull(db.checklistRunDao().getLatestForRoom(roomId)!!.finishedAt)
    }
  }

  @Test
  fun `return requires a reason and reopens failed steps`() {
    runBlocking {
      val roomId = cleanedRoomWithFinishedRun()
      val blank =
        repository.inspectReturn(
          roomId = roomId, reason = null, note = "mirror",
          staffName = "Karim", role = StaffRole.SUPERVISOR,
        )
      assertTrue(blank.isFailure)
      assertEquals(RoomStatus.CLEANED, db.roomDao().getRoomById(roomId)!!.status)

      val result =
        repository.inspectReturn(
          roomId = roomId, reason = ReturnReason.BATHROOM, note = "streaky mirror",
          staffName = "Karim", role = StaffRole.SUPERVISOR,
        )
      assertTrue(result.isSuccess)
      assertEquals(RoomStatus.CLEANING, db.roomDao().getRoomById(roomId)!!.status)

      val run = db.checklistRunDao().getLatestForRoom(roomId)!!
      assertNull(run.finishedAt)
      assertEquals("BATHROOM", run.returnReasonKey)
      assertEquals("streaky mirror", run.returnNote)
      val byKey = run.steps.associateBy { it.key }
      assertEquals(false, byKey.getValue("bathroom").isDone)
      assertEquals(true, byKey.getValue("floors").isDone)

      val log =
        db.statusLogDao().observeLogsForRoom(roomId).first()
          .first { it.toStatus == RoomStatus.CLEANING }
      assertEquals(RoomStatus.CLEANED, log.fromStatus)
      assertEquals("Karim", log.changedBy)
      assertNotNull(log.reason)
    }
  }

  @Test
  fun `real room migration 5 to 6 adds return and log reason columns`() {
    val dbName = "migration_5_6_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(5) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS checklist_runs (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              roomId INTEGER NOT NULL,
              performedBy TEXT NOT NULL,
              performedByRole TEXT NOT NULL,
              startedAt INTEGER NOT NULL,
              finishedAt INTEGER,
              steps TEXT NOT NULL,
              runKind TEXT NOT NULL DEFAULT 'FULL'
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
            "INSERT INTO checklist_runs (id, roomId, performedBy, performedByRole, startedAt, finishedAt, steps, runKind) VALUES (1, 1, 'Amina Rahman', 'HOUSEKEEPER', 900, 1000, '[]', 'FULL')"
          )
          db.execSQL(
            "INSERT INTO status_logs (id, roomId, roomName, fromStatus, toStatus, changedBy, changedByRole, changedAt) VALUES (1, 1, '101', 'CLEANING', 'CLEANED', 'Amina Rahman', 'HOUSEKEEPER', 900)"
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
    AppDatabase.MIGRATION_5_6.migrate(sqliteDb)

    val runCursor = sqliteDb.query("SELECT performedBy, returnReasonKey, returnNote FROM checklist_runs WHERE id = 1")
    assertTrue(runCursor.moveToFirst())
    assertEquals("Amina Rahman", runCursor.getString(0))
    assertTrue(runCursor.isNull(1))
    assertEquals("", runCursor.getString(2))
    runCursor.close()

    val logCursor = sqliteDb.query("SELECT toStatus, reason FROM status_logs WHERE id = 1")
    assertTrue(logCursor.moveToFirst())
    assertEquals("CLEANED", logCursor.getString(0))
    assertTrue(logCursor.isNull(1))
    logCursor.close()

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `inspection screen shows read-only steps with pass and return`() {
    val (room, run) =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        val seeded = db.roomDao().observeAllRooms().first().first { it.name == "103" }
        seeded to db.checklistRunDao().getLatestForRoom(seeded.id)!!
      }
    composeTestRule.setContent {
      RoomReadyTheme {
        InspectionScreen(
          uiState =
            RoomDetailUiState(
              isLoading = false,
              room = room,
              latestChecklistRun = run,
              session =
                UserSession(
                  role = StaffRole.SUPERVISOR,
                  staffName = "Karim",
                  isDemoMode = true,
                  hasSeededDemo = true,
                ),
            ),
          onBack = {},
          onPass = {},
          onReturn = { _, _ -> },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("inspection_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inspection_step_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inspection_elapsed").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inspection_steps_list")
      .performScrollToNode(hasTestTag("inspection_pass_button"))
    composeTestRule.onNodeWithTag("inspection_pass_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inspection_steps_list")
      .performScrollToNode(hasTestTag("inspection_return_button"))
    composeTestRule.onNodeWithTag("inspection_return_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inspection_return_sheet").assertDoesNotExist()
  }
}
