package com.vivescriptsolutions.roomready.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.KingBed
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.ui.screens.ChecklistRunScreen
import com.vivescriptsolutions.roomready.ui.screens.DashboardScreen
import com.vivescriptsolutions.roomready.ui.screens.MeScreen
import com.vivescriptsolutions.roomready.ui.screens.OnboardingScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomDetailScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomsBoardScreen
import com.vivescriptsolutions.roomready.ui.screens.TicketDetailScreen
import com.vivescriptsolutions.roomready.ui.screens.TicketsScreen
import com.vivescriptsolutions.roomready.ui.viewmodel.MainAppViewModel
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailViewModel
import com.vivescriptsolutions.roomready.ui.viewmodel.TicketDetailViewModel
import kotlinx.coroutines.launch

object Routes {
  const val ROOMS = "rooms"
  const val TICKETS = "tickets"
  const val DASHBOARD = "dashboard"
  const val ME = "me"
  const val ROOM_DETAIL = "room_detail/{roomId}"
  const val CHECKLIST_RUN = "checklist_run/{roomId}"
  const val TICKET_DETAIL = "ticket_detail/{ticketId}"

  fun roomDetail(roomId: Long): String = "room_detail/$roomId"

  fun checklistRun(roomId: Long): String = "checklist_run/$roomId"

  fun ticketDetail(ticketId: Long): String = "ticket_detail/$ticketId"
}

enum class BottomNavTab(
  val route: String,
  @StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  ROOMS(Routes.ROOMS, R.string.nav_rooms, Icons.Filled.KingBed, Icons.Outlined.KingBed),
  TICKETS(
    Routes.TICKETS,
    R.string.nav_tickets,
    Icons.Filled.ConfirmationNumber,
    Icons.Outlined.ConfirmationNumber,
  ),
  DASHBOARD(
    Routes.DASHBOARD,
    R.string.nav_dashboard,
    Icons.Filled.Assessment,
    Icons.Outlined.Assessment,
  ),
  ME(Routes.ME, R.string.nav_me, Icons.Filled.Person, Icons.Outlined.Person),
}

