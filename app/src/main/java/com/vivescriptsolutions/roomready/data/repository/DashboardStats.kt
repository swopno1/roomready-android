package com.vivescriptsolutions.roomready.data.repository

import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.domain.RoomStatus
import java.util.Calendar
import java.util.TimeZone

data class PersonCount(val name: String, val count: Int)

enum class BannerTone { POSITIVE, NEUTRAL }

fun offlineBannerTone(isOnline: Boolean): BannerTone =
  if (isOnline) BannerTone.NEUTRAL else BannerTone.POSITIVE

fun startOfDayMillis(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
  val cal =
    Calendar.getInstance(timeZone).apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
  return cal.timeInMillis
}

fun readinessPercent(ready: Int, total: Int): Int =
  if (total <= 0) 0 else (ready * 100 / total).coerceIn(0, 100)

fun cleansPerPerson(logs: List<StatusLogEntity>): List<PersonCount> =
  logs.groupingBy { it.changedBy }
    .eachCount()
    .map { (name, count) -> PersonCount(name, count) }
    .sortedWith(compareByDescending<PersonCount> { it.count }.thenBy { it.name })

const val STUCK_CLEANING_MINUTES = 60L

fun isStuckCleaning(room: RoomEntity, nowMillis: Long): Boolean =
  room.status == RoomStatus.CLEANING &&
    nowMillis - room.updatedAt > STUCK_CLEANING_MINUTES * 60 * 1000L

fun stuckCleaningRooms(rooms: List<RoomEntity>, nowMillis: Long): List<RoomEntity> =
  rooms.filter { isStuckCleaning(it, nowMillis) }.sortedBy { it.updatedAt }
