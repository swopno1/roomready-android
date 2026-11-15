package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.connectivity.ConnectivityMonitor
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.sync.ApiKeyStore
import com.vivescriptsolutions.roomready.data.sync.BootstrapOutcome
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.SyncEngine
import com.vivescriptsolutions.roomready.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Replays a byte-identical bootstrap payload captured from a seeded
 * demo13 PMS (5 room types, 19 units; all floors/blocks null, most
 * housekeepingAt null) through the real parser and snapshot writer.
 * Regression cover for the founder's empty-rooms-board report: the
 * payload must yield 5 stored types, 19 stored units, and a board
 * state holding all 19.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RealPayloadReplayTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: UserPreferencesRepository
  private lateinit var keys: ApiKeyStore
  private lateinit var server: MockWebServer
  private lateinit var engine: SyncEngine
  private lateinit var repository: HousekeepingRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    prefs = UserPreferencesRepository(context)
    runBlocking { prefs.clearForTesting() }
    keys = InMemoryApiKeyStore()
    keys.saveKey("rk.key1.secret1")
    server = MockWebServer()
    server.start()
    val api =
      PmsApi(
        baseUrlProvider = { server.url("/").toString().trimEnd('/') },
        keyProvider = { keys.getKey() },
        client = OkHttpClient.Builder().build(),
      )
    engine = SyncEngine(db, api, keys, prefs)
    repository = HousekeepingRepository(db, prefs)
  }

  @After
  fun tearDown() {
    server.shutdown()
    db.close()
  }

  private fun realPayload(): String =
    javaClass.classLoader!!.getResourceAsStream("bootstrap-real.json")!!.bufferedReader().readText()

  private fun liveMode() {
    runBlocking {
      prefs.setConfigured(true)
      prefs.setBaseUrl(server.url("/").toString().trimEnd('/'))
    }
  }

  @Test
  fun `real bootstrap payload stores 5 types and 19 units`() {
    runBlocking {
      server.enqueue(MockResponse().setBody(realPayload()))
      val outcome = engine.bootstrap()
      assertTrue(outcome is BootstrapOutcome.Applied)

      assertEquals(5, db.roomTypeDao().count())
      assertEquals(19, db.roomDao().count())
      assertEquals(19, db.roomDao().observeAllRooms().first().size)
      // The dashboard reads the same table: its counts must agree.
      assertEquals(19, db.roomDao().observeStatusCounts().first().sumOf { it.count })
      // Staff from the same payload lands too.
      assertEquals(3, db.staffDao().observeAll().first().size)
    }
  }

  @Test
  fun `orphan unit for unknown type is skipped, snapshot still applies`() {
    runBlocking {
      liveMode()
      // The live property's inactive 6th type still has active units: the
      // PMS returns the units without their type. One such orphan must not
      // fail the whole snapshot.
      val payload = JSONObject(realPayload())
      val units = payload.getJSONArray("units")
      units.put(
        JSONObject(realPayload())
          .getJSONArray("units").getJSONObject(0).toString()
          .let { JSONObject(it).put("id", "orphan-unit").put("roomTypeId", "type-ghost") },
      )
      server.enqueue(MockResponse().setBody(payload.toString()))
      val outcome = engine.bootstrap()
      assertTrue(outcome is BootstrapOutcome.Applied)

      assertEquals(5, db.roomTypeDao().count())
      assertEquals(19, db.roomDao().count())
      assertNull(db.roomDao().getRoomByPmsUnitId("orphan-unit"))
      assertEquals(3, db.staffDao().observeAll().first().size)
    }
  }

  @Test
  fun `live orphan capture applies with orphans skipped`() {
    runBlocking {
      liveMode()
      // Captured from the local PMS after deactivating one room: 4 types,
      // 19 units, 3 of them orphaned. The old code failed this whole
      // snapshot (the founder's empty board); the fix skips the orphans.
      val payload =
        javaClass.classLoader!!.getResourceAsStream("bootstrap-orphan.json")!!.bufferedReader().readText()
      server.enqueue(MockResponse().setBody(payload))
      val outcome = engine.bootstrap()
      assertTrue(outcome is BootstrapOutcome.Applied)

      assertEquals(4, db.roomTypeDao().count())
      assertEquals(16, db.roomDao().count())
      assertEquals(16, db.roomDao().observeAllRooms().first().size)
    }
  }

  @Test
  @OptIn(ExperimentalCoroutinesApi::class)
  fun `real bootstrap payload fills the rooms board state`() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    try {
      runBlocking {
        liveMode()
        server.enqueue(MockResponse().setBody(realPayload()))
        assertTrue(engine.bootstrap() is BootstrapOutcome.Applied)

        val vm = MainAppViewModel(repository, ConnectivityMonitor(context))
        val board = withTimeout(15_000) { vm.roomsBoardState.first { !it.isLoading } }
        assertEquals(19, board.allRooms.size)
        assertEquals(19, board.filteredRooms.size)
        assertEquals(19, board.statusCounts.values.sum())
      }
    } finally {
      Dispatchers.resetMain()
    }
  }
}
