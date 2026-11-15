package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.repository.cleansPerPerson
import com.vivescriptsolutions.roomready.data.repository.stuckCleaningRooms
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import com.vivescriptsolutions.roomready.ui.screens.OfflineProofBanner
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
class DashboardFlowTest {
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
  fun `status counts match seeded fixture`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val counts =
        db.roomDao().observeStatusCounts().first().associate { it.status to it.count }
      assertEquals(3, counts[RoomStatus.DIRTY])
      assertEquals(3, counts[RoomStatus.CLEANING])
      assertEquals(2, counts[RoomStatus.CLEANED])
      assertEquals(3, counts[RoomStatus.READY])
      assertEquals(1, counts[RoomStatus.OUT_OF_SERVICE])
    }
  }

  @Test
  fun `cleans today counts transitions since midnight with per-person list`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val now = System.currentTimeMillis()
      val since = com.vivescriptsolutions.roomready.data.repository.startOfDayMillis(now)
      val hour = 60 * 60 * 1000L
      fun log(by: String, to: RoomStatus, at: Long) =
        StatusLogEntity(
          roomId = 1, roomName = "101",
          fromStatus = RoomStatus.CLEANING, toStatus = to,
          changedBy = by, changedByRole = StaffRole.HOUSEKEEPER, changedAt = at,
        )
      db.statusLogDao().insertAll(
        listOf(
          log("Amina Rahman", RoomStatus.CLEANED, since + hour),
          log("Amina Rahman", RoomStatus.CLEANED, since + 2 * hour),
          log("Tariq Hasan", RoomStatus.CLEANED, since + 3 * hour),
          log("Maya Devi", RoomStatus.CLEANED, since - hour),
          log("Amina Rahman", RoomStatus.READY, since + 4 * hour),
        )
      )
      val today = db.statusLogDao().observeLogsSince(RoomStatus.CLEANED, since).first()
      assertEquals(3, today.size)
      assertEquals(
        listOf("Amina Rahman" to 2, "Tariq Hasan" to 1),
        cleansPerPerson(today).map { it.name to it.count },
      )
    }
  }

  @Test
  fun `open tickets group by priority and stuck rooms surface`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val byPriority =
        db.ticketDao().observeCountByPriority(TicketStatus.OPEN).first()
          .associate { it.priority to it.count }
      assertEquals(1, byPriority[TicketPriority.URGENT])
      assertEquals(1, byPriority[TicketPriority.NORMAL])
      assertEquals(null, byPriority[TicketPriority.LOW])

      val now = System.currentTimeMillis()
      val stuck = stuckCleaningRooms(db.roomDao().observeRoomsByStatus(RoomStatus.CLEANING).first(), now)
      // Seeded CLEANING rooms carry fixed 2023 timestamps, so all 3 are stuck
      // against a live clock; ordering is oldest-first.
      assertEquals(3, stuck.size)
      assertTrue(stuck.all { it.status == RoomStatus.CLEANING })
      assertTrue(stuck[0].updatedAt <= stuck[1].updatedAt)
    }
  }

  @Test
  fun `banner reads offline-green when offline and neutral online`() {
    composeTestRule.setContent {
      RoomReadyTheme { OfflineProofBanner(isOnline = false) }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Offline — everything still works").assertIsDisplayed()
  }

  @Test
  fun `banner reads online when connected`() {
    composeTestRule.setContent {
      RoomReadyTheme { OfflineProofBanner(isOnline = true) }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Online").assertIsDisplayed()
  }
}
