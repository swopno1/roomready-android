package com.vivescriptsolutions.roomready

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import androidx.work.NetworkType
import com.vivescriptsolutions.roomready.data.notifications.NotificationEvent
import com.vivescriptsolutions.roomready.data.notifications.RoomReadyNotifier
import com.vivescriptsolutions.roomready.data.notifications.STUCK_REMINDER_TAG
import com.vivescriptsolutions.roomready.data.notifications.StuckReminderWorker
import com.vivescriptsolutions.roomready.data.notifications.buildStuckReminderRequest
import com.vivescriptsolutions.roomready.data.notifications.RoomDeepLink
import com.vivescriptsolutions.roomready.data.notifications.findStuckRunIds
import com.vivescriptsolutions.roomready.data.notifications.notificationContent
import com.vivescriptsolutions.roomready.data.notifications.parseDeepLink
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepKey
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
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

/**
 * P9 local-notifications gate: trigger conditions, exact content,
 * denied-permission silence, and deep-link targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationFlowTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var received: MutableList<NotificationEvent>
  private lateinit var repository: HousekeepingRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    received = mutableListOf()
    repository =
      HousekeepingRepository(db, UserPreferencesRepository(context), onNotify = received::add)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `inspection return emits a room-returned event`() {
    runBlocking {
      val roomId = cleanedRoomWithFinishedRun()
      repository.inspectReturn(
        roomId = roomId, reason = ReturnReason.BATHROOM, note = "streaky mirror",
        staffName = "Karim", role = StaffRole.SUPERVISOR,
      ).getOrThrow()

      assertEquals(1, received.size)
      val event = received.single() as NotificationEvent.RoomReturned
      assertEquals(roomId, event.roomId)
      assertEquals("Bathroom: streaky mirror", event.reason)
    }
  }

  @Test
  fun `failed return emits nothing`() {
    runBlocking {
      val roomId = cleanedRoomWithFinishedRun()
      val result =
        repository.inspectReturn(
          roomId = roomId, reason = null, note = "x",
          staffName = "Karim", role = StaffRole.SUPERVISOR,
        )
      assertTrue(result.isFailure)
      assertTrue(received.isEmpty())
    }
  }

  @Test
  fun `urgent ticket creation emits an event, normal does not`() {
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val room = db.roomDao().observeAllRooms().first().first()
      val urgentId =
        repository.createTicket(
          roomId = room.id, roomName = room.name, title = "Burst pipe",
          description = "bathroom", priority = TicketPriority.URGENT, createdBy = "Amina Rahman",
        )!!
      repository.createTicket(
        roomId = room.id, roomName = room.name, title = "Dusty shelf",
        description = "", priority = TicketPriority.NORMAL, createdBy = "Amina Rahman",
      )

      assertEquals(1, received.size)
      val event = received.single() as NotificationEvent.UrgentTicket
      assertEquals(urgentId, event.ticketId)
      assertEquals(room.name, event.roomName)
      assertEquals("Burst pipe", event.title)
    }
  }

  @Test
  fun `stuck detection flags only unfinished runs over 60 minutes`() {
    val now = 1_700_000_000_000L
    val runs =
      listOf(
        run(id = 1, startedAt = now - 61 * 60_000L, finishedAt = null),
        run(id = 2, startedAt = now - 59 * 60_000L, finishedAt = null),
        run(id = 3, startedAt = now - 120 * 60_000L, finishedAt = now - 60 * 60_000L),
      )
    assertEquals(listOf(1L), findStuckRunIds(runs, now, alreadyNotified = emptySet()))
    assertEquals(
      emptyList<Long>(),
      findStuckRunIds(runs, now, alreadyNotified = setOf(1L)),
    )
  }

  @Test
  fun `stuck check notifies once per episode`() {
    runBlocking {
      val now = 1_800_000_000_000L
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val room = db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
      repository.startChecklistRun(
        roomId = room.id, kind = ChecklistRunKind.FULL,
        staffName = "Amina Rahman", role = StaffRole.HOUSEKEEPER,
        nowMillis = now - 61 * 60_000L,
      ).getOrThrow()

      fun stuckForRoom() =
        received.filterIsInstance<NotificationEvent.StuckCleaning>()
          .filter { it.roomId == room.id }

      repository.checkStuckCleans(now)
      val stuck = stuckForRoom()
      assertEquals(1, stuck.size)
      assertEquals(61L, stuck.single().elapsedMinutes)

      repository.checkStuckCleans(now + 60_000L)
      assertEquals(1, stuckForRoom().size)
    }
  }

  @Test
  fun `stuck reminder request repeats half-hourly with no constraints`() {
    val spec = buildStuckReminderRequest().workSpec
    assertEquals(30 * 60_000L, spec.intervalDuration)
    assertEquals(NetworkType.NOT_REQUIRED, spec.constraints.requiredNetworkType)
    assertEquals(StuckReminderWorker::class.java.name, spec.workerClassName)
    val request = buildStuckReminderRequest()
    assertTrue(request.tags.contains(STUCK_REMINDER_TAG))
  }

  @Test
  fun `notification content matches the spec shapes`() {
    val returned =
      notificationContent(
        context,
        NotificationEvent.RoomReturned(roomId = 5, roomName = "101", reason = "Bathroom"),
      )
    assertEquals("Room 101 needs re-cleaning", returned.title)
    assertEquals("Room 101 needs re-cleaning: Bathroom", returned.text)

    val urgent =
      notificationContent(
        context,
        NotificationEvent.UrgentTicket(ticketId = 7, roomId = 5, roomName = "101", title = "Burst pipe"),
      )
    assertEquals("Urgent issue in Room 101", urgent.title)
    assertEquals("Burst pipe", urgent.text)

    val stuck =
      notificationContent(
        context,
        NotificationEvent.StuckCleaning(roomId = 5, roomName = "101", elapsedMinutes = 75),
      )
    assertEquals("Room 101 is still cleaning", stuck.title)
  }

  @Test
  fun `denied notifications post nothing, granted posts with a deep link`() {
    val manager = context.getSystemService<NotificationManager>()!!
    val denied = RoomReadyNotifier(context, canPostCheck = { false })
    denied.ensureChannels()

    denied.notify(NotificationEvent.RoomReturned(roomId = 5, roomName = "101", reason = "Beds"))
    assertTrue(shadowOf(manager).allNotifications.isEmpty())

    val granted = RoomReadyNotifier(context, canPostCheck = { true })
    granted.notify(NotificationEvent.RoomReturned(roomId = 5, roomName = "101", reason = "Beds"))
    val posted = shadowOf(manager).allNotifications
    assertEquals(1, posted.size)
    assertEquals(
      "Room 101 needs re-cleaning: Beds",
      posted.single().extras.getString(Notification.EXTRA_TEXT),
    )
    val target = shadowOf(posted.single().contentIntent).savedIntent
    assertEquals(RoomDeepLink.Room(5), parseDeepLink(target))
  }

  @Test
  fun `ticket deep links parse to the ticket`() {
    val notifier = RoomReadyNotifier(context)
    val intent =
      notifier.deepLinkIntent(
        NotificationEvent.UrgentTicket(ticketId = 9, roomId = 5, roomName = "101", title = "x")
      )
    assertEquals(RoomDeepLink.Ticket(9), parseDeepLink(intent))
    assertNull(parseDeepLink(Intent()))
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

  private fun run(id: Long, startedAt: Long, finishedAt: Long?): ChecklistRunEntity =
    ChecklistRunEntity(
      id = id,
      roomId = id,
      performedBy = "Amina Rahman",
      performedByRole = StaffRole.HOUSEKEEPER,
      startedAt = startedAt,
      finishedAt = finishedAt,
      steps =
        ChecklistStepKey.standardTemplateKeys.mapIndexed { index, key ->
          ChecklistStepState(stepIndex = index, key = key, title = key, isDone = false, note = "")
        },
    )
}
