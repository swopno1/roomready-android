package com.vivescriptsolutions.roomready.data.sync

import com.vivescriptsolutions.roomready.domain.StaffRole
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

const val PMS_CONTRACT_VERSION = 1

class MalformedPayloadException(message: String) : Exception(message)

data class PmsRoomType(
  val id: String,
  val title: String,
  val checklistSteps: List<String>,
)

data class PmsUnit(
  val id: String,
  val roomTypeId: String,
  val unitNumber: String,
  val floor: Int?,
  val block: String?,
  val unitStatus: String,
  val housekeeping: String,
  val housekeepingAtMillis: Long,
  val departsToday: Boolean,
)

data class PmsStaffMember(
  val id: String,
  val name: String,
  val role: StaffRole,
)

data class BootstrapPayload(
  val contractVersion: Int,
  val propertyName: String,
  val propertyTimezone: String,
  val businessDate: String,
  val roomTypes: List<PmsRoomType>,
  val units: List<PmsUnit>,
  val staff: List<PmsStaffMember>,
)

data class RoomsPayload(
  val businessDate: String?,
  val units: List<PmsUnit>,
)

data class StatusResult(
  val unitId: String,
  val housekeeping: String,
  val housekeepingAtMillis: Long,
)

data class PmsTicket(
  val id: String,
  val unitId: String,
  val title: String,
  val description: String,
  val priority: String,
  val status: String,
  val createdBy: String,
  val createdAt: String,
  val doneBy: String?,
  val doneAt: String?,
)

private val UNIT_STATUSES =
  setOf("AVAILABLE", "OCCUPIED", "RESERVED", "CLEANING", "MAINTENANCE", "OUT_OF_ORDER")
private val HOUSEKEEPING_VALUES = setOf("DIRTY", "CLEAN", "INSPECTED")
private val BUSINESS_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

fun parseInstantMillis(raw: String): Long {
  val formats =
    listOf(
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      },
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
    )
  formats.forEach { format ->
    try {
      return format.parse(raw)?.time ?: throw ParseException(raw, 0)
    } catch (_: ParseException) {
      // try next format
    }
  }
  throw MalformedPayloadException("Bad timestamp: $raw")
}

private fun JSONObject.requiredString(key: String): String {
  val value = optString(key, null) ?: throw MalformedPayloadException("Missing '$key'")
  if (value.isBlank()) throw MalformedPayloadException("Blank '$key'")
  return value
}

private fun JSONObject.requiredObject(key: String): JSONObject =
  try {
    getJSONObject(key)
  } catch (_: JSONException) {
    throw MalformedPayloadException("Missing object '$key'")
  }

private fun JSONObject.requiredArray(key: String): JSONArray =
  try {
    getJSONArray(key)
  } catch (_: JSONException) {
    throw MalformedPayloadException("Missing array '$key'")
  }

fun parseUnit(json: JSONObject): PmsUnit {
  val unitStatus = json.requiredString("unitStatus")
  if (unitStatus !in UNIT_STATUSES) throw MalformedPayloadException("Unknown unitStatus: $unitStatus")
  val housekeeping = json.requiredString("housekeeping")
  if (housekeeping !in HOUSEKEEPING_VALUES) {
    throw MalformedPayloadException("Unknown housekeeping: $housekeeping")
  }
  return PmsUnit(
    id = json.requiredString("id"),
    roomTypeId = json.requiredString("roomTypeId"),
    unitNumber = json.requiredString("unitNumber"),
    floor =
      if (json.isNull("floor")) {
        null
      } else {
        (json.opt("floor") as? Number)?.toInt()
          ?: throw MalformedPayloadException("Bad floor value")
      },
    block = json.optString("block", null)?.takeIf { it.isNotBlank() },
    unitStatus = unitStatus,
    housekeeping = housekeeping,
    housekeepingAtMillis = parseInstantMillis(json.requiredString("housekeepingAt")),
    departsToday = json.optBoolean("departsToday", false),
  )
}

fun parseRoomType(json: JSONObject): PmsRoomType {
  val stepsJson = json.requiredArray("checklistSteps")
  val steps = List(stepsJson.length()) { stepsJson.optString(it) }
  if (steps.any { it.isBlank() }) throw MalformedPayloadException("Blank checklist step")
  return PmsRoomType(
    id = json.requiredString("id"),
    title = json.requiredString("title"),
    checklistSteps = steps,
  )
}

