package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
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
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.ui.components.RoomStatusChip
import com.vivescriptsolutions.roomready.ui.screens.RoomDetailScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomsBoardScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailUiState
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomsBoardUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomReadyRobolectricTest {
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
  fun `app name matches RoomReady`() {
    assertEquals("RoomReady", context.getString(R.string.app_name))
  }

  @Test
  fun `bengali locale loads translated strings with intact placeholders`() {
    val bnConfig = android.content.res.Configuration(context.resources.configuration)
    bnConfig.setLocale(Locale("bn"))
    val bn = context.createConfigurationContext(bnConfig)
    assertEquals("রুম", bn.getString(R.string.nav_rooms))
    assertEquals("পরিষ্কার শুরু করুন", bn.getString(R.string.action_start_cleaning))
    assertEquals("নোংরা", bn.getString(R.string.status_dirty))
    assertEquals("রুম 101", bn.getString(R.string.room_title_format, "101"))
    assertEquals(
      "৮টির মধ্যে ৭টি ধাপ সম্পন্ন",
      bn.getString(R.string.checklist_progress_format, 7, 8),
    )
    // Default locale stays English.
    assertEquals("Rooms", context.getString(R.string.nav_rooms))
  }

  @Test
  fun `demo seed creates 3 room types with 8 steps and 12 rooms across all 5 statuses`() {
    runBlocking {
      val seeded = DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      assertTrue(seeded)

      val roomTypes = db.roomTypeDao().observeAll().first()
      assertEquals(3, roomTypes.size)
      assertEquals(
        listOf("Deluxe King", "Family Suite", "Garden Cottage"),
        roomTypes.map { it.name },
      )
      roomTypes.forEach { type ->
        assertEquals(8, type.checklistTemplateSteps.size)
      }

      val rooms = db.roomDao().observeAllRooms().first()
      assertEquals(12, rooms.size)

      val countsByStatus =
        RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }
      assertEquals(3, countsByStatus[RoomStatus.DIRTY])
      assertEquals(3, countsByStatus[RoomStatus.CLEANING])
      assertEquals(2, countsByStatus[RoomStatus.CLEANED])
      assertEquals(3, countsByStatus[RoomStatus.READY])
      assertEquals(1, countsByStatus[RoomStatus.OUT_OF_SERVICE])

      val tickets = db.ticketDao().observeAllTickets().first()
      assertEquals(2, tickets.size)
    }
  }

  @Test
  fun `status transition writes StatusLog and manages ChecklistRun`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val dirtyRoom = db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }

      val illegalResult =
        repository.transitionRoomStatus(
          roomId = dirtyRoom.id,
          toStatus = RoomStatus.READY,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(illegalResult.isFailure)

      val startResult =
        repository.transitionRoomStatus(
          roomId = dirtyRoom.id,
          toStatus = RoomStatus.CLEANING,
          staffName = "Amina Rahman",
          role = StaffRole.HOUSEKEEPER,
        )
      assertTrue(startResult.isSuccess)

      val updatedRoom = db.roomDao().getRoomById(dirtyRoom.id)!!
      assertEquals(RoomStatus.CLEANING, updatedRoom.status)
      assertEquals("Amina Rahman", updatedRoom.assignedTo)

      val run = db.checklistRunDao().getLatestForRoom(dirtyRoom.id)
      assertNotNull(run)
      assertEquals(8, run!!.steps.size)

      val logs = db.statusLogDao().observeLogsForRoom(dirtyRoom.id).first()
      assertEquals(1, logs.size)
      assertEquals(RoomStatus.DIRTY, logs[0].fromStatus)
      assertEquals(RoomStatus.CLEANING, logs[0].toStatus)
      assertEquals("Amina Rahman", logs[0].changedBy)
      assertEquals(StaffRole.HOUSEKEEPER, logs[0].changedByRole)
    }
  }

  @Test
  fun `real room migration 1 to 2 preserves existing rooms and adds notes column`() {
    val dbName = "migration_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS room_types (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              name TEXT NOT NULL,
              checklistTemplateSteps TEXT NOT NULL,
              isDemo INTEGER NOT NULL
            )
            """.trimIndent()
          )
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
              isDemo INTEGER NOT NULL
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
            "INSERT INTO room_types (id, name, checklistTemplateSteps, isDemo) VALUES (1, 'Deluxe King', '[]', 1)"
          )
          db.execSQL(
            "INSERT INTO rooms (id, name, roomTypeId, roomTypeName, floor, status, assignedTo, updatedAt, isDemo) VALUES (1, '101', 1, 'Deluxe King', 1, 'DIRTY', NULL, 1000, 1)"
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
    AppDatabase.MIGRATION_1_2.migrate(sqliteDb)

    val cursor = sqliteDb.query("SELECT name, notes FROM rooms WHERE id = 1")
    assertTrue(cursor.moveToFirst())
    assertEquals("101", cursor.getString(0))
    assertEquals("", cursor.getString(1))
    cursor.close()
    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `all 5 status chips render with labels and colors`() {
    composeTestRule.setContent {
      RoomReadyTheme {
        Column {
          RoomStatus.entries.forEach { status -> RoomStatusChip(status = status, large = true) }
        }
      }
    }

    composeTestRule.waitForIdle()
    RoomStatus.entries.forEach { status ->
      composeTestRule.onNodeWithTag("status_chip_${status.name.lowercase()}").assertIsDisplayed()
      composeTestRule.onNodeWithText(context.getString(status.labelRes)).assertIsDisplayed()
    }
  }

  @Test
  fun `rooms board renders filter chips and seeded rooms`() {
    val rooms =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first()
      }
    val counts = RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }

    composeTestRule.setContent {
      RoomReadyTheme {
        RoomsBoardScreen(
          uiState =
            RoomsBoardUiState(
              isLoading = false,
              session =
                UserSession(
                  role = StaffRole.HOUSEKEEPER,
                  staffName = "Amina Rahman",
                  isDemoMode = true,
                  hasSeededDemo = true,
                ),
              allRooms = rooms,
              filteredRooms = rooms,
              selectedFilter = null,
              searchQuery = "",
              statusCounts = counts,
              openTicketsByRoomId = emptyMap(),
            ),
          onSelectFilter = {},
          onSearchQueryChange = {},
          onRoomClick = {},
          onProfileClick = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("filter_chip_all").assertIsDisplayed()
    RoomStatus.entries.forEach { status ->
      composeTestRule.onNodeWithTag("filter_chip_${status.name.lowercase()}").assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("rooms_list").performScrollToNode(hasTestTag("room_card_101"))
    composeTestRule.onNodeWithText("Room 101").assertIsDisplayed()
    composeTestRule.onNodeWithTag("rooms_list").performScrollToNode(hasTestTag("room_card_204"))
    composeTestRule.onNodeWithText("Room 204").assertIsDisplayed()
  }

  @Test
  fun `room detail shows only legal next actions for dirty room`() {
    val dirtyRoom =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
      }
    val actions = RoomTransitionEngine.legalActionsFor(dirtyRoom.status, StaffRole.HOUSEKEEPER)

    composeTestRule.setContent {
      RoomReadyTheme {
        RoomDetailScreen(
          uiState =
            RoomDetailUiState(
              isLoading = false,
              room = dirtyRoom,
              legalActions = actions,
              session =
                UserSession(
                  role = StaffRole.HOUSEKEEPER,
                  staffName = "Amina Rahman",
                  isDemoMode = true,
                  hasSeededDemo = true,
                ),
            ),
          onBack = {},
          onTransition = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onCreateTicket = { _, _, _, _ -> },
          onToggleTicketStatus = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("action_button_cleaning").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_button_out_of_service").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_button_ready").assertDoesNotExist()
    composeTestRule.onNodeWithTag("action_button_cleaned").assertDoesNotExist()
  }
}
