package com.vivescriptsolutions.roomready.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.RoomTypeEntity
import com.vivescriptsolutions.roomready.data.local.RunReturnFlag
import com.vivescriptsolutions.roomready.data.local.StatusCount
import com.vivescriptsolutions.roomready.data.local.TicketPriorityCount
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.domain.AppLanguage
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.data.connectivity.ConnectivityMonitor
import com.vivescriptsolutions.roomready.data.repository.PersonCount
import com.vivescriptsolutions.roomready.data.repository.cleansPerPerson
import com.vivescriptsolutions.roomready.data.repository.filterTicketsByStatus
import com.vivescriptsolutions.roomready.data.repository.readinessPercent
import com.vivescriptsolutions.roomready.data.repository.startOfDayMillis
import com.vivescriptsolutions.roomready.data.repository.stuckCleaningRooms
import com.vivescriptsolutions.roomready.data.repository.urgentOpenByRoom
import com.vivescriptsolutions.roomready.data.repository.urgentOpenCount
import com.vivescriptsolutions.roomready.data.sync.BootstrapOutcome
import com.vivescriptsolutions.roomready.data.sync.SyncOutcome
import com.vivescriptsolutions.roomready.data.sync.SyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionAction
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoomsBoardUiState(
  val isLoading: Boolean = true,
  val session: UserSession = UserSession(),
  val allRooms: List<RoomEntity> = emptyList(),
  val filteredRooms: List<RoomEntity> = emptyList(),
  val selectedFilter: RoomStatus? = null,
  val searchQuery: String = "",
  val statusCounts: Map<RoomStatus, Int> = emptyMap(),
  val openTicketsByRoomId: Map<Long, Int> = emptyMap(),
  val urgentOpenByRoomId: Map<Long, Int> = emptyMap(),
  val returnedFlagsByRoomId: Map<Long, RunReturnFlag> = emptyMap(),
)

data class RoomDetailUiState(
  val isLoading: Boolean = true,
  val room: RoomEntity? = null,
  val roomType: RoomTypeEntity? = null,
  val latestChecklistRun: ChecklistRunEntity? = null,
  val templatePreviewSteps: List<ChecklistStepState> = emptyList(),
  val tickets: List<TicketEntity> = emptyList(),
  val statusLogs: List<StatusLogEntity> = emptyList(),
  val legalActions: List<RoomTransitionAction> = emptyList(),
  val session: UserSession = UserSession(),
  val actionError: String? = null,
)

data class TicketsTabUiState(
  val tickets: List<TicketEntity> = emptyList(),
  val filteredTickets: List<TicketEntity> = emptyList(),
  val selectedFilter: TicketStatus? = null,
  val urgentOpenCount: Int = 0,
  val rooms: List<RoomEntity> = emptyList(),
  val session: UserSession = UserSession(),
)

data class TicketDetailUiState(
  val ticket: TicketEntity? = null,
  val session: UserSession = UserSession(),
)

sealed interface ConnectionMessage {
  data object Saved : ConnectionMessage

  data object Synced : ConnectionMessage

  data object Disconnected : ConnectionMessage

  data class Failed(val detail: String) : ConnectionMessage

  data object Unauthorized : ConnectionMessage

  data object UpdateRequired : ConnectionMessage
}

data class ConnectionUiState(
  val isConfigured: Boolean = false,
  val baseUrl: String? = null,
  val propertyName: String? = null,
  val businessDate: String? = null,
  val lastSyncAt: Long? = null,
  val lastError: String? = null,
  val pendingCount: Int = 0,
  val syncing: Boolean = false,
  val message: ConnectionMessage? = null,
)

private data class DashboardNumbers(
  val statusRows: List<StatusCount>,
  val cleaningRooms: List<RoomEntity>,
  val cleansToday: List<StatusLogEntity>,
  val ticketRows: List<TicketPriorityCount>,
  val toInspectRooms: List<RoomEntity>,
)

private data class DashboardContext(
  val recentLogs: List<StatusLogEntity>,
  val session: UserSession,
  val online: Boolean,
  val outboxCount: Int,
  val now: Long,
)

