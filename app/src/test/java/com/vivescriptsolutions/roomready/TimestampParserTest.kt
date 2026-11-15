package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.data.sync.MalformedPayloadException
import com.vivescriptsolutions.roomready.data.sync.parseBootstrap
import com.vivescriptsolutions.roomready.data.sync.parseInstantMillis
import com.vivescriptsolutions.roomready.data.sync.parseStatusResult
import com.vivescriptsolutions.roomready.data.sync.parseUnit
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimestampParserTest {

  @Test
  fun `parses exact founder error timestamp with milliseconds and Z`() {
    val expected = Instant.parse("2026-11-07T05:16:50.651Z").toEpochMilli()
    assertEquals(1_794_028_610_651L, expected)
    assertEquals(expected, parseInstantMillis("2026-11-07T05:16:50.651Z"))
  }

  @Test
  fun `parses instant without fractional seconds and Z`() {
    val expected = Instant.parse("2026-11-07T05:16:50Z").toEpochMilli()
    assertEquals(expected, parseInstantMillis("2026-11-07T05:16:50Z"))
  }

  @Test
  fun `parses numeric offsets with and without fractional seconds`() {
    val baseNoFraction = Instant.parse("2026-11-07T05:16:50Z").toEpochMilli()
    val baseWithFraction = Instant.parse("2026-11-07T05:16:50.651Z").toEpochMilli()

    assertEquals(baseNoFraction, parseInstantMillis("2026-11-07T11:16:50+06:00"))
    assertEquals(baseWithFraction, parseInstantMillis("2026-11-07T11:16:50.651+06:00"))
    assertEquals(baseNoFraction, parseInstantMillis("2026-11-07T00:16:50-05:00"))
    assertEquals(baseWithFraction, parseInstantMillis("2026-11-07T00:16:50.651-05:00"))
  }

  @Test
  fun `rejects invalid instant strings cleanly`() {
    assertThrows(MalformedPayloadException::class.java) {
      parseInstantMillis("yesterday")
    }
    assertThrows(MalformedPayloadException::class.java) {
      parseInstantMillis("")
    }
  }

  @Test
  fun `parseUnit degrades unparseable housekeepingAt to null but rejects structural defects`() {
    val validUnit =
      JSONObject()
        .put("id", "u1")
        .put("roomTypeId", "t1")
        .put("unitNumber", "101")
        .put("floor", 1)
        .put("unitStatus", "AVAILABLE")
        .put("housekeeping", "CLEAN")
        .put("housekeepingAt", "2026-11-07T05:16:50.651Z")
        .put("departsToday", false)

    assertEquals(1_794_028_610_651L, parseUnit(validUnit).housekeepingAtMillis)

    ShadowLog.clear()
    val badTimestampUnit = JSONObject(validUnit.toString()).put("housekeepingAt", "not-a-timestamp")
    assertNull(parseUnit(badTimestampUnit).housekeepingAtMillis)
    val debugLogs = ShadowLog.getLogsForTag("SyncPayloads")
    assertTrue(debugLogs.any { it.msg.contains("housekeepingAt") && it.msg.contains("u1") })
    assertFalse(debugLogs.any { it.msg.contains("rk.") })

    val missingTimestampUnit = JSONObject(validUnit.toString()).apply { remove("housekeepingAt") }
    assertThrows(MalformedPayloadException::class.java) { parseUnit(missingTimestampUnit) }

    val wrongTypeUnit = JSONObject(validUnit.toString()).put("housekeepingAt", 12345)
    assertThrows(MalformedPayloadException::class.java) { parseUnit(wrongTypeUnit) }
  }

  @Test
  fun `parseStatusResult and parseBootstrap accept ISO-8601 instants and offsets`() {
    val statusBody =
      JSONObject()
        .put("unitId", "u1")
        .put("housekeeping", "INSPECTED")
        .put("housekeepingAt", "2026-11-07T11:16:50.651+06:00")
        .toString()
    assertEquals(1_794_028_610_651L, parseStatusResult(statusBody).housekeepingAtMillis)

    val badStatusBody =
      JSONObject()
        .put("unitId", "u1")
        .put("housekeeping", "INSPECTED")
        .put("housekeepingAt", "garbage")
        .toString()
    assertNull(parseStatusResult(badStatusBody).housekeepingAtMillis)

    val bootstrapBody =
      JSONObject()
        .put("contractVersion", 1)
        .put("property", JSONObject().put("name", "Retreat").put("timezone", "Asia/Dhaka"))
        .put("businessDate", "2026-11-07")
        .put(
          "roomTypes",
          JSONArray().put(
            JSONObject()
              .put("id", "t1")
              .put("title", "Villa")
              .put("checklistSteps", JSONArray().put("Step 1"))
          ),
        )
        .put(
          "units",
          JSONArray()
            .put(
              JSONObject()
                .put("id", "u1")
                .put("roomTypeId", "t1")
                .put("unitNumber", "01")
                .put("floor", 1)
                .put("unitStatus", "AVAILABLE")
                .put("housekeeping", "DIRTY")
                .put("housekeepingAt", "2026-11-07T05:16:50.651Z")
            )
            .put(
              JSONObject()
                .put("id", "u2")
                .put("roomTypeId", "t1")
                .put("unitNumber", "02")
                .put("floor", 1)
                .put("unitStatus", "AVAILABLE")
                .put("housekeeping", "CLEAN")
                .put("housekeepingAt", "bad-stamp")
            ),
        )
        .toString()

    val parsed = parseBootstrap(bootstrapBody)
    assertEquals(2, parsed.units.size)
    assertEquals(1_794_028_610_651L, parsed.units[0].housekeepingAtMillis)
    assertNull(parsed.units[1].housekeepingAtMillis)
  }
}
