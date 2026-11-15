package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.repository.BannerTone
import com.vivescriptsolutions.roomready.data.repository.cleansPerPerson
import com.vivescriptsolutions.roomready.data.repository.isStuckCleaning
import com.vivescriptsolutions.roomready.data.repository.offlineBannerTone
import com.vivescriptsolutions.roomready.data.repository.readinessPercent
import com.vivescriptsolutions.roomready.data.repository.startOfDayMillis
import com.vivescriptsolutions.roomready.data.repository.stuckCleaningRooms
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DashboardStatsTest {
  @Test
  fun `start of day truncates to midnight`() {
    // 2026-11-18T15:30:00Z in millis.
    val now = 1_795_015_800_000L
    assertEquals(1_794_960_000_000L, startOfDayMillis(now, TimeZone.getTimeZone("UTC")))
  }

  @Test
  fun `start of day stays within the last 24h in any zone`() {
    val now = System.currentTimeMillis()
    val start = startOfDayMillis(now)
    assertTrue(start <= now)
    assertTrue(start > now - 24 * 60 * 60 * 1000L)
  }

  @Test
  fun `readiness percent rounds down and guards empty`() {
    assertEquals(25, readinessPercent(ready = 3, total = 12))
    assertEquals(100, readinessPercent(ready = 5, total = 5))
    assertEquals(0, readinessPercent(ready = 0, total = 0))
    assertEquals(0, readinessPercent(ready = 0, total = 9))
  }

  private fun log(by: String, at: Long): StatusLogEntity =
    StatusLogEntity(
      roomId = 1, roomName = "101",
      fromStatus = RoomStatus.CLEANING, toStatus = RoomStatus.CLEANED,
      changedBy = by, changedByRole = StaffRole.HOUSEKEEPER, changedAt = at,
    )

  @Test
  fun `cleans per person groups and orders by count then name`() {
    val rows =
      cleansPerPerson(
        listOf(
          log("Tariq Hasan", 3L), log("Amina Rahman", 1L),
          log("Amina Rahman", 2L), log("Maya Devi", 4L),
          log("Maya Devi", 5L),
        )
      )
    assertEquals(listOf("Amina Rahman" to 2, "Maya Devi" to 2, "Tariq Hasan" to 1),
      rows.map { it.name to it.count })
  }

  private fun room(status: RoomStatus, updatedAt: Long): RoomEntity =
    RoomEntity(
      name = "101", roomTypeId = 1, roomTypeName = "Deluxe King", floor = 1,
      status = status, assignedTo = "Amina Rahman", updatedAt = updatedAt,
    )

  @Test
  fun `stuck rule is cleaning older than 60 minutes`() {
    val now = 1_700_000_000_000L
    val minute = 60_000L
    assertTrue(isStuckCleaning(room(RoomStatus.CLEANING, now - 61 * minute), now))
    assertTrue(isStuckCleaning(room(RoomStatus.CLEANING, now - (60 * minute + 1)), now))
    assertFalse(isStuckCleaning(room(RoomStatus.CLEANING, now - 60 * minute), now))
    assertFalse(isStuckCleaning(room(RoomStatus.CLEANING, now - 59 * minute), now))
    assertFalse(isStuckCleaning(room(RoomStatus.DIRTY, now - 600 * minute), now))
    assertFalse(isStuckCleaning(room(RoomStatus.READY, now - 600 * minute), now))
  }

  @Test
  fun `stuck list filters and orders oldest first`() {
    val now = 1_700_000_000_000L
    val minute = 60_000L
    val rooms =
      listOf(
        room(RoomStatus.CLEANING, now - 61 * minute).copy(name = "newer"),
        room(RoomStatus.CLEANING, now - 30 * minute).copy(name = "fresh"),
        room(RoomStatus.CLEANING, now - 180 * minute).copy(name = "older"),
        room(RoomStatus.DIRTY, now - 180 * minute).copy(name = "dirty"),
      )
    assertEquals(listOf("older", "newer"), stuckCleaningRooms(rooms, now).map { it.name })
  }

  @Test
  fun `banner tone is positive when offline neutral when online`() {
    assertEquals(BannerTone.POSITIVE, offlineBannerTone(isOnline = false))
    assertEquals(BannerTone.NEUTRAL, offlineBannerTone(isOnline = true))
  }
}
