package com.vivescriptsolutions.roomready.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.vivescriptsolutions.roomready.data.notifications.RoomDeepLink
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.data.sync.SyncRepository
import com.vivescriptsolutions.roomready.domain.AppLanguage
import com.vivescriptsolutions.roomready.ui.locale.LocalBanglaDigits
import com.vivescriptsolutions.roomready.ui.locale.findActivity
import com.vivescriptsolutions.roomready.ui.locale.isBanglaUi
import com.vivescriptsolutions.roomready.ui.locale.localizedDigits
import com.vivescriptsolutions.roomready.ui.screens.ChecklistRunScreen
import com.vivescriptsolutions.roomready.ui.screens.ConnectionScreen
import com.vivescriptsolutions.roomready.ui.screens.DashboardScreen
import com.vivescriptsolutions.roomready.ui.screens.InspectionScreen
import com.vivescriptsolutions.roomready.ui.screens.MeScreen
import com.vivescriptsolutions.roomready.ui.screens.OnboardingScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomDetailScreen
import com.vivescriptsolutions.roomready.ui.screens.RoomsBoardScreen
import com.vivescriptsolutions.roomready.data.connectivity.ConnectivityMonitor
import com.vivescriptsolutions.roomready.data.photos.PhotoShare
import com.vivescriptsolutions.roomready.data.photos.PhotoStore
import com.vivescriptsolutions.roomready.ui.screens.PhotoCaptureMode
import com.vivescriptsolutions.roomready.ui.screens.PhotoCaptureScreen
import com.vivescriptsolutions.roomready.ui.screens.TicketDetailScreen
import com.vivescriptsolutions.roomready.ui.screens.TicketsScreen
import com.vivescriptsolutions.roomready.ui.viewmodel.ConnectionViewModel
import com.vivescriptsolutions.roomready.ui.viewmodel.MainAppViewModel
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailViewModel
import com.vivescriptsolutions.roomready.ui.viewmodel.TicketDetailViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

object Routes {
  const val ROOMS = "rooms"
  const val TICKETS = "tickets"
  const val DASHBOARD = "dashboard"
  const val ME = "me"
  const val CONNECTION = "connection"
  const val ROOM_DETAIL = "room_detail/{roomId}"
  const val CHECKLIST_RUN = "checklist_run/{roomId}"
  const val TICKET_DETAIL = "ticket_detail/{ticketId}"
  const val INSPECTION = "inspection/{roomId}"
  const val PHOTO_CAPTURE = "photo_capture/{mode}/{refId}"

  fun roomDetail(roomId: Long): String = "room_detail/$roomId"

  fun inspection(roomId: Long): String = "inspection/$roomId"

  fun photoCaptureRun(roomId: Long): String = "photo_capture/run/$roomId"

