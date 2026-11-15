package com.vivescriptsolutions.roomready.data.local

import androidx.room.TypeConverter
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import org.json.JSONArray
import org.json.JSONObject

class Converters {
  @TypeConverter
  fun fromRoomStatus(value: RoomStatus): String = value.name

  @TypeConverter
  fun toRoomStatus(value: String): RoomStatus = RoomStatus.valueOf(value)

  @TypeConverter
  fun fromStaffRole(value: StaffRole): String = value.name

  @TypeConverter
  fun toStaffRole(value: String): StaffRole = StaffRole.valueOf(value)

  @TypeConverter
  fun fromTicketPriority(value: TicketPriority): String = value.name

  @TypeConverter
  fun toTicketPriority(value: String): TicketPriority = TicketPriority.valueOf(value)

  @TypeConverter
  fun fromTicketStatus(value: TicketStatus): String = value.name

  @TypeConverter
  fun toTicketStatus(value: String): TicketStatus = TicketStatus.valueOf(value)

  @TypeConverter
  fun fromChecklistRunKind(value: ChecklistRunKind): String = value.name

  @TypeConverter
  fun toChecklistRunKind(value: String): ChecklistRunKind = ChecklistRunKind.valueOf(value)

  @TypeConverter
  fun fromOutboxKind(value: OutboxKind): String = value.name

  @TypeConverter
  fun toOutboxKind(value: String): OutboxKind = OutboxKind.valueOf(value)

  @TypeConverter
  fun fromStringList(value: List<String>): String {
    val array = JSONArray()
    value.forEach { array.put(it) }
    return array.toString()
  }

  @TypeConverter
  fun toStringList(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val array = JSONArray(value)
    return buildList(array.length()) {
      for (i in 0 until array.length()) {
        add(array.getString(i))
      }
    }
  }

  @TypeConverter
  fun fromChecklistStepList(value: List<ChecklistStepState>): String {
    val array = JSONArray()
    value.forEach { step ->
      val obj =
        JSONObject().apply {
          put("stepIndex", step.stepIndex)
          put("key", step.key)
          put("title", step.title)
          put("isDone", step.isDone)
          put("note", step.note)
        }
      array.put(obj)
    }
    return array.toString()
  }

  @TypeConverter
  fun toChecklistStepList(value: String): List<ChecklistStepState> {
    if (value.isBlank()) return emptyList()
    val array = JSONArray(value)
    return buildList(array.length()) {
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        add(
          ChecklistStepState(
            stepIndex = obj.getInt("stepIndex"),
            key = obj.optString("key", ""),
            title = obj.optString("title", ""),
            isDone = obj.optBoolean("isDone", false),
            note = obj.optString("note", ""),
          )
        )
      }
    }
  }
}
