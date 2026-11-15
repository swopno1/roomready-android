package com.vivescriptsolutions.roomready.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.RoomTypeEntity
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.data.repository.HousekeepingRepository
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionAction
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketPriority
import kotlinx.coroutines.flow.MutableStateFlow
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
)

data class TicketsTabUiState(
  val tickets: List<TicketEntity> = emptyList(),
  val session: UserSession = UserSession(),
)

data class DashboardTabUiState(
  val totalRooms: Int = 0,
  val statusCounts: Map<RoomStatus, Int> = emptyMap(),
  val recentLogs: List<StatusLogEntity> = emptyList(),
  val session: UserSession = UserSession(),
)

class MainAppViewModel(private val repository: HousekeepingRepository) : ViewModel() {
  private val selectedFilter = MutableStateFlow<RoomStatus?>(null)
  private val searchQuery = MutableStateFlow("")
  private val isReady = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      repository.ensureDemoDataSeeded()
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

  val roomsBoardState: StateFlow<RoomsBoardUiState> =
    combine(
        isReady,
        repository.sessionFlow,
        repository.observeAllRooms(),
        repository.observeAllTickets(),
        combine(selectedFilter, searchQuery) { filter, query -> filter to query },
      ) { ready, session, rooms, tickets, filterAndQuery ->
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
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomsBoardUiState(),
      )

  val ticketsTabState: StateFlow<TicketsTabUiState> =
    combine(repository.observeAllTickets(), repository.sessionFlow) { tickets, session ->
        TicketsTabUiState(tickets = tickets, session = session)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TicketsTabUiState(),
      )

  val dashboardTabState: StateFlow<DashboardTabUiState> =
    combine(
        repository.observeAllRooms(),
        repository.observeRecentLogs(15),
        repository.sessionFlow,
      ) { rooms, logs, session ->
        val counts = RoomStatus.entries.associateWith { status -> rooms.count { it.status == status } }
        DashboardTabUiState(
          totalRooms = rooms.size,
          statusCounts = counts,
          recentLogs = logs,
          session = session,
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

  fun toggleTicketStatus(ticket: TicketEntity) {
    viewModelScope.launch { repository.toggleTicketStatus(ticket) }
  }

  fun resetDemoData(onComplete: () -> Unit = {}) {
    viewModelScope.launch {
      repository.resetDemoData()
      onComplete()
    }
  }

  class Factory(private val repository: HousekeepingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MainAppViewModel(repository) as T
  }
}

class RoomDetailViewModel(
  private val roomId: Long,
  private val repository: HousekeepingRepository,
) : ViewModel() {
  val uiState: StateFlow<RoomDetailUiState> =
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
        val legalActions =
          if (room != null) {
            RoomTransitionEngine.legalActionsFor(room.status, activeRole)
          } else {
            emptyList()
          }
        val previewSteps =
          DemoSeeder.buildTemplateSteps(roomType?.checklistTemplateSteps.orEmpty())

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

  fun createTicket(title: String, priority: TicketPriority) {
    val room = uiState.value.room ?: return
    val staffName = uiState.value.session.staffName ?: return
    viewModelScope.launch {
      repository.createTicket(
        roomId = room.id,
        roomName = room.name,
        title = title,
        priority = priority,
        createdBy = staffName,
      )
    }
  }

  fun toggleTicketStatus(ticket: TicketEntity) {
    viewModelScope.launch { repository.toggleTicketStatus(ticket) }
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
