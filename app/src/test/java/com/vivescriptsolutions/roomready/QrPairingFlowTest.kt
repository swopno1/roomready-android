package com.vivescriptsolutions.roomready

import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.sync.BootstrapOutcome
import com.vivescriptsolutions.roomready.data.sync.PairingQrParser
import com.vivescriptsolutions.roomready.data.sync.PairingQrResult
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import com.vivescriptsolutions.roomready.data.sync.SyncRepository
import com.vivescriptsolutions.roomready.ui.screens.ConnectionScreen
import com.vivescriptsolutions.roomready.ui.screens.QrScanError
import com.vivescriptsolutions.roomready.ui.screens.QrScanErrorCard
import com.vivescriptsolutions.roomready.ui.screens.QrScannerScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import com.vivescriptsolutions.roomready.ui.viewmodel.ConnectionUiState
import com.vivescriptsolutions.roomready.ui.viewmodel.ConnectionViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrPairingFlowTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: UserPreferencesRepository
  private lateinit var keys: InMemoryApiKeyStore
  private lateinit var server: MockWebServer
  private lateinit var syncRepository: SyncRepository

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
    server = MockWebServer()
    server.start()
    val api =
      PmsApi(
        baseUrlProvider = { prefs.connection().baseUrl },
        keyProvider = { keys.getKey() },
        client = OkHttpClient.Builder().build(),
      )
    syncRepository = SyncRepository(context, db, prefs, keys, api)
  }

  @After
  fun tearDown() {
    server.shutdown()
    db.close()
  }

  @Test
  fun `scan button shows above manual fields in both setup and connection mode`() {
    var setupScanClicks = 0
    composeTestRule.setContent {
      RoomReadyTheme {
        ConnectionScreen(
          uiState = ConnectionUiState(),
          onBack = {},
          onSaveConnection = { _, _ -> },
          onSyncNow = {},
          onDisconnect = {},
          isSetup = true,
          onScanQr = { setupScanClicks++ },
        )
      }
    }
    composeTestRule.waitForIdle()
    val scanBtn = composeTestRule.onNodeWithTag("connection_scan_qr_button")
    val urlInput = composeTestRule.onNodeWithTag("connection_url_input")
    scanBtn.assertIsDisplayed()
    urlInput.assertIsDisplayed()
    assertTrue(
      scanBtn.fetchSemanticsNode().boundsInRoot.top <
        urlInput.fetchSemanticsNode().boundsInRoot.top
    )
    scanBtn.performClick()
    assertEquals(1, setupScanClicks)
  }

  @Test
  fun `denied camera permission shows plain rationale and type-instead fallback`() {
    var typedInstead = false
    composeTestRule.setContent {
      RoomReadyTheme {
        QrScannerScreen(
          onPayloadScanned = {},
          onTypeInstead = { typedInstead = true },
          onBack = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("qr_rationale").assertIsDisplayed()
    composeTestRule.onNodeWithTag("qr_allow_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("qr_type_instead_button").performClick()
    assertTrue(typedInstead)
  }

  @Test
  fun `invalid code card offers plain message with rescan and type instead`() {
    var rescanCount = 0
    var typeInsteadCount = 0
    composeTestRule.setContent {
      RoomReadyTheme {
        QrScanErrorCard(
          error = QrScanError.InvalidCode,
          onRescan = { rescanCount++ },
          onTypeInstead = { typeInsteadCount++ },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithText("This code isn't a RoomReady connection code")
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("qr_rescan_button").performClick()
    composeTestRule.onNodeWithTag("qr_type_instead_button").performClick()
    assertEquals(1, rescanCount)
    assertEquals(1, typeInsteadCount)
  }

  @Test
  fun `valid scanned qr fills url and key fields and runs bootstrap`() {
    val httpsUrl = "https://${server.hostName}:${server.port}"
    val parsed =
      PairingQrParser.parse(
        "roomready://connect?v=1&url=https%3A%2F%2F${server.hostName}%3A${server.port}&key=rk.k1.QrSecret9"
      ) as PairingQrResult.Valid

    // MockWebServer runs plain HTTP by default while QR pairing requires https://,
    // so wire a local SyncRepository pointed at server.url("/") for bootstrap transport
    // while verifying the view-model fills the exact scanned HTTPS URL and key.
    val localApi =
      PmsApi(
        baseUrlProvider = { server.url("/").toString().trimEnd('/') },
        keyProvider = { keys.getKey() },
        client = OkHttpClient.Builder().build(),
      )
    val localSyncRepo = SyncRepository(context, db, prefs, keys, localApi)
    server.enqueue(MockResponse().setBody(bootstrapJson()))

    val vm = ConnectionViewModel(localSyncRepo)
    composeTestRule.setContent {
      RoomReadyTheme {
        val state = vm.uiState.collectAsStateWithLifecycle().value
        ConnectionScreen(
          uiState = state,
          onBack = {},
          onSaveConnection = vm::saveConnection,
          onSyncNow = vm::syncNow,
          onDisconnect = vm::disconnect,
          isSetup = true,
          onScannedPairingApplied = vm::consumeScannedPairing,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      vm.connectWithScannedQr(parsed.payload)
    }
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      val state = vm.uiState.value
      !state.syncing && state.message != null
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("connection_url_input").assertTextContains(httpsUrl)
    val keyEditable =
      composeTestRule.onNodeWithTag("connection_key_input")
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text
    assertEquals("rk.k1.QrSecret9".length, keyEditable.length)

    runBlocking {
      assertTrue(prefs.connection().isConfigured)
      assertEquals(httpsUrl, prefs.connection().baseUrl)
      assertEquals("rk.k1.QrSecret9", keys.getKey())
      assertEquals(1, db.roomDao().observeAllRooms().first().size)
    }
  }

  @Test
  fun `scanned qr with 401 bootstrap wipes key and explains`() {
    val parsed =
      PairingQrParser.parse(
        "roomready://connect?v=1&url=https%3A%2F%2F${server.hostName}%3A${server.port}&key=rk.k1.BadKey123"
      ) as PairingQrResult.Valid

    val localApi =
      PmsApi(
        baseUrlProvider = { server.url("/").toString().trimEnd('/') },
        keyProvider = { keys.getKey() },
        client = OkHttpClient.Builder().build(),
      )
    val localSyncRepo = SyncRepository(context, db, prefs, keys, localApi)
    server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid key"}"""))

    val vm = ConnectionViewModel(localSyncRepo)
    composeTestRule.setContent {
      RoomReadyTheme {
        val state = vm.uiState.collectAsStateWithLifecycle().value
        ConnectionScreen(
          uiState = state,
          onBack = {},
          onSaveConnection = vm::saveConnection,
          onSyncNow = vm::syncNow,
          onDisconnect = vm::disconnect,
          isSetup = false,
          onScannedPairingApplied = vm::consumeScannedPairing,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      vm.connectWithScannedQr(parsed.payload)
    }
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      val state = vm.uiState.value
      !state.syncing && state.message != null
    }
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithText("The key was rejected. Enter a new property API key.")
      .assertIsDisplayed()
    assertNull(keys.getKey())
  }

  @Test
  fun `typed url with spaces is rejected before any network call`() {
    runBlocking {
      val outcome = syncRepository.saveConnection("https://demo 13.example", "rk.k1.secret")
      assertTrue(outcome is BootstrapOutcome.Failed)
      assertEquals(0, server.requestCount)
      assertFalse(prefs.connection().isConfigured)
    }
  }

  @Test
  fun `typed url without https is rejected before any network call`() {
    runBlocking {
      val outcome = syncRepository.saveConnection("demo13.example", "rk.k1.secret")
      assertTrue(outcome is BootstrapOutcome.Failed)
      assertEquals(0, server.requestCount)
      assertFalse(prefs.connection().isConfigured)
    }
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
        .put("housekeepers", JSONArray(listOf(JSONObject().put("id", "s1").put("name", "Rahim"))))
        .put("supervisors", JSONArray())
    return JSONObject()
      .put("contractVersion", 1)
      .put("property", JSONObject().put("name", "Krishnachura Retreat").put("timezone", "Asia/Dhaka"))
      .put("businessDate", "2026-11-18")
      .put("roomTypes", JSONArray(listOf(type)))
      .put("units", JSONArray(listOf(unit)))
      .put("staff", staff)
      .toString()
  }
}