  fun photoCaptureTicket(ticketId: Long): String = "photo_capture/ticket/$ticketId"

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
fun RoomReadyApp(
  repository: HousekeepingRepository,
  connectivityMonitor: ConnectivityMonitor,
  syncRepository: SyncRepository,
  deepLinkChannel: Channel<RoomDeepLink> = Channel(Channel.BUFFERED),
) {
  val mainViewModel: MainAppViewModel =
    viewModel(factory = MainAppViewModel.Factory(repository, connectivityMonitor))
  val session by mainViewModel.sessionState.collectAsStateWithLifecycle()
  val isInitialized by mainViewModel.isAppInitialized.collectAsStateWithLifecycle()
  val appLanguage by mainViewModel.appLanguage.collectAsStateWithLifecycle()
  val isConfigured by mainViewModel.isPmsConfigured.collectAsStateWithLifecycle()

  CompositionLocalProvider(LocalBanglaDigits provides isBanglaUi(appLanguage)) {
    RoomReadyContent(
      repository = repository,
      connectivityMonitor = connectivityMonitor,
      syncRepository = syncRepository,
      mainViewModel = mainViewModel,
      session = session,
      isInitialized = isInitialized,
      isConfigured = isConfigured,
      appLanguage = appLanguage,
      deepLinkChannel = deepLinkChannel,
    )
  }
}

@Composable
private fun RoomReadyContent(
  repository: HousekeepingRepository,
  connectivityMonitor: ConnectivityMonitor,
  syncRepository: SyncRepository,
  mainViewModel: MainAppViewModel,
  session: UserSession,
  isInitialized: Boolean,
  isConfigured: Boolean,
  appLanguage: AppLanguage,
  deepLinkChannel: Channel<RoomDeepLink>,
) {
  if (!isInitialized) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  // Production shape: no connection → setup only. Onboarding (role +
  // roster/free-text name) follows the first successful sync.
  if (!isConfigured) {
    val connectionViewModel: ConnectionViewModel =
      viewModel(factory = ConnectionViewModel.Factory(syncRepository))
    val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    ConnectionScreen(
      uiState = connectionState,
      onBack = {},
      onSaveConnection = connectionViewModel::saveConnection,
      onSyncNow = connectionViewModel::syncNow,
      onDisconnect = connectionViewModel::disconnect,
      isSetup = true,
    )
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
  val context = LocalContext.current
  val photoStore = remember(context) { PhotoStore(context) }
  fun sharePhoto(name: String) = PhotoShare.sharePhoto(context, photoStore, name)
  // Resolved via stringResource (not context.getString) so configuration
  // changes such as locale switches recompose with fresh values.
  val runFinishedMessage = stringResource(R.string.run_finished_snackbar)
  val unauthorizedMessage = stringResource(R.string.connection_unauthorized)

  // Contract: 401 wipes the key and returns to the Connection screen.
  // The flag is cleared on navigate so the next revocation re-fires.
  LaunchedEffect(navController) {
    syncRepository.authRevoked.collect { revoked ->
      if (revoked) {
        syncRepository.clearAuthRevoked()
        coroutineScope.launch {
          snackbarHostState.showSnackbar(unauthorizedMessage)
        }
        navController.navigate(Routes.CONNECTION) { launchSingleTop = true }
      }
    }
  }

  // Notification taps land on the relevant room or ticket. The channel
  // delivers each tap once; unknown rooms/tickets fail navigation
  // silently by popping back (detail screens handle nulls).
  LaunchedEffect(navController, deepLinkChannel) {
    for (link in deepLinkChannel) {
      when (link) {
        is RoomDeepLink.Room -> navController.navigate(Routes.roomDetail(link.roomId))
        is RoomDeepLink.Ticket -> navController.navigate(Routes.ticketDetail(link.ticketId))
      }
    }
  }

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
                        Text(text = localizedDigits(ticketsTabState.urgentOpenCount.toString()))
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
          onAddPhoto = { navController.navigate(Routes.photoCaptureTicket(ticketId)) },
          onSharePhoto = ::sharePhoto,
        )
      }

