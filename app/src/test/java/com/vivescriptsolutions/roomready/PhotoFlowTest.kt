package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.photos.PhotoStore
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.ui.screens.PhotoCaptureMode
import com.vivescriptsolutions.roomready.ui.screens.PhotoCaptureScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoFlowTest {
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

  private suspend fun roomWithCompleteRun(): Long {
    TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
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
    return dirtyRoom.id
  }

  @Test
  fun `finish requires photo or skip reason`() {
    runBlocking {
      val roomId = roomWithCompleteRun()
      val blocked =
        repository.finishChecklistRun(
          roomId = roomId, staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(blocked.isFailure)
      assertEquals(RoomStatus.CLEANING, db.roomDao().getRoomById(roomId)!!.status)

      val skipped = repository.skipRunPhoto(roomId, "Old demo phone, no camera")
      assertTrue(skipped.isSuccess)
      val finished =
        repository.finishChecklistRun(
          roomId = roomId, staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(finished.isSuccess)
      val run = db.checklistRunDao().getLatestForRoom(roomId)!!
      assertEquals("Old demo phone, no camera", run.photoSkippedReason)
    }
  }

  @Test
  fun `blank skip reason is rejected`() {
    runBlocking {
      val roomId = roomWithCompleteRun()
      assertTrue(repository.skipRunPhoto(roomId, "   ").isFailure)
      val run = db.checklistRunDao().getLatestForRoom(roomId)!!
      assertEquals(null, run.photoSkippedReason)
    }
  }

  @Test
  fun `photo refs persist on runs and tickets`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val room = db.roomDao().observeAllRooms().first().first { it.name == "101" }
      repository.startChecklistRun(
        roomId = room.id, kind = ChecklistRunKind.FULL,
        staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
      ).getOrThrow()
      repository.setRunPhoto(room.id, "run_1_1700000000000.jpg")
      assertEquals(
        "run_1_1700000000000.jpg",
        db.checklistRunDao().getLatestForRoom(room.id)!!.photoPath,
      )

      val ticketId =
        repository.createTicket(
          roomId = room.id, roomName = room.name, title = "Leak",
          description = "", priority = TicketPriority.NORMAL, createdBy = "Amina Rahman",
        )!!
      repository.setTicketPhoto(ticketId, "ticket_1_1700000000000.jpg")
      assertEquals(
        "ticket_1_1700000000000.jpg",
        db.ticketDao().getTicketById(ticketId)!!.photoPath,
      )
    }
  }

  @Test
  fun `photo store writes inside app-private files`() {
    val store = PhotoStore(context)
    val name = store.savePhoto("run", ByteArray(64) { it.toByte() })
    val file = store.photoFile(name)
    assertTrue(file.exists())
    assertTrue(file.absolutePath.startsWith(context.filesDir.absolutePath))
    assertTrue(name.startsWith("run_") && name.endsWith(".jpg"))
  }

  @Test
  fun `real room migration 6 to 7 adds photo columns`() {
    val dbName = "migration_6_7_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(6) {
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
              runKind TEXT NOT NULL DEFAULT 'FULL',
              returnReasonKey TEXT,
              returnNote TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tickets (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              roomId INTEGER NOT NULL,
              roomName TEXT NOT NULL,
              title TEXT NOT NULL,
              description TEXT NOT NULL DEFAULT '',
              priority TEXT NOT NULL,
              status TEXT NOT NULL,
              createdBy TEXT NOT NULL,
              createdAt INTEGER NOT NULL,
              resolvedBy TEXT,
              resolvedAt INTEGER,
              isDemo INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO checklist_runs (id, roomId, performedBy, performedByRole, startedAt, finishedAt, steps, runKind, returnReasonKey, returnNote) VALUES (1, 1, 'Amina Rahman', 'HOUSEKEEPER', 900, NULL, '[]', 'FULL', NULL, '')"
          )
          db.execSQL(
            "INSERT INTO tickets (id, roomId, roomName, title, description, priority, status, createdBy, createdAt, resolvedBy, resolvedAt, isDemo) VALUES (1, 1, '101', 'Leak', '', 'NORMAL', 'OPEN', 'Amina Rahman', 900, NULL, NULL, 0)"
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
    AppDatabase.MIGRATION_6_7.migrate(sqliteDb)

    val runCursor = sqliteDb.query("SELECT photoPath, photoSkippedReason FROM checklist_runs WHERE id = 1")
    assertTrue(runCursor.moveToFirst())
    assertTrue(runCursor.isNull(0))
    assertTrue(runCursor.isNull(1))
    runCursor.close()

    val ticketCursor = sqliteDb.query("SELECT photoPath FROM tickets WHERE id = 1")
    assertTrue(ticketCursor.moveToFirst())
    assertTrue(ticketCursor.isNull(0))
    ticketCursor.close()

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `denied camera shows rationale with skip requiring a reason`() {
    // Robolectric denies runtime permissions by default: this is the denied path.
    composeTestRule.setContent {
      RoomReadyTheme {
        PhotoCaptureScreen(
          mode = PhotoCaptureMode.Run(roomName = "101"),
          onPhotoTaken = {},
          onSkip = {},
          onBack = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("photo_rationale").assertIsDisplayed()
    composeTestRule.onNodeWithTag("photo_skip_reason_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("photo_skip_confirm_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("photo_skip_reason_input").performTextInput("No camera lens")
    composeTestRule.onNodeWithTag("photo_skip_confirm_button").assertIsEnabled()
  }
}