data class DashboardTabUiState(
  val totalRooms: Int = 0,
  val statusCounts: Map<RoomStatus, Int> = emptyMap(),
  val readinessPercent: Int = 0,
  val cleansTodayCount: Int = 0,
  val cleansTodayByPerson: List<PersonCount> = emptyList(),
  val openTicketsByPriority: Map<TicketPriority, Int> = emptyMap(),
  val stuckCleaningRooms: List<RoomEntity> = emptyList(),
  val toInspectRooms: List<RoomEntity> = emptyList(),
  val isOnline: Boolean = true,
  val outboxPendingCount: Int = 0,
  val recentLogs: List<StatusLogEntity> = emptyList(),
  val session: UserSession = UserSession(),
)

class MainAppViewModel(
  private val repository: HousekeepingRepository,
  connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {
  private val selectedFilter = MutableStateFlow<RoomStatus?>(null)
  private val searchQuery = MutableStateFlow("")
  private val selectedTicketFilter = MutableStateFlow<TicketStatus?>(null)
  private val isReady = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      repository.ensureProductionBaseline()
      isReady.value = true
    }
  }

  val sessionState: StateFlow<UserSession> =
    repository.sessionFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSession(),
    )

  val isAppInitialized: StateFlow<Boolean> =
    isReady.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = false,
    )

  val isPmsConfigured: StateFlow<Boolean> =
    repository.observeConnection()
      .map { it.isConfigured }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
      )

  val roomsBoardState: StateFlow<RoomsBoardUiState> =
    combine(
        isReady,
        repository.sessionFlow,
        repository.observeAllRooms(),
        repository.observeAllTickets(),
        combine(
          repository.observeReturnedRunFlags(),
          combine(selectedFilter, searchQuery) { filter, query -> filter to query },
        ) { flags, filterAndQuery -> flags to filterAndQuery },
      ) { ready, session, rooms, tickets, flagsAndFilter ->
        val (returnFlags, filterAndQuery) = flagsAndFilter
        val (filter, query) = filterAndQuery
        val counts = RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }
        val openTicketsByRoom =
          tickets
            .filter { it.status.name == "OPEN" }
            .groupingBy { it.roomId }
            .eachCount()
        val trimmedQuery = query.trim()
        val filtered =
          rooms.filter { room ->
            val matchesStatus = filter == null || room.status == filter
            val matchesQuery =
              trimmedQuery.isEmpty() ||
                room.name.contains(trimmedQuery, ignoreCase = true) ||
                room.roomTypeName.contains(trimmedQuery, ignoreCase = true) ||
                (room.assignedTo?.contains(trimmedQuery, ignoreCase = true) == true)
            matchesStatus && matchesQuery
          }
        RoomsBoardUiState(
          isLoading = !ready,
          session = session,
          allRooms = rooms,
          filteredRooms = filtered,
          selectedFilter = filter,
          searchQuery = query,
          statusCounts = counts,
          openTicketsByRoomId = openTicketsByRoom,
          urgentOpenByRoomId = urgentOpenByRoom(tickets),
          returnedFlagsByRoomId = returnFlags.associateBy { it.roomId },
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomsBoardUiState(),
      )

  val ticketsTabState: StateFlow<TicketsTabUiState> =
    combine(
        repository.observeAllTickets(),
        repository.observeAllRooms(),
        repository.sessionFlow,
        selectedTicketFilter,
      ) { tickets, rooms, session, filter ->
        TicketsTabUiState(
          tickets = tickets,
          filteredTickets = filterTicketsByStatus(tickets, filter),
          selectedFilter = filter,
          urgentOpenCount = urgentOpenCount(tickets),
          rooms = rooms,
          session = session,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TicketsTabUiState(),
      )

  // Ticks every minute so the stuck-room highlight and "today" boundary
  // stay live without polling any data source (Room pushes data changes).
  private val minuteTicker: Flow<Long> =
    flow {
      while (true) {
        emit(System.currentTimeMillis())
        delay(60_000L)
      }
    }

  private val cleansTodayFlow: Flow<List<StatusLogEntity>> =
    minuteTicker.flatMapLatest { now ->
      repository.observeLogsSince(RoomStatus.CLEANED, repository.todayStartMillis(now))
    }

  val onlineState: StateFlow<Boolean> =
    connectivityMonitor.isOnline.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = true,
    )

  // Every number below is computed from Room queries at collection time —
  // no cached copies. P7 swaps the snapshot underneath; these queries
  // target the same tables and keep working untouched.
  val dashboardTabState: StateFlow<DashboardTabUiState> =
    combine(
        combine(
          repository.observeStatusCounts(),
          repository.observeRoomsByStatus(RoomStatus.CLEANING),
          cleansTodayFlow,
          repository.observeCountByPriority(TicketStatus.OPEN),
          repository.observeRoomsByStatus(RoomStatus.CLEANED),
        ) { statusRows, cleaningRooms, cleansToday, ticketRows, toInspectRooms ->
          DashboardNumbers(statusRows, cleaningRooms, cleansToday, ticketRows, toInspectRooms)
        },
        combine(
          repository.observeRecentLogs(15),
          repository.sessionFlow,
          connectivityMonitor.isOnline,
          repository.observeOutboxCount(),
          minuteTicker,
        ) { recentLogs, session, online, outboxCount, now ->
          DashboardContext(recentLogs, session, online, outboxCount, now)
        },
      ) { numbers, context ->
        val counts =
          RoomStatus.entries.associateWith { status ->
            numbers.statusRows.firstOrNull { it.status == status }?.count ?: 0
          }
        val total = counts.values.sum()
        DashboardTabUiState(
          totalRooms = total,
          statusCounts = counts,
          readinessPercent = readinessPercent(counts[RoomStatus.READY] ?: 0, total),
          cleansTodayCount = numbers.cleansToday.size,
          cleansTodayByPerson = cleansPerPerson(numbers.cleansToday),
          openTicketsByPriority = numbers.ticketRows.associate { it.priority to it.count },
          stuckCleaningRooms = stuckCleaningRooms(numbers.cleaningRooms, context.now),
          toInspectRooms = numbers.toInspectRooms,
          isOnline = context.online,
          outboxPendingCount = context.outboxCount,
          recentLogs = context.recentLogs,
          session = context.session,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardTabUiState(),
      )

  fun setFilter(status: RoomStatus?) {
    selectedFilter.value = status
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun saveRoleAndStaff(role: StaffRole, staffName: String) {
    viewModelScope.launch { repository.saveRoleAndStaff(role, staffName) }
  }

  fun switchRole(role: StaffRole) {
    viewModelScope.launch { repository.switchRole(role) }
  }

  fun switchStaffName(staffName: String) {
    viewModelScope.launch { repository.switchStaffName(staffName) }
  }

  val appLanguage: StateFlow<AppLanguage> =
    repository.observeAppLanguage().stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = AppLanguage.SYSTEM,
    )

  fun setAppLanguage(language: AppLanguage, onSaved: () -> Unit = {}) {
    viewModelScope.launch {
      repository.setAppLanguage(language)
      onSaved()
    }
  }

  fun setTicketFilter(status: TicketStatus?) {
    selectedTicketFilter.value = status
  }

  fun createTicket(
    roomId: Long,
    title: String,
    description: String,
    priority: TicketPriority,
    createdBy: String? = null,
  ) {
    val reporter = createdBy?.takeIf { it.isNotBlank() } ?: sessionState.value.staffName ?: return
    viewModelScope.launch {
      val rooms = roomsBoardState.value.allRooms
      val room = rooms.firstOrNull { it.id == roomId } ?: return@launch
      repository.createTicket(
        roomId = room.id,
        roomName = room.name,
        title = title,
        description = description,
        priority = priority,
        createdBy = reporter,
      )
    }
  }

  fun toggleTicketStatus(ticket: TicketEntity) {
    val staffName = sessionState.value.staffName ?: return
    viewModelScope.launch { repository.toggleTicketStatus(ticket, staffName) }
  }

  class Factory(
    private val repository: HousekeepingRepository,
    private val connectivityMonitor: ConnectivityMonitor,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MainAppViewModel(repository, connectivityMonitor) as T
  }
}

