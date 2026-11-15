package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.sync.ApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.BootstrapOutcome
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.SyncEngine
import com.vivescriptsolutions.roomready.data.sync.SyncOutcome
import com.vivescriptsolutions.roomready.data.sync.SyncRepository
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class InMemoryApiKeyStore : ApiKeyStore {
  private var key: String? = null

  override fun getKey(): String? = key

  override fun hasKey(): Boolean = key != null

  override fun saveKey(raw: String) {
    key = raw
  }

  override fun clear() {
    key = null
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncEngineTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: UserPreferencesRepository
  private lateinit var keys: InMemoryApiKeyStore
  private lateinit var server: MockWebServer
  private lateinit var api: PmsApi
  private lateinit var engine: SyncEngine
  private lateinit var repository: HousekeepingRepository

  private val unitA = "unit-aaaa"
  private val unitB = "unit-bbbb"
  private val typeVilla = "type-villa"

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    prefs = UserPreferencesRepository(context)
    keys = InMemoryApiKeyStore()
    keys.saveKey("rk.key1.secret1")
    server = MockWebServer()
    server.start()
    val client = OkHttpClient.Builder().build()
    api = PmsApi(
      baseUrlProvider = { server.url("/").toString().trimEnd('/') },
      keyProvider = { keys.getKey() },
      client = client,
    )
    engine = SyncEngine(db, api, keys, prefs)
    repository = HousekeepingRepository(db, prefs)
  }

  @After
  fun tearDown() {
    server.shutdown()
    db.close()
  }

  private fun unitJson(
    id: String,
    number: String,
    housekeeping: String,
    at: String = "2026-11-18T04:12:00Z",
    unitStatus: String = "AVAILABLE",
    departs: Boolean = false,
    floor: Int? = 1,
    block: String? = null,
  ): JSONObject =
    JSONObject()
      .put("id", id)
      .put("roomTypeId", typeVilla)
      .put("unitNumber", number)
      .put("floor", floor ?: JSONObject.NULL)
      .put("block", block ?: JSONObject.NULL)
      .put("unitStatus", unitStatus)
      .put("housekeeping", housekeeping)
      .put("housekeepingAt", at)
      .put("departsToday", departs)

  private fun bootstrapJson(
    units: List<JSONObject>,
    typeTitle: String = "Terracotta Villa",
    contractVersion: Any = 1,
  ): String {
    val steps =
      JSONArray(
        listOf(
          "Change bed linen", "Fresh towels & toiletries", "Vacuum & mop floors",
          "Bathroom deep clean", "Check minibar & restock", "AC, lights & sockets check",
          "Balcony / veranda tidy", "Final walkthrough",
        )
      )
    return JSONObject()
      .put("contractVersion", contractVersion)
      .put("property", JSONObject().put("name", "Krishnachura Retreat").put("timezone", "Asia/Dhaka"))
      .put("businessDate", "2026-11-18")
      .put("unknownTopLevel", "ignored")
      .put(
        "roomTypes",
        JSONArray()
          .put(
            JSONObject().put("id", typeVilla).put("title", typeTitle)
              .put("checklistSteps", steps).put("unknownNested", 42)
          ),
      )
      .put("units", JSONArray(units))
      .put(
        "staff",
        JSONObject()
          .put("housekeepers", JSONArray().put(JSONObject().put("id", "s1").put("name", "Rahim")))
          .put("supervisors", JSONArray().put(JSONObject().put("id", "s2").put("name", "Karim"))),
      )
      .toString()
  }

  private fun roomsJson(vararg units: JSONObject): String =
    JSONObject()
      .put("businessDate", "2026-11-18")
      .put("units", JSONArray(units.toList()))
      .toString()

  private fun liveMode() {
    runBlocking {
      prefs.setConfigured(true)
      prefs.setBaseUrl(server.url("/").toString().trimEnd('/'))
    }
  }

  @Test
  fun `bootstrap replaces snapshot wholesale and ignores unknown fields`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      assertEquals(12, db.roomDao().observeAllRooms().first().size)
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))

      val outcome = engine.bootstrap()
      assertTrue(outcome is BootstrapOutcome.Applied)

      val request = server.takeRequest()
      assertEquals("/api/housekeeping/bootstrap", request.path)
      assertEquals("Bearer rk.key1.secret1", request.getHeader("Authorization"))

      val rooms = db.roomDao().observeAllRooms().first()
      assertEquals(1, rooms.size)
      assertEquals("01", rooms[0].name)
      assertEquals(unitA, rooms[0].pmsUnitId)
      assertEquals(RoomStatus.DIRTY, rooms[0].status)
      val types = db.roomTypeDao().observeAll().first()
      assertEquals(1, types.size)
      assertEquals("Terracotta Villa", types[0].name)
      assertEquals(8, types[0].checklistTemplateSteps.size)
      assertEquals(
        listOf("Karim", "Rahim"),
        db.staffDao().observeAll().first().map { it.name }.sorted(),
      )
      val sync = db.syncStateDao().get()
      assertNotNull(sync)
      assertEquals("Krishnachura Retreat", sync!!.propertyName)
      assertEquals("Asia/Dhaka", sync.propertyTimezone)
      assertEquals("2026-11-18", sync.businessDate)
    }
  }

  @Test
  fun `bootstrap applies rename add and archive`() {
    runBlocking {
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)

      // Rename type, add unit B as CLEAN, archive unit A.
      server.enqueue(
        MockResponse().setBody(
          bootstrapJson(
            listOf(unitJson(unitB, "02", "CLEAN", at = "2026-11-18T06:00:00Z")),
            typeTitle = "Terracotta Villa Deluxe",
          )
        )
      )
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)

      val rooms = db.roomDao().observeAllRooms().first()
      assertEquals(listOf("02"), rooms.map { it.name })
      assertEquals(RoomStatus.CLEANED, rooms[0].status)
      assertEquals("Terracotta Villa Deluxe", db.roomTypeDao().observeAll().first()[0].name)
    }
  }

  @Test
  fun `bootstrap preserves in-progress cleans and outbox`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)
      val room = db.roomDao().observeAllRooms().first()[0]
      repository.startChecklistRun(
        roomId = room.id, kind = ChecklistRunKind.FULL,
        staffName = "Rahim", role = StaffRole.HOUSEKEEPER,
      ).getOrThrow()
      db.outboxDao().insert(
        com.vivescriptsolutions.roomready.data.local.OutboxEntry(
          createdAt = 1L,
          kind = com.vivescriptsolutions.roomready.data.local.OutboxKind.STATUS,
          unitPmsId = unitA,
          ticketLocalId = null,
          payloadJson = "{}",
        )
      )
      // Second snapshot flips the unit to CLEAN and adds another room.
      server.enqueue(
        MockResponse().setBody(
          bootstrapJson(
            listOf(
              unitJson(unitA, "01", "CLEAN", at = "2026-11-18T06:00:00Z"),
              unitJson(unitB, "02", "DIRTY"),
            )
          )
        )
      )
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)

      // In-progress room row untouched (still CLEANING with its run).
      val kept = db.roomDao().getRoomById(room.id)!!
      assertEquals(RoomStatus.CLEANING, kept.status)
      assertNull(db.checklistRunDao().getLatestForRoom(room.id)!!.finishedAt)
      assertEquals(1, db.outboxDao().count())
      // ...while the rest of the snapshot applied.
      assertEquals(2, db.roomDao().observeAllRooms().first().size)
    }
  }

  @Test
  fun `contract version mismatch changes nothing`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val before = db.roomDao().observeAllRooms().first().map { it.name to it.status }
      server.enqueue(
        MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")), contractVersion = 2))
      )
      val outcome = engine.bootstrap()
      assertTrue(outcome is BootstrapOutcome.UpdateRequired)
      assertEquals(before, db.roomDao().observeAllRooms().first().map { it.name to it.status })
      assertNull(db.syncStateDao().get()?.contractVersion)
    }
  }

  @Test
  fun `malformed bootstrap leaves state untouched`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val beforeCount = db.roomDao().observeAllRooms().first().size
      listOf(
        "{ not json",
        JSONObject().put("contractVersion", 1).toString(), // missing everything
        bootstrapJson(listOf(unitJson(unitA, "01", "SPARKLING"))), // bad enum
        bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY", at = "yesterday"))), // bad timestamp
      ).forEach { body ->
        server.enqueue(MockResponse().setBody(body))
        val outcome = engine.bootstrap()
        assertTrue("expected Failed for: $body", outcome is BootstrapOutcome.Failed)
        assertEquals(beforeCount, db.roomDao().observeAllRooms().first().size)
      }
    }
  }

  @Test
  fun `push order follows outbox and bodies match contract`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)
      val room = db.roomDao().observeAllRooms().first()[0]
      server.takeRequest() // consume bootstrap

      repository.startChecklistRun(
        roomId = room.id, kind = ChecklistRunKind.FULL,
        staffName = "Rahim", role = StaffRole.HOUSEKEEPER,
      ).getOrThrow()
      repeat(8) { i ->
        repository.toggleChecklistStep(room.id, i, "Rahim", StaffRole.HOUSEKEEPER)
      }
      repository.skipRunPhoto(room.id, "test").getOrThrow()
      repository.finishChecklistRun(room.id, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()
      repository.inspectPass(room.id, "Karim", StaffRole.SUPERVISOR).getOrThrow()

      server.enqueue(
        MockResponse().setBody(
          JSONObject().put("unitId", unitA).put("housekeeping", "CLEAN")
            .put("housekeepingAt", "2026-11-18T07:00:00Z").toString()
        )
      )
      server.enqueue(
        MockResponse().setBody(
          JSONObject().put("unitId", unitA).put("housekeeping", "INSPECTED")
            .put("housekeepingAt", "2026-11-18T07:30:00Z").toString()
        )
      )
      server.enqueue(MockResponse().setBody(roomsJson(unitJson(unitA, "01", "INSPECTED", at = "2026-11-18T07:30:00Z"))))
      server.enqueue(MockResponse().setBody(JSONArray().toString()))

      val outcome = engine.syncOnce()
      assertTrue(outcome is SyncOutcome.Completed)
      assertEquals(0, db.outboxDao().count())

      val first = server.takeRequest()
      assertEquals("POST", first.method)
      assertEquals("/api/housekeeping/rooms/$unitA/status", first.path)
      val firstBody = JSONObject(first.body.readUtf8())
      assertEquals("DIRTY", firstBody.getString("expected"))
      assertEquals("MARK_CLEAN", firstBody.getString("action"))
      assertEquals("Rahim", firstBody.getString("staffName"))

      val second = server.takeRequest()
      val secondBody = JSONObject(second.body.readUtf8())
      assertEquals("CLEAN", secondBody.getString("expected"))
      assertEquals("INSPECT_PASS", secondBody.getString("action"))
      assertEquals("Karim", secondBody.getString("staffName"))
    }
  }

  @Test
  fun `retry after failure keeps entry and drains on success`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      engine.bootstrap()
      server.takeRequest()
      val room = db.roomDao().observeAllRooms().first()[0]
      repository.startChecklistRun(
        room.id, ChecklistRunKind.FULL, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()
      repeat(8) { repository.toggleChecklistStep(room.id, it, "Rahim", StaffRole.HOUSEKEEPER) }
      repository.skipRunPhoto(room.id, "test").getOrThrow()
      repository.finishChecklistRun(room.id, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()

      server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
      val failed = engine.syncOnce() as SyncOutcome.Completed
      assertTrue(failed.needsRetry)
      assertEquals(1, db.outboxDao().count())

      server.enqueue(
        MockResponse().setBody(
          JSONObject().put("unitId", unitA).put("housekeeping", "CLEAN")
            .put("housekeepingAt", "2026-11-18T07:00:00Z").toString()
        )
      )
      server.enqueue(MockResponse().setBody(roomsJson(unitJson(unitA, "01", "CLEAN", at = "2026-11-18T07:00:00Z"))))
      server.enqueue(MockResponse().setBody(JSONArray().toString()))
      val done = engine.syncOnce()
      assertTrue(done is SyncOutcome.Completed)
      assertEquals(0, db.outboxDao().count())
    }
  }

  @Test
  fun `401 wipes the key and signals auth revoked`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      engine.bootstrap()
      server.takeRequest()
      val room = db.roomDao().observeAllRooms().first()[0]
      repository.startChecklistRun(
        room.id, ChecklistRunKind.FULL, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()
      repeat(8) { repository.toggleChecklistStep(room.id, it, "Rahim", StaffRole.HOUSEKEEPER) }
      repository.skipRunPhoto(room.id, "test").getOrThrow()
      repository.finishChecklistRun(room.id, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()
      assertTrue(keys.hasKey())

      server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"revoked"}"""))
      val outcome = engine.syncOnce()
      assertTrue(outcome is SyncOutcome.AuthRevoked)
      assertFalse(keys.hasKey())
      // Entry stays queued for after re-authentication.
      assertEquals(1, db.outboxDao().count())
    }
  }

  @Test
  fun `409 drops the entry and logs the conflict`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      engine.bootstrap()
      server.takeRequest()
      val room = db.roomDao().observeAllRooms().first()[0]
      repository.startChecklistRun(
        room.id, ChecklistRunKind.FULL, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()
      repeat(8) { repository.toggleChecklistStep(room.id, it, "Rahim", StaffRole.HOUSEKEEPER) }
      repository.skipRunPhoto(room.id, "test").getOrThrow()
      repository.finishChecklistRun(room.id, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()

      server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"stale"}"""))
      server.enqueue(MockResponse().setBody(roomsJson(unitJson(unitA, "01", "CLEAN", at = "2026-11-18T07:00:00Z"))))
      server.enqueue(MockResponse().setBody(JSONArray().toString()))
      engine.syncOnce()

      assertEquals(0, db.outboxDao().count())
      // Local CLEANED stands (server agrees); conflict is surfaced in StatusLog.
      assertEquals(RoomStatus.CLEANED, db.roomDao().getRoomById(room.id)!!.status)
      val logs = db.statusLogDao().observeLogsForRoom(room.id).first()
      assertTrue(logs.any { it.source == "pms" && (it.reason ?: "").contains("Conflict", ignoreCase = true) })
    }
  }

  @Test
  fun `merge matrix respects in-progress cleans and timestamps`() {
    runBlocking {
      liveMode()
      server.enqueue(
        MockResponse().setBody(
          bootstrapJson(
            listOf(
              unitJson(unitA, "01", "DIRTY", at = "2026-11-18T04:00:00Z"),
              unitJson(unitB, "02", "DIRTY", at = "2026-11-18T04:00:00Z", departs = true),
            )
          )
        )
      )
      engine.bootstrap()
      server.takeRequest()
      val rooms = db.roomDao().observeAllRooms().first()
      val roomA = rooms.first { it.pmsUnitId == unitA }
      val roomB = rooms.first { it.pmsUnitId == unitB }

      // Start a clean on A (in progress), leave B idle.
      repository.startChecklistRun(
        roomA.id, ChecklistRunKind.FULL, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()

      // Server A flipped CLEAN with a far-future timestamp (deterministically
      // newer than the local touch); B is departed-dirty.
      server.enqueue(
        MockResponse().setBody(
          roomsJson(
            unitJson(unitA, "01", "CLEAN", at = "2030-01-01T00:00:00Z"),
            unitJson(unitB, "02", "DIRTY", at = "2026-11-18T07:00:00Z", departs = true),
          )
        )
      )
      server.enqueue(MockResponse().setBody(JSONArray().toString()))
      engine.syncOnce()

      // A: PMS newer than local touch -> PMS wins even though cleaning started.
      assertEquals(RoomStatus.CLEANED, db.roomDao().getRoomById(roomA.id)!!.status)
      // B: departed + DIRTY with no clean in progress -> DIRTY (already DIRTY).
      assertEquals(RoomStatus.DIRTY, db.roomDao().getRoomById(roomB.id)!!.status)
      val logsA = db.statusLogDao().observeLogsForRoom(roomA.id).first()
      assertTrue(logsA.any { it.source == "pms" })
    }
  }

  @Test
  fun `real room migration 7 to 8 adds sync columns and tables`() {
    val dbName = "migration_7_8_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(7) {
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
              isDemo INTEGER NOT NULL,
              notes TEXT NOT NULL DEFAULT '',
              isOccupied INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tickets (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              roomId INTEGER NOT NULL,
              roomName TEXT NOT NULL,
              title TEXT NOT NULL,
              description TEXT NOT NULL,
              priority TEXT NOT NULL,
              status TEXT NOT NULL,
              createdBy TEXT NOT NULL,
              createdAt INTEGER NOT NULL,
              resolvedBy TEXT,
              resolvedAt INTEGER,
              photoPath TEXT,
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
              changedAt INTEGER NOT NULL,
              reason TEXT
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO rooms (id, name, roomTypeId, roomTypeName, floor, status, assignedTo, updatedAt, isDemo, notes, isOccupied) VALUES (1, '101', 1, 'Deluxe King', 1, 'DIRTY', NULL, 1000, 1, '', 0)"
          )
          db.execSQL(
            "INSERT INTO tickets (id, roomId, roomName, title, description, priority, status, createdBy, createdAt, isDemo) VALUES (1, 1, '101', 'Leaky tap', '', 'NORMAL', 'OPEN', 'Amina Rahman', 800, 1)"
          )
          db.execSQL(
            "INSERT INTO status_logs (id, roomId, roomName, fromStatus, toStatus, changedBy, changedByRole, changedAt, reason) VALUES (1, 1, '101', 'CLEANING', 'CLEANED', 'Amina Rahman', 'HOUSEKEEPER', 900, NULL)"
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
    AppDatabase.MIGRATION_7_8.migrate(sqliteDb)

    val roomCursor =
      sqliteDb.query("SELECT name, pmsUnitId, departsToday, pmsUnitStatus FROM rooms WHERE id = 1")
    assertTrue(roomCursor.moveToFirst())
    assertEquals("101", roomCursor.getString(0))
    assertTrue(roomCursor.isNull(1))
    assertEquals(0, roomCursor.getInt(2))
    assertTrue(roomCursor.isNull(3))
    roomCursor.close()

    val logCursor = sqliteDb.query("SELECT source FROM status_logs WHERE id = 1")
    assertTrue(logCursor.moveToFirst())
    assertEquals("local", logCursor.getString(0))
    logCursor.close()

    val ticketCursor = sqliteDb.query("SELECT title, pmsTicketId FROM tickets WHERE id = 1")
    assertTrue(ticketCursor.moveToFirst())
    assertEquals("Leaky tap", ticketCursor.getString(0))
    assertTrue(ticketCursor.isNull(1))
    ticketCursor.close()

    val tables = sqliteDb.query(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('staff_members', 'outbox_entries', 'sync_state')"
    )
    val found = mutableListOf<String>()
    while (tables.moveToNext()) found += tables.getString(0)
    tables.close()
    assertEquals(listOf("outbox_entries", "staff_members", "sync_state"), found.sorted())

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `live session uses synced roster and business-day start`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)
      server.takeRequest()

      val session = repository.sessionFlow.first()
      assertEquals(listOf("Karim", "Rahim"), session.staffRoster)

      // Asia/Dhaka (UTC+6) midnight 2026-11-18 = 2026-11-17T18:00:00Z.
      assertEquals(1_794_938_400_000L, repository.todayStartMillis(1_795_015_800_000L))
    }
  }

  @Test
  fun `local newer cleaning wins over stale server state`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY", at = "2026-11-18T04:00:00Z")))))
      engine.bootstrap()
      server.takeRequest()
      val room = db.roomDao().observeAllRooms().first()[0]
      repository.startChecklistRun(
        room.id, ChecklistRunKind.FULL, "Rahim", StaffRole.HOUSEKEEPER).getOrThrow()
      // Stale server timestamp (2020) is older than any local touch.
      server.enqueue(MockResponse().setBody(roomsJson(unitJson(unitA, "01", "CLEAN", at = "2020-01-01T00:00:00Z"))))
      server.enqueue(MockResponse().setBody(JSONArray().toString()))
      engine.syncOnce()

      assertEquals(RoomStatus.CLEANING, db.roomDao().getRoomById(room.id)!!.status)
      val logs = db.statusLogDao().observeLogsForRoom(room.id).first()
      assertTrue(logs.any { it.source == "local" })
    }
  }

  @Test
  fun `disconnect wipes key and clears the whole snapshot`() {
    runBlocking {
      liveMode()
      server.enqueue(MockResponse().setBody(bootstrapJson(listOf(unitJson(unitA, "01", "DIRTY")))))
      assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)
      server.takeRequest()
      val room = db.roomDao().observeAllRooms().first()[0]
      repository.createTicket(
        room.id, room.name, "Tap", "Leak", TicketPriority.NORMAL, "Rahim")
      assertEquals(1, db.outboxDao().count())

      SyncRepository(context, db, prefs, keys, api).disconnect()

      assertEquals(false, prefs.connection().isConfigured)
      assertFalse(keys.hasKey())
      assertTrue(db.roomDao().observeAllRooms().first().isEmpty())
      assertEquals(0, db.outboxDao().count())
      assertEquals(0, db.staffDao().getAll().size)
      assertNull(db.syncStateDao().get())
    }
  }
}