@Composable
fun RoomReadyApp(repository: HousekeepingRepository) {
  val mainViewModel: MainAppViewModel =
    viewModel(factory = MainAppViewModel.Factory(repository))
  val session by mainViewModel.sessionState.collectAsStateWithLifecycle()
  val isInitialized by mainViewModel.isAppInitialized.collectAsStateWithLifecycle()

  if (!isInitialized) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  if (!session.isOnboarded) {
    OnboardingScreen(
      staffRoster = session.staffRoster,
      initialRole = session.role,
      initialStaffName = session.staffName,
      onComplete = { role, staffName -> mainViewModel.saveRoleAndStaff(role, staffName) },
    )
    return
  }

  val navController = rememberNavController()
  val ticketsTabState by mainViewModel.ticketsTabState.collectAsStateWithLifecycle()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val showBottomBar =
    BottomNavTab.entries.any { tab ->
      currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  // Resolved via stringResource (not context.getString) so configuration
  // changes such as locale switches recompose with fresh values.
  val resetDoneMessage = stringResource(R.string.me_reset_done_snackbar)
  val runFinishedMessage = stringResource(R.string.run_finished_snackbar)

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (showBottomBar) {
        NavigationBar(
          windowInsets = WindowInsets.navigationBars,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
          BottomNavTab.entries.forEach { tab ->
            val selected =
              currentDestination?.hierarchy?.any { it.route == tab.route } == true
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(tab.route) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                if (tab == BottomNavTab.TICKETS && ticketsTabState.urgentOpenCount > 0) {
                  BadgedBox(
                    badge = {
                      Badge(modifier = Modifier.testTag("tickets_nav_urgent_badge")) {
                        Text(text = ticketsTabState.urgentOpenCount.toString())
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = label,
                    )
                  }
                } else {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                }
              },
              label = {
                Text(
                  text = label,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              alwaysShowLabel = true,
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.primary,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("bottom_nav_${tab.route}"),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = Routes.ROOMS,
      modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
      composable(Routes.ROOMS) {
        val roomsState by mainViewModel.roomsBoardState.collectAsStateWithLifecycle()
        RoomsBoardScreen(
          uiState = roomsState,
          onSelectFilter = mainViewModel::setFilter,
          onSearchQueryChange = mainViewModel::setSearchQuery,
          onRoomClick = { roomId -> navController.navigate(Routes.roomDetail(roomId)) },
          onProfileClick = {
            navController.navigate(Routes.ME) {
              popUpTo(navController.graph.findStartDestination().id) { saveState = true }
              launchSingleTop = true
              restoreState = true
            }
          },
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
      }

      composable(Routes.TICKETS) {
        TicketsScreen(
          uiState = ticketsTabState,
          onSelectFilter = mainViewModel::setTicketFilter,
          onCreateTicket = { roomId, title, description, priority, createdBy ->
            mainViewModel.createTicket(roomId, title, description, priority, createdBy)
          },
          onOpenTicket = { ticketId -> navController.navigate(Routes.ticketDetail(ticketId)) },
          onToggleTicketStatus = mainViewModel::toggleTicketStatus,
          onOpenRoom = { roomId -> navController.navigate(Routes.roomDetail(roomId)) },
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
      }

      composable(
        route = Routes.TICKET_DETAIL,
        arguments = listOf(navArgument("ticketId") { type = NavType.LongType }),
      ) { backStackEntry ->
        val ticketId = backStackEntry.arguments?.getLong("ticketId") ?: return@composable
        val ticketViewModel: TicketDetailViewModel =
          viewModel(
            key = "ticket_detail_$ticketId",
            factory = TicketDetailViewModel.Factory(ticketId, repository),
          )
        val ticketState by ticketViewModel.uiState.collectAsStateWithLifecycle()
        TicketDetailScreen(
          ticket = ticketState.ticket,
          staffName = ticketState.session.staffName,
          onBack = { navController.popBackStack() },
          onOpenRoom = { roomId -> navController.navigate(Routes.roomDetail(roomId)) },
          onMarkDone = ticketViewModel::markDone,
          onReopen = ticketViewModel::reopen,
        )
      }

      composable(Routes.DASHBOARD) {
        val dashboardState by mainViewModel.dashboardTabState.collectAsStateWithLifecycle()
        DashboardScreen(
          uiState = dashboardState,
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
      }

      composable(Routes.ME) {
        MeScreen(
          session = session,
          onSwitchRole = mainViewModel::switchRole,
          onSwitchStaffName = mainViewModel::switchStaffName,
          onResetDemoData = {
            mainViewModel.resetDemoData {
              coroutineScope.launch {
                snackbarHostState.showSnackbar(resetDoneMessage)
              }
            }
          },
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
      }

      composable(
        route = Routes.ROOM_DETAIL,
        arguments = listOf(navArgument("roomId") { type = NavType.LongType }),
      ) { backStackEntry ->
        val roomId = backStackEntry.arguments?.getLong("roomId") ?: return@composable
        val detailViewModel: RoomDetailViewModel =
          viewModel(
            key = "room_detail_$roomId",
            factory = RoomDetailViewModel.Factory(roomId, repository),
          )
        val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(detailState.actionError) {
          val error = detailState.actionError
          if (error != null) {
            snackbarHostState.showSnackbar(error)
            detailViewModel.clearActionError()
          }
        }
        RoomDetailScreen(
          uiState = detailState,
          onBack = { navController.popBackStack() },
          onTransition = detailViewModel::performTransition,
          onToggleStep = detailViewModel::toggleChecklistStep,
          onSaveStepNote = detailViewModel::updateChecklistStepNote,
          onCreateTicket = detailViewModel::createTicket,
          onToggleTicketStatus = detailViewModel::toggleTicketStatus,
          onOpenTicket = { ticketId -> navController.navigate(Routes.ticketDetail(ticketId)) },
          onOpenRun = { navController.navigate(Routes.checklistRun(roomId)) },
          onStartCleaning = { kind ->
            detailViewModel.startCleaning(kind) {
              navController.navigate(Routes.checklistRun(roomId))
            }
          },
        )
      }

      composable(
        route = Routes.CHECKLIST_RUN,
        arguments = listOf(navArgument("roomId") { type = NavType.LongType }),
      ) { backStackEntry ->
        val roomId = backStackEntry.arguments?.getLong("roomId") ?: return@composable
        val detailViewModel: RoomDetailViewModel =
          viewModel(
            key = "room_detail_$roomId",
            factory = RoomDetailViewModel.Factory(roomId, repository),
          )
        val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(detailState.actionError) {
          val error = detailState.actionError
          if (error != null) {
            snackbarHostState.showSnackbar(error)
            detailViewModel.clearActionError()
          }
        }
        ChecklistRunScreen(
          uiState = detailState,
          onBack = { navController.popBackStack() },
          onToggleStep = detailViewModel::toggleChecklistStep,
          onSaveStepNote = detailViewModel::updateChecklistStepNote,
          onOpenTicket = { ticketId -> navController.navigate(Routes.ticketDetail(ticketId)) },
          onFinish = {
            detailViewModel.finishCleaning {
              navController.popBackStack()
              coroutineScope.launch {
                snackbarHostState.showSnackbar(runFinishedMessage)
              }
            }
          },
        )
      }
    }
  }
}
