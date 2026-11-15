package com.vivescriptsolutions.roomready

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.sync.ApiOutcome
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * P10 airplane-mode pass. Proves two things: (1) the simulated device
 * really has no network (airplane flag on, no active network), and
 * (2) every core flow still succeeds end to end while the sync
 * transport stays silent (NotConfigured on all six endpoints without
 * opening a socket — Demo mode has no URL or key).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AirplaneModeTest {
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
  fun `airplane mode still runs every core flow`() {
    enableAirplaneMode()
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)

      // Full turnover: start, 8 steps, skip photo with reason, finish.
      val room = db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
      repository.startChecklistRun(
        roomId = room.id, kind = ChecklistRunKind.FULL,
        staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
      ).getOrThrow()
      repeat(8) { index ->
        repository.toggleChecklistStep(
          roomId = room.id, stepIndex = index,
          staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
        )
      }
      repository.skipRunPhoto(room.id, "no camera on this phone").getOrThrow()
      repository.finishChecklistRun(
        roomId = room.id, staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
      ).getOrThrow()
      assertEquals(RoomStatus.CLEANED, db.roomDao().getRoomById(room.id)!!.status)

      // Inspection return with reason reopens the failed steps.
      repository.inspectReturn(
        roomId = room.id, reason = ReturnReason.BATHROOM, note = "",
        staffName = "Karim", role = StaffRole.SUPERVISOR,
      ).getOrThrow()
      assertEquals(RoomStatus.CLEANING, db.roomDao().getRoomById(room.id)!!.status)

      // Ticket lifecycle + out-of-service marking + dashboard reads.
      val ticketId =
        repository.createTicket(
          roomId = room.id, roomName = room.name, title = "Leak",
          description = "tap", priority = TicketPriority.URGENT, createdBy = "Amina Rahman",
        )!!
      repository.toggleTicketStatus(
        db.ticketDao().getTicketById(ticketId)!!, "Amina Rahman")
      assertEquals(TicketStatus.DONE, db.ticketDao().getTicketById(ticketId)!!.status)
      val readyRoom =
        db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.READY }
      repository.transitionRoomStatus(
        roomId = readyRoom.id, toStatus = RoomStatus.OUT_OF_SERVICE,
        staffName = "Karim", role = StaffRole.SUPERVISOR,
      ).getOrThrow()
      assertTrue(db.roomDao().observeAllRooms().first().size == 12)
      assertTrue(db.statusLogDao().observeLogsForRoom(room.id).first().isNotEmpty())
    }
  }

  @Test
  fun `demo transport stays silent on all six endpoints`() {
    enableAirplaneMode()
    runBlocking {
      // Demo-mode providers: no URL, no key — exactly what the app
      // holds when the user never configured Live mode.
      val api = PmsApi(baseUrlProvider = { null }, keyProvider = { null })
      assertTrue(api.bootstrap() is ApiOutcome.NotConfigured)
      assertTrue(api.refreshRooms() is ApiOutcome.NotConfigured)
      assertTrue(
        api.postStatus("u", "DIRTY", "MARK_CLEAN", "Amina Rahman", null)
          is ApiOutcome.NotConfigured
      )
      assertTrue(api.getOpenTickets() is ApiOutcome.NotConfigured)
      assertTrue(
        api.postTicket("u", "t", "d", "URGENT", "Amina Rahman") is ApiOutcome.NotConfigured
      )
      assertTrue(
        api.patchTicket("t", "DONE", "Karim") is ApiOutcome.NotConfigured
      )
    }
  }

  private fun enableAirplaneMode() {
    ShadowSettings.setAirplaneMode(true)
    val manager = context.getSystemService<ConnectivityManager>()!!
    shadowOf(manager).setActiveNetworkInfo(null)
    shadowOf(manager).setDefaultNetworkActive(false)
    assertEquals(
      1,
      Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0),
    )
    assertNull(manager.activeNetworkInfo)
  }
}
