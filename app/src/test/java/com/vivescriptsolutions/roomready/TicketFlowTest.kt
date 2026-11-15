package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.repository.urgentOpenCount
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import com.vivescriptsolutions.roomready.ui.screens.ChecklistRunScreen
import com.vivescriptsolutions.roomready.ui.screens.TicketDetailScreen
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
class TicketFlowTest {
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
  fun `create done reopen cycle records who and when`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val room = db.roomDao().observeAllRooms().first().first { it.name == "101" }

      repository.createTicket(
        roomId = room.id,
        roomName = room.name,
        title = "Leaking tap",
        description = "Bathroom sink drips steadily",
        priority = TicketPriority.URGENT,
        createdBy = "Amina Rahman",
      )
      val created =
        db.ticketDao().observeTicketsForRoom(room.id).first().first { it.title == "Leaking tap" }
      assertEquals("Bathroom sink drips steadily", created.description)
      assertEquals(TicketStatus.OPEN, created.status)
      assertNull(created.resolvedBy)
      assertNull(created.resolvedAt)

      repository.resolveTicket(created.id, resolvedBy = "Tariq Hasan")
      val done = db.ticketDao().observeTicketsForRoom(room.id).first().first { it.id == created.id }
      assertEquals(TicketStatus.DONE, done.status)
      assertEquals("Tariq Hasan", done.resolvedBy)
      assertNotNull(done.resolvedAt)

      repository.reopenTicket(created.id)
      val reopened =
        db.ticketDao().observeTicketsForRoom(room.id).first().first { it.id == created.id }
      assertEquals(TicketStatus.OPEN, reopened.status)
      assertNull(reopened.resolvedBy)
      assertNull(reopened.resolvedAt)
    }
  }

  @Test
  fun `urgent badge count tracks open urgent tickets`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      // Seeded: one URGENT open ticket (room 204).
      assertEquals(1, urgentOpenCount(db.ticketDao().observeAllTickets().first()))

      val room = db.roomDao().observeAllRooms().first().first { it.name == "101" }
      repository.createTicket(
        roomId = room.id,
        roomName = room.name,
        title = "No hot water",
        description = "",
        priority = TicketPriority.URGENT,
        createdBy = "Amina Rahman",
      )
      assertEquals(2, urgentOpenCount(db.ticketDao().observeAllTickets().first()))

      val added =
        db.ticketDao().observeTicketsForRoom(room.id).first().first { it.title == "No hot water" }
      repository.resolveTicket(added.id, resolvedBy = "Tariq Hasan")
      assertEquals(1, urgentOpenCount(db.ticketDao().observeAllTickets().first()))
    }
  }

  @Test
  fun `real room migration 4 to 5 adds ticket resolution columns`() {
    val dbName = "migration_4_5_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(4) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tickets (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              roomId INTEGER NOT NULL,
              roomName TEXT NOT NULL,
              title TEXT NOT NULL,
              priority TEXT NOT NULL,
              status TEXT NOT NULL,
              createdBy TEXT NOT NULL,
              createdAt INTEGER NOT NULL,
              isDemo INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO tickets (id, roomId, roomName, title, priority, status, createdBy, createdAt, isDemo) VALUES (1, 5, '204', 'AC leak', 'URGENT', 'OPEN', 'Rafiq Islam', 1000, 1)"
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
    AppDatabase.MIGRATION_4_5.migrate(sqliteDb)

    val cursor = sqliteDb.query("SELECT title, description, resolvedBy, resolvedAt FROM tickets WHERE id = 1")
    assertTrue(cursor.moveToFirst())
    assertEquals("AC leak", cursor.getString(0))
    assertEquals("", cursor.getString(1))
    assertTrue(cursor.isNull(2))
    assertTrue(cursor.isNull(3))
    cursor.close()

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `run screen shows urgent ticket banner when cleaning room has one`() {
    val roomId = 9L
    val urgent =
      TicketEntity(
        id = 3L,
        roomId = roomId,
        roomName = "102",
        title = "AC dripping",
        description = "",
        priority = TicketPriority.URGENT,
        status = TicketStatus.OPEN,
        createdBy = "Rafiq Islam",
        createdAt = 1_700_000_000_000L,
      )
    val room =
      runBlocking {
        TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first().first { it.name == "102" }
      }
    composeTestRule.setContent {
      RoomReadyTheme {
        ChecklistRunScreen(
          uiState =
            RoomDetailUiState(
              isLoading = false,
              room = room,
              latestChecklistRun =
                runBlocking { db.checklistRunDao().getLatestForRoom(room.id) },
              tickets = listOf(urgent),
              session =
                UserSession(
                  role = StaffRole.HOUSEKEEPER,
                  staffName = "Amina Rahman",
                ),
            ),
          onBack = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onFinish = {},
          onOpenTicket = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("run_urgent_ticket_banner").assertIsDisplayed()
  }

  @Test
  fun `ticket detail shows done action for open ticket`() {
    val ticket =
      TicketEntity(
        id = 3L,
        roomId = 9L,
        roomName = "102",
        title = "AC dripping",
        description = "Water pools under the unit",
        priority = TicketPriority.URGENT,
        status = TicketStatus.OPEN,
        createdBy = "Rafiq Islam",
        createdAt = 1_700_000_000_000L,
      )
    composeTestRule.setContent {
      RoomReadyTheme {
        TicketDetailScreen(
          ticket = ticket,
          staffName = "Amina Rahman",
          onBack = {},
          onOpenRoom = {},
          onMarkDone = {},
          onReopen = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ticket_detail_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ticket_mark_done_button").assertIsDisplayed()
  }
}