class RoomDetailViewModel(
  private val roomId: Long,
  private val repository: HousekeepingRepository,
) : ViewModel() {
  private val actionError = MutableStateFlow<String?>(null)

  /**
   * One-shot notification-permission prompt: raised after a successful
   * finish (first value) when never asked; cleared on dismiss. The
   * route shows the rationale dialog and the system prompt.
   */
  private val _notificationPrompt = MutableStateFlow(false)
  val notificationPrompt: StateFlow<Boolean> = _notificationPrompt.asStateFlow()

  private val detailCore: StateFlow<RoomDetailUiState> =
    combine(
        repository.observeRoom(roomId),
        repository.observeRoomTypes(),
        repository.observeLatestChecklistRun(roomId),
        combine(
          repository.observeTicketsForRoom(roomId),
          repository.observeLogsForRoom(roomId),
          repository.sessionFlow,
        ) { tickets, logs, session ->
          Triple(tickets, logs, session)
        },
      ) { room, roomTypes, checklistRun, extras ->
        val (tickets, logs, session) = extras
        val roomType = roomTypes.firstOrNull { it.id == room?.roomTypeId }
        val activeRole = session.role ?: StaffRole.HOUSEKEEPER
        // OUT_OF_SERVICE is PMS-driven and read-only in the app.
        val legalActions =
          if (room != null) {
            RoomTransitionEngine.legalActionsFor(room.status, activeRole)
              .filter { action ->
                action.toStatus != RoomStatus.OUT_OF_SERVICE &&
                  room.status != RoomStatus.OUT_OF_SERVICE
              }
          } else {
            emptyList()
          }
        val previewSteps =
          ChecklistTemplates.buildSteps(roomType?.checklistTemplateSteps.orEmpty())

        RoomDetailUiState(
          isLoading = false,
          room = room,
          roomType = roomType,
          latestChecklistRun = checklistRun,
          templatePreviewSteps = previewSteps,
          tickets = tickets,
          statusLogs = logs,
          legalActions = legalActions,
          session = session,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomDetailUiState(),
      )

  val uiState: StateFlow<RoomDetailUiState> =
    combine(detailCore, actionError) { state, error ->
        state.copy(actionError = error)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomDetailUiState(),
      )

  fun startCleaning(kind: ChecklistRunKind, onStarted: () -> Unit = {}) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.startChecklistRun(
          roomId = roomId,
          kind = kind,
          staffName = staffName,
          role = role,
        )
        .onSuccess { onStarted() }
        .onFailure { error -> actionError.value = error.message }
    }
  }

  fun finishCleaning(onFinished: () -> Unit = {}) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.finishChecklistRun(
          roomId = roomId,
          staffName = staffName,
          role = role,
        )
        .onSuccess {
          onFinished()
          maybePromptNotifications()
        }
        .onFailure { error -> actionError.value = error.message }
    }
  }

  fun dismissNotificationPrompt() {
    viewModelScope.launch {
      repository.markNotificationsAsked()
      _notificationPrompt.value = false
    }
  }

  private suspend fun maybePromptNotifications() {
    if (!repository.notificationsAsked()) {
      _notificationPrompt.value = true
    }
  }

  fun inspectPass(onDone: () -> Unit = {}) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.inspectPass(roomId = roomId, staffName = staffName, role = role)
        .onSuccess { onDone() }
        .onFailure { error -> actionError.value = error.message }
    }
  }

  fun inspectReturn(reason: ReturnReason?, note: String, onDone: () -> Unit = {}) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.inspectReturn(
          roomId = roomId, reason = reason, note = note,
          staffName = staffName, role = role,
        )
        .onSuccess { onDone() }
        .onFailure { error -> actionError.value = error.message }
    }
  }

  fun setRunPhoto(photoName: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.setRunPhoto(roomId, photoName)
      onDone()
    }
  }

  fun skipRunPhoto(reason: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.skipRunPhoto(roomId, reason)
        .onSuccess { onDone() }
        .onFailure { error -> actionError.value = error.message }
    }
  }

  fun clearActionError() {
    actionError.value = null
  }

  fun performTransition(toStatus: RoomStatus) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.transitionRoomStatus(
          roomId = roomId,
          toStatus = toStatus,
          staffName = staffName,
          role = role,
        )
        .onSuccess {
          if (toStatus == RoomStatus.CLEANED) maybePromptNotifications()
        }
        .onFailure { error -> actionError.value = error.message }
    }
  }

  fun toggleChecklistStep(stepIndex: Int) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.toggleChecklistStep(
        roomId = roomId,
        stepIndex = stepIndex,
        staffName = staffName,
        role = role,
      )
    }
  }

  fun updateChecklistStepNote(stepIndex: Int, note: String) {
    val session = uiState.value.session
    val staffName = session.staffName ?: return
    val role = session.role ?: return
    viewModelScope.launch {
      repository.updateChecklistStepNote(
        roomId = roomId,
        stepIndex = stepIndex,
        note = note,
        staffName = staffName,
        role = role,
      )
    }
  }

  fun createTicket(
    title: String,
    description: String,
    priority: TicketPriority,
    createdBy: String? = null,
  ) {
    val room = uiState.value.room ?: return
    val reporter = createdBy?.takeIf { it.isNotBlank() } ?: uiState.value.session.staffName ?: return
    viewModelScope.launch {
      repository.createTicket(
        roomId = room.id,
        roomName = room.name,
        title = title,
        description = description,
        priority = priority,
        createdBy = reporter,
      )
    }
  }

  fun toggleTicketStatus(ticket: TicketEntity) {
    val staffName = uiState.value.session.staffName ?: return
    viewModelScope.launch { repository.toggleTicketStatus(ticket, staffName) }
  }

  class Factory(
    private val roomId: Long,
    private val repository: HousekeepingRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      RoomDetailViewModel(roomId, repository) as T
  }
}