      composable(Routes.DASHBOARD) {
        val dashboardState by mainViewModel.dashboardTabState.collectAsStateWithLifecycle()
        DashboardScreen(
          uiState = dashboardState,
          onSelectStatus = mainViewModel::setFilter,
          onOpenRooms = {
            navController.navigate(Routes.ROOMS) {
              popUpTo(navController.graph.findStartDestination().id) { saveState = true }
              launchSingleTop = true
              restoreState = true
            }
          },
          onOpenRoom = { roomId -> navController.navigate(Routes.roomDetail(roomId)) },
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
      }

      composable(Routes.ME) {
        MeScreen(
          session = session,
          onSwitchRole = mainViewModel::switchRole,
          onSwitchStaffName = mainViewModel::switchStaffName,
          onOpenConnection = { navController.navigate(Routes.CONNECTION) },
          appLanguage = appLanguage,
          onLanguageChange = { language ->
            mainViewModel.setAppLanguage(language) {
              context.findActivity()?.recreate()
            }
          },
          modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
      }

      composable(Routes.CONNECTION) {
        val connectionViewModel: ConnectionViewModel =
          viewModel(factory = ConnectionViewModel.Factory(syncRepository))
        val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()
        ConnectionScreen(
          uiState = connectionState,
          onBack = { navController.popBackStack() },
          onSaveConnection = connectionViewModel::saveConnection,
          onSyncNow = connectionViewModel::syncNow,
          onDisconnect = connectionViewModel::disconnect,
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
        // Ask-once notification prompt, raised after the first finished
        // clean. Below API 33 there is nothing to grant: dismiss
        // silently. Denied = silently off, never re-asked.
        val showNotifPrompt by detailViewModel.notificationPrompt.collectAsStateWithLifecycle()
        val notifPermissionLauncher =
          rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
          ) {
            detailViewModel.dismissNotificationPrompt()
          }
        if (showNotifPrompt) {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            LaunchedEffect(Unit) { detailViewModel.dismissNotificationPrompt() }
          } else {
            AlertDialog(
              onDismissRequest = { detailViewModel.dismissNotificationPrompt() },
              title = { Text(text = stringResource(R.string.notif_prompt_title)) },
              text = { Text(text = stringResource(R.string.notif_prompt_body)) },
              confirmButton = {
                TextButton(
                  onClick = {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  },
                  modifier = Modifier.testTag("notif_prompt_allow_button"),
                ) {
                  Text(text = stringResource(R.string.notif_prompt_allow))
                }
              },
              dismissButton = {
                TextButton(
                  onClick = { detailViewModel.dismissNotificationPrompt() },
                  modifier = Modifier.testTag("notif_prompt_later_button"),
                ) {
                  Text(text = stringResource(R.string.notif_prompt_later))
                }
              },
              modifier = Modifier.testTag("notif_prompt_dialog"),
            )
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
          onOpenInspection = { navController.navigate(Routes.inspection(roomId)) },
          onSharePhoto = ::sharePhoto,
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
          onFinish = { navController.navigate(Routes.photoCaptureRun(roomId)) },
        )
      }

      composable(
        route = Routes.PHOTO_CAPTURE,
        arguments =
          listOf(
            navArgument("mode") { type = NavType.StringType },
            navArgument("refId") { type = NavType.LongType },
          ),
      ) { backStackEntry ->
        val mode = backStackEntry.arguments?.getString("mode")
        val refId = backStackEntry.arguments?.getLong("refId") ?: return@composable
        if (mode == "run") {
          val detailViewModel: RoomDetailViewModel =
            viewModel(
              key = "room_detail_$refId",
              factory = RoomDetailViewModel.Factory(refId, repository),
            )
          val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
          fun finishAfterPhoto() {
            detailViewModel.finishCleaning {
              navController.popBackStack()
              navController.popBackStack()
              coroutineScope.launch {
                snackbarHostState.showSnackbar(runFinishedMessage)
              }
            }
          }
          PhotoCaptureScreen(
            mode = PhotoCaptureMode.Run(detailState.room?.name.orEmpty()),
            onPhotoTaken = { file ->
              detailViewModel.setRunPhoto(file.name) { finishAfterPhoto() }
            },
            onSkip = { reason ->
              detailViewModel.skipRunPhoto(reason) { finishAfterPhoto() }
            },
            onBack = { navController.popBackStack() },
          )
        } else if (mode == "ticket") {
          val ticketViewModel: TicketDetailViewModel =
            viewModel(
              key = "ticket_detail_$refId",
              factory = TicketDetailViewModel.Factory(refId, repository),
            )
          PhotoCaptureScreen(
            mode = PhotoCaptureMode.Ticket,
            onPhotoTaken = { file ->
              ticketViewModel.setTicketPhoto(file.name)
              navController.popBackStack()
            },
            onSkip = {},
            onBack = { navController.popBackStack() },
          )
        } else {
          navController.popBackStack()
        }
      }

      composable(
        route = Routes.INSPECTION,
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
        InspectionScreen(
          uiState = detailState,
          onBack = { navController.popBackStack() },
          onPass = { detailViewModel.inspectPass { navController.popBackStack() } },
          onReturn = { reason, note ->
            detailViewModel.inspectReturn(reason, note) { navController.popBackStack() }
          },
          onSharePhoto = ::sharePhoto,
        )
      }
    }
  }
}
