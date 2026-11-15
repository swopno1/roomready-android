package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.OutboxEntry
import com.vivescriptsolutions.roomready.data.local.OutboxKind
import com.vivescriptsolutions.roomready.data.local.StaffMemberEntity
import com.vivescriptsolutions.roomready.data.local.SyncStateEntity
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.sync.ApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.BootstrapOutcome
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.SyncEngine
import com.vivescriptsolutions.roomready.domain.AppLanguage
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.ui.screens.ConnectionScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import com.vivescriptsolutions.roomready.ui.viewmodel.ConnectionUiState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Production-shape coverage: unconfigured empty state, first sync into
 * the roster picker, disconnect-with-pending-work warning, the upgrade
 * purge (unconfigured purged, configured untouched), and the 8→9
 * migration that drops the last demo columns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductionShapeTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: UserPreferencesRepository
  private lateinit var repository: HousekeepingRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    prefs = UserPreferencesRepository(context)
    // The DataStore singleton outlives test methods: reset through the
    // API so configured-state assertions start clean.
    runBlocking { prefs.clearForTesting() }
    repository = HousekeepingRepository(db, prefs)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `fresh unconfigured install reads empty everywhere`() {
    runBlocking {
      repository.ensureProductionBaseline()
      assertTrue(db.roomDao().observeAllRooms().first().isEmpty())
      assertTrue(db.ticketDao().observeAllTickets().first().isEmpty())
      assertTrue(db.roomDao().observeStatusCounts().first().isEmpty())
      assertTrue(repository.sessionFlow.first().staffRoster.isEmpty())
      assertEquals(false, prefs.connection().isConfigured)
    }
  }

  @Test
  fun `upgrade purge empties operational tables but keeps photos and settings`() {
    runBlocking {
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      db.staffDao().insertAll(
        listOf(StaffMemberEntity(pmsId = "s1", name = "Rahim", role = StaffRole.HOUSEKEEPER))
      )
      db.outboxDao().insert(
        OutboxEntry(
          createdAt = 1_700_000_000_001L, kind = OutboxKind.STATUS,
          unitPmsId = "u1", ticketLocalId = null, payloadJson = "{}",
        )
      )
      db.syncStateDao().upsert(SyncStateEntity(propertyName = "Old"))
      prefs.saveRoleAndStaff(StaffRole.HOUSEKEEPER, "Amina Rahman")
      prefs.setAppLanguage(AppLanguage.BANGLA)
      prefs.markNotificationsAsked()
      val photo = File(File(context.filesDir, "photos"), "keep.jpg")
      photo.parentFile!!.mkdirs()
      photo.writeText("bytes")

      repository.ensureProductionBaseline()

      assertTrue(db.roomDao().observeAllRooms().first().isEmpty())
      assertTrue(db.roomTypeDao().observeAll().first().isEmpty())
      assertTrue(db.ticketDao().observeAllTickets().first().isEmpty())
      assertTrue(db.checklistRunDao().getAll().isEmpty())
      assertTrue(db.staffDao().getAll().isEmpty())
      assertEquals(0, db.outboxDao().count())
      assertNull(db.syncStateDao().get())
      assertTrue(photo.exists())
      val session = repository.sessionFlow.first()
      assertEquals(StaffRole.HOUSEKEEPER, session.role)
      assertEquals("Amina Rahman", session.staffName)
      assertEquals(AppLanguage.BANGLA, prefs.appLanguage())
      assertTrue(prefs.notificationsAsked())
    }
  }

  @Test
  fun `configured upgrade touches nothing`() {
    runBlocking {
      prefs.setConfigured(true)
      TestFixtures.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      db.outboxDao().insert(
        OutboxEntry(
          createdAt = 1_700_000_000_001L, kind = OutboxKind.STATUS,
          unitPmsId = "u1", ticketLocalId = null, payloadJson = "{}",
        )
      )

      repository.ensureProductionBaseline()

      assertEquals(12, db.roomDao().observeAllRooms().first().size)
      assertEquals(1, db.outboxDao().count())
      assertEquals(true, prefs.connection().isConfigured)
    }
  }

  @Test
  fun `first sync fills the roster picker and name selection persists`() {
    val keys =
      object : ApiKeyStore {
        var stored: String? = "rk.k1.s3cr3t"
        override fun getKey(): String? = stored
        override fun hasKey(): Boolean = !stored.isNullOrBlank()
        override fun saveKey(raw: String) { stored = raw }
        override fun clear() { stored = null }
      }
    val server = MockWebServer()
    server.start()
    try {
      runBlocking {
        prefs.setConfigured(true)
        prefs.setBaseUrl(server.url("/").toString().trimEnd('/'))
        val api =
          PmsApi(
            baseUrlProvider = { prefs.connection().baseUrl },
            keyProvider = { keys.getKey() },
            client = OkHttpClient.Builder().build(),
          )
        val engine = SyncEngine(db, api, keys, prefs)
        server.enqueue(MockResponse().setBody(bootstrapJson()))
        assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)

        assertEquals(listOf("Rahim"), repository.sessionFlow.first().staffRoster)
        repository.saveRoleAndStaff(StaffRole.HOUSEKEEPER, "Rahim")
        val session = repository.sessionFlow.first()
        assertEquals("Rahim", session.staffName)
        assertTrue(session.isOnboarded)
      }
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun `disconnect with pending outbox warns before calling back`() {
    var disconnected = false
    composeTestRule.setContent {
      RoomReadyTheme {
        ConnectionScreen(
          uiState = ConnectionUiState(isConfigured = true, pendingCount = 2),
          onBack = {},
          onSaveConnection = { _, _ -> },
          onSyncNow = {},
          onDisconnect = { disconnected = true },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("connection_disconnect_button").performClick()
    composeTestRule.onNodeWithTag("disconnect_confirm_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("There are 2 unsynced changes. They will be deleted and cannot be sent.")
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("disconnect_confirm_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(disconnected)
  }

  @Test
  fun `disconnect with empty outbox skips the warning`() {
    var disconnected = false
    composeTestRule.setContent {
      RoomReadyTheme {
        ConnectionScreen(
          uiState = ConnectionUiState(isConfigured = true, pendingCount = 0),
          onBack = {},
          onSaveConnection = { _, _ -> },
          onSyncNow = {},
          onDisconnect = { disconnected = true },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("connection_disconnect_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(disconnected)
  }

  @Test
  fun `migration 8 to 9 drops demo columns and keeps live rows`() {
    val dbName = "migration_8_9_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val callback =
      object : SupportSQLiteOpenHelper.Callback(8) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS room_types (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              name TEXT NOT NULL,
              checklistTemplateSteps TEXT NOT NULL,
              isDemo INTEGER NOT NULL,
              pmsTypeId TEXT
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
              notes TEXT NOT NULL,
              isOccupied INTEGER NOT NULL,
              pmsUnitId TEXT,
              block TEXT,
              departsToday INTEGER NOT NULL,
              pmsUnitStatus TEXT,
              pmsHousekeepingAt INTEGER,
              FOREIGN KEY(roomTypeId) REFERENCES room_types(id) ON UPDATE NO ACTION ON DELETE RESTRICT
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
              pmsTicketId TEXT,
              isDemo INTEGER NOT NULL,
              FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO room_types (id, name, checklistTemplateSteps, isDemo, pmsTypeId) " +
              "VALUES (1, 'Terracotta Villa', '[]', 0, 'type-1')"
          )
          db.execSQL(
            "INSERT INTO rooms (id, name, roomTypeId, roomTypeName, floor, status, assignedTo, " +
              "updatedAt, isDemo, notes, isOccupied, pmsUnitId, departsToday) " +
              "VALUES (1, '01', 1, 'Terracotta Villa', 1, 'DIRTY', NULL, 1000, 0, '', 0, 'unit-1', 0)"
          )
          db.execSQL(
            "INSERT INTO tickets (id, roomId, roomName, title, description, priority, status, " +
              "createdBy, createdAt, isDemo) " +
              "VALUES (1, 1, '01', 'Tap', '', 'URGENT', 'OPEN', 'Rahim', 800, 0)"
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
    AppDatabase.MIGRATION_8_9.migrate(sqliteDb)

    val roomCursor = sqliteDb.query("SELECT name, pmsUnitId, notes FROM rooms WHERE id = 1")
    assertTrue(roomCursor.moveToFirst())
    assertEquals("01", roomCursor.getString(0))
    assertEquals("unit-1", roomCursor.getString(1))
    assertEquals("", roomCursor.getString(2))
    roomCursor.close()

    val ticketCursor = sqliteDb.query("SELECT title, pmsTicketId FROM tickets WHERE id = 1")
    assertTrue(ticketCursor.moveToFirst())
    assertEquals("Tap", ticketCursor.getString(0))
    assertTrue(ticketCursor.isNull(1))
    ticketCursor.close()

    val typeCursor = sqliteDb.query("SELECT name, pmsTypeId FROM room_types WHERE id = 1")
    assertTrue(typeCursor.moveToFirst())
    assertEquals("Terracotta Villa", typeCursor.getString(0))
    assertEquals("type-1", typeCursor.getString(1))
    typeCursor.close()

    val cols = sqliteDb.query("PRAGMA table_info(rooms)")
    val names = mutableListOf<String>()
    while (cols.moveToNext()) names += cols.getString(1)
    cols.close()
    assertTrue(!names.contains("isDemo"))

    sqliteDb.close()
    context.deleteDatabase(dbName)
  }

  private fun bootstrapJson(): String {
    val steps =
      JSONArray(
        listOf(
          "Change bed linen", "Fresh towels & toiletries", "Vacuum & mop floors",
          "Bathroom deep clean", "Check minibar & restock", "AC, lights & sockets check",
          "Balcony / veranda tidy", "Final walkthrough",
        )
      )
    val type =
      JSONObject()
        .put("id", "type-1")
        .put("title", "Terracotta Villa")
        .put("checklistSteps", steps)
    val unit =
      JSONObject()
        .put("id", "unit-1")
        .put("roomTypeId", "type-1")
        .put("unitNumber", "01")
        .put("floor", 1)
        .put("block", JSONObject.NULL)
        .put("unitStatus", "AVAILABLE")
        .put("housekeeping", "DIRTY")
        .put("housekeepingAt", "2026-11-18T04:12:00Z")
        .put("departsToday", false)
    val staff =
      JSONObject()
        .put(
          "housekeepers",
          JSONArray(listOf(JSONObject().put("id", "s1").put("name", "Rahim"))),
        )
        .put("supervisors", JSONArray())
    return JSONObject()
      .put("contractVersion", 1)
      .put(
        "property",
        JSONObject().put("name", "Krishnachura Retreat").put("timezone", "Asia/Dhaka"),
      )
      .put("businessDate", "2026-11-18")
      .put("roomTypes", JSONArray(listOf(type)))
      .put("units", JSONArray(listOf(unit)))
      .put("staff", staff)
      .toString()
  }
}