class TicketDetailViewModel(
  private val ticketId: Long,
  private val repository: HousekeepingRepository,
) : ViewModel() {
  val uiState: StateFlow<TicketDetailUiState> =
    combine(repository.observeTicket(ticketId), repository.sessionFlow) { ticket, session ->
        TicketDetailUiState(ticket = ticket, session = session)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TicketDetailUiState(),
      )

  fun markDone() {
    val staffName = uiState.value.session.staffName ?: return
    viewModelScope.launch { repository.resolveTicket(ticketId, resolvedBy = staffName) }
  }

  fun reopen() {
    viewModelScope.launch { repository.reopenTicket(ticketId) }
  }

  fun setTicketPhoto(photoName: String) {
    viewModelScope.launch { repository.setTicketPhoto(ticketId, photoName) }
  }

  class Factory(
    private val ticketId: Long,
    private val repository: HousekeepingRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      TicketDetailViewModel(ticketId, repository) as T
  }
}

class ConnectionViewModel(
  private val sync: SyncRepository,
) : ViewModel() {
  private val syncing = MutableStateFlow(false)
  private val message = MutableStateFlow<ConnectionMessage?>(null)

  val uiState: StateFlow<ConnectionUiState> =
    combine(
      sync.observeConnection(),
      sync.observeSyncState(),
      sync.observeOutboxCount(),
      syncing,
      message,
    ) { connection, syncState, pending, syncingNow, msg ->
      ConnectionUiState(
        isConfigured = connection.isConfigured,
        baseUrl = connection.baseUrl,
        propertyName = syncState?.propertyName,
        businessDate = syncState?.businessDate,
        lastSyncAt = syncState?.lastSyncAt,
        lastError = syncState?.lastError,
        pendingCount = pending,
        syncing = syncingNow,
        message = msg,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = ConnectionUiState(),
    )

  init {
    sync.clearAuthRevoked()
  }

  fun saveConnection(baseUrl: String, rawKey: String) {
    message.value = null
    viewModelScope.launch {
      syncing.value = true
      try {
        message.value =
          when (val outcome = sync.saveConnection(baseUrl, rawKey)) {
            is BootstrapOutcome.Applied -> ConnectionMessage.Saved
            is BootstrapOutcome.Unauthorized -> ConnectionMessage.Unauthorized
            is BootstrapOutcome.UpdateRequired ->
              ConnectionMessage.UpdateRequired
            is BootstrapOutcome.Failed -> ConnectionMessage.Failed(outcome.reason)
          }
      } finally {
        syncing.value = false
      }
    }
  }

  fun syncNow() {
    message.value = null
    viewModelScope.launch {
      syncing.value = true
      try {
        message.value =
          when (val outcome = sync.syncNow()) {
            is SyncOutcome.Completed ->
              if (outcome.errors.isEmpty()) ConnectionMessage.Synced
              else ConnectionMessage.Failed(outcome.errors.first())
            is SyncOutcome.AuthRevoked -> ConnectionMessage.Unauthorized
            is SyncOutcome.Offline ->
              ConnectionMessage.Failed("Offline — changes stay queued")
            is SyncOutcome.NotConfigured ->
              ConnectionMessage.Failed("Connect a property first")
          }
      } finally {
        syncing.value = false
      }
    }
  }

  fun disconnect() {
    viewModelScope.launch {
      sync.disconnect()
      message.value = ConnectionMessage.Disconnected
    }
  }

  fun clearMessage() {
    message.value = null
  }

  class Factory(
    private val sync: SyncRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ConnectionViewModel(sync) as T
  }
}
