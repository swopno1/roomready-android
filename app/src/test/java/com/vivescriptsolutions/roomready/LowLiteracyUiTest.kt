package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.local.AppDatabase
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.ui.locale.LocalBanglaDigits
import com.vivescriptsolutions.roomready.ui.screens.ChecklistRunScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomDetailScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomsBoardScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailUiState
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomsBoardUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P8 low-literacy gate: key screens survive 1.3x font scale, the
 * detail primary action sits in the bottom two-thirds without
 * scrolling, Bengali UI renders Bengali digits, and every clickable
 * carries visible text or a TalkBack label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowLiteracyUiTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var repository: HousekeepingRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repository = HousekeepingRepository(db, UserPreferencesRepository(context))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `font scale 1_3 keeps board nodes displayed`() {
    val rooms =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first()
      }
    val counts = RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }

    composeTestRule.setContent {
      val current = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(current.density, 1.3f)) {
        RoomReadyTheme {
          RoomsBoardScreen(
            uiState =
              RoomsBoardUiState(
                isLoading = false,
                session = demoSession(),
                allRooms = rooms,
                filteredRooms = rooms,
                selectedFilter = null,
                searchQuery = "",
                statusCounts = counts,
                openTicketsByRoomId = emptyMap(),
              ),
            onSelectFilter = {},
            onSearchQueryChange = {},
            onRoomClick = {},
            onProfileClick = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("filter_chip_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("rooms_list").performScrollToNode(hasTestTag("room_card_101"))
    composeTestRule.onNodeWithText("Room 101").assertIsDisplayed()
    composeTestRule.onNodeWithTag("rooms_list").performScrollToNode(hasTestTag("room_card_204"))
    composeTestRule.onNodeWithText("Room 204").assertIsDisplayed()
  }

  @Test
  fun `font scale 1_3 keeps detail actions reachable`() {
    val dirtyRoom = seededDirtyRoom()
    val actions = RoomTransitionEngine.legalActionsFor(dirtyRoom.status, StaffRole.HOUSEKEEPER)

    composeTestRule.setContent {
      val current = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(current.density, 1.3f)) {
        RoomReadyTheme {
          RoomDetailScreen(
            uiState =
              RoomDetailUiState(
                isLoading = false,
                room = dirtyRoom,
                legalActions = actions,
                session = demoSession(),
              ),
            onBack = {},
            onTransition = {},
            onToggleStep = {},
            onSaveStepNote = { _, _ -> },
            onCreateTicket = { _, _, _, _ -> },
            onToggleTicketStatus = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("room_detail_list")
      .performScrollToNode(hasTestTag("action_button_cleaning"))
    composeTestRule.onNodeWithTag("action_button_cleaning").assertIsDisplayed()
  }

  @Test
  fun `detail primary action sits in the bottom two thirds`() {
    val dirtyRoom = seededDirtyRoom()
    val actions = RoomTransitionEngine.legalActionsFor(dirtyRoom.status, StaffRole.HOUSEKEEPER)

    composeTestRule.setContent {
      RoomReadyTheme {
        RoomDetailScreen(
          uiState =
            RoomDetailUiState(
              isLoading = false,
              room = dirtyRoom,
              legalActions = actions,
              session = demoSession(),
            ),
          onBack = {},
          onTransition = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onCreateTicket = { _, _, _, _ -> },
          onToggleTicketStatus = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    val root = composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val button =
      composeTestRule.onNodeWithTag("action_button_cleaning", useUnmergedTree = true)
        .fetchSemanticsNode()
    val topFraction = button.boundsInRoot.top / root.size.height.toFloat()
    assertTrue("Primary action top at $topFraction of viewport", topFraction > 1f / 3f)
  }

  @Test
  fun `bengali UI renders bengali digits for room numbers`() {
    val rooms =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first()
      }
    val counts = RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalBanglaDigits provides true) {
        RoomReadyTheme {
          RoomsBoardScreen(
            uiState =
              RoomsBoardUiState(
                isLoading = false,
                session = demoSession(),
                allRooms = rooms,
                filteredRooms = rooms,
                selectedFilter = null,
                searchQuery = "",
                statusCounts = counts,
                openTicketsByRoomId = emptyMap(),
              ),
            onSelectFilter = {},
            onSearchQueryChange = {},
            onRoomClick = {},
            onProfileClick = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("rooms_list").performScrollToNode(hasTestTag("room_card_101"))
    // Resource strings stay English here (no bn context wrap); digits must map.
    composeTestRule.onNodeWithText("Room ১০১").assertIsDisplayed()
  }

  @Test
  fun `every clickable on the board carries text or a label`() {
    val (rooms, counts) = seededRoomsWithCounts()

    composeTestRule.setContent {
      RoomReadyTheme {
        RoomsBoardScreen(
          uiState =
            RoomsBoardUiState(
              isLoading = false,
              session = demoSession(),
              allRooms = rooms,
              filteredRooms = rooms,
              selectedFilter = null,
              searchQuery = "",
              statusCounts = counts,
              openTicketsByRoomId = emptyMap(),
            ),
          onSelectFilter = {},
          onSearchQueryChange = {},
          onRoomClick = {},
          onProfileClick = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    assertClickablesLabeled("board")
  }

  @Test
  fun `every clickable on the detail screen carries text or a label`() {
    val dirtyRoom = seededDirtyRoom()

    composeTestRule.setContent {
      RoomReadyTheme {
        RoomDetailScreen(
          uiState =
            RoomDetailUiState(
              isLoading = false,
              room = dirtyRoom,
              legalActions =
                RoomTransitionEngine.legalActionsFor(dirtyRoom.status, StaffRole.HOUSEKEEPER),
              session = demoSession(),
            ),
          onBack = {},
          onTransition = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onCreateTicket = { _, _, _, _ -> },
          onToggleTicketStatus = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    assertClickablesLabeled("detail")
  }

  @Test
  fun `every clickable on the run screen carries text or a label`() {
    val rooms =
      runBlocking {
        DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
        db.roomDao().observeAllRooms().first()
      }
    val cleaningRoom = rooms.first { it.status == RoomStatus.CLEANING }

    composeTestRule.setContent {
      RoomReadyTheme {
        ChecklistRunScreen(
          uiState =
            RoomDetailUiState(
              isLoading = false,
              room = cleaningRoom,
              session = demoSession(),
            ),
          onBack = {},
          onToggleStep = {},
          onSaveStepNote = { _, _ -> },
          onFinish = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    assertClickablesLabeled("run")
  }

  private fun seededRoomsWithCounts() =
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      val rooms = db.roomDao().observeAllRooms().first()
      val counts = RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }
      rooms to counts
    }

  private fun assertClickablesLabeled(screen: String) {
    val root = composeTestRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
    val bad = mutableListOf<String>()
    fun visit(node: SemanticsNode) {
      val hasClick = node.config.contains(SemanticsActions.OnClick)
      val isTextField = node.config.contains(SemanticsProperties.EditableText)
      if (hasClick && !isTextField) {
        val text =
          node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(separator = " ") { it.text }.orEmpty()
        val desc =
          node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(separator = " ").orEmpty()
        if (text.isBlank() && desc.isBlank()) {
          bad += node.config.getOrNull(SemanticsProperties.TestTag) ?: "(untagged)"
        }
      }
      node.children.forEach(::visit)
    }
    visit(root)
    assertTrue("$screen clickables without text or label: $bad", bad.isEmpty())
  }

  private fun demoSession(): UserSession =
    UserSession(
      role = StaffRole.HOUSEKEEPER,
      staffName = "Amina Rahman",
      isDemoMode = true,
      hasSeededDemo = true,
    )

  private fun seededDirtyRoom() =
    runBlocking {
      DemoSeeder.seedIfEmpty(db, nowMillis = 1_700_000_000_000L)
      db.roomDao().observeAllRooms().first().first { it.status == RoomStatus.DIRTY }
    }
}