fun parseStaff(json: JSONObject): List<PmsStaffMember> {
  fun members(key: String, role: StaffRole): List<PmsStaffMember> {
    if (json.isNull(key)) return emptyList()
    val array = json.requiredArray(key)
    return List(array.length()) { index ->
      val member = array.optJSONObject(index)
        ?: throw MalformedPayloadException("Bad staff entry in '$key'")
      PmsStaffMember(
        id = member.requiredString("id"),
        name = member.requiredString("name"),
        role = role,
      )
    }
  }
  return members("housekeepers", StaffRole.HOUSEKEEPER) + members("supervisors", StaffRole.SUPERVISOR)
}

fun parseBootstrap(body: String): BootstrapPayload {
  val json =
    try {
      JSONObject(body)
    } catch (_: JSONException) {
      throw MalformedPayloadException("Response is not JSON")
    }
  if (!json.has("contractVersion")) throw MalformedPayloadException("Missing 'contractVersion'")
  val contractVersion = json.optInt("contractVersion", Int.MIN_VALUE)
  val property = json.requiredObject("property")
  val businessDate = json.requiredString("businessDate")
  if (!BUSINESS_DATE_REGEX.matches(businessDate)) {
    throw MalformedPayloadException("Bad businessDate: $businessDate")
  }
  val typesJson = json.requiredArray("roomTypes")
  val unitsJson = json.requiredArray("units")
  return BootstrapPayload(
    contractVersion = contractVersion,
    propertyName = property.requiredString("name"),
    propertyTimezone = property.requiredString("timezone"),
    businessDate = businessDate,
    roomTypes = List(typesJson.length()) { parseRoomType(typesJson.getJSONObject(it)) },
    units = List(unitsJson.length()) { parseUnit(unitsJson.getJSONObject(it)) },
    staff = if (json.isNull("staff")) emptyList() else parseStaff(json.requiredObject("staff")),
  )
}

fun parseRooms(body: String): RoomsPayload {
  val json =
    try {
      JSONObject(body)
    } catch (_: JSONException) {
      throw MalformedPayloadException("Response is not JSON")
    }
  val unitsJson = json.requiredArray("units")
  return RoomsPayload(
    businessDate = json.optString("businessDate", null)?.takeIf { it.isNotBlank() },
    units = List(unitsJson.length()) { parseUnit(unitsJson.getJSONObject(it)) },
  )
}

fun parseStatusResult(body: String): StatusResult {
  val json =
    try {
      JSONObject(body)
    } catch (_: JSONException) {
      throw MalformedPayloadException("Response is not JSON")
    }
  val housekeeping = json.requiredString("housekeeping")
  if (housekeeping !in HOUSEKEEPING_VALUES) {
    throw MalformedPayloadException("Unknown housekeeping: $housekeeping")
  }
  return StatusResult(
    unitId = json.requiredString("unitId"),
    housekeeping = housekeeping,
    housekeepingAtMillis = parseInstantMillis(json.requiredString("housekeepingAt")),
  )
}

fun parseTicket(json: JSONObject): PmsTicket =
  PmsTicket(
    id = json.requiredString("id"),
    unitId = json.requiredString("unitId"),
    title = json.requiredString("title"),
    description = json.optString("description", ""),
    priority = json.requiredString("priority"),
    status = json.requiredString("status"),
    createdBy = json.optString("createdBy", ""),
    createdAt = json.optString("createdAt", ""),
    doneBy = json.optString("doneBy", null)?.takeIf { it.isNotBlank() },
    doneAt = json.optString("doneAt", null)?.takeIf { it.isNotBlank() },
  )

fun parseSingleTicket(body: String): PmsTicket {
  val json =
    try {
      JSONObject(body)
    } catch (_: JSONException) {
      throw MalformedPayloadException("Response is not JSON")
    }
  return parseTicket(json)
}

fun parseTickets(body: String): List<PmsTicket> {
  val array =
    try {
      JSONArray(body)
    } catch (_: JSONException) {
      throw MalformedPayloadException("Response is not a JSON array")
    }
  return List(array.length()) { parseTicket(array.getJSONObject(it)) }
}

fun parseErrorMessage(body: String?): String? =
  if (body.isNullOrBlank()) {
    null
  } else {
    runCatching { JSONObject(body).optString("error", null)?.takeIf { it.isNotBlank() } }
      .getOrNull()
  }
