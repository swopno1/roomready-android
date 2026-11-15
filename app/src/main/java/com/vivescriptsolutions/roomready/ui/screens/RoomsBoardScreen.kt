package com.vivescriptsolutions.roomready.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.RunReturnFlag
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.formatReturnReason
import com.vivescriptsolutions.roomready.ui.components.DemoModePill
import com.vivescriptsolutions.roomready.ui.components.OfflineReadyPill
import com.vivescriptsolutions.roomready.ui.components.RoomStatusChip
import com.vivescriptsolutions.roomready.ui.components.formatTimeShort
import com.vivescriptsolutions.roomready.ui.components.localizedFloorLabel
import com.vivescriptsolutions.roomready.ui.components.localizedRoomTypeName
import com.vivescriptsolutions.roomready.ui.theme.colorSpec
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomsBoardUiState

@Composable
fun RoomsBoardScreen(
  uiState: RoomsBoardUiState,
  onSelectFilter: (RoomStatus?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onRoomClick: (Long) -> Unit,
  onProfileClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
    ) {
      // Top Resort Header
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.demo_resort_name),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            if (uiState.session.isDemoMode) {
              DemoModePill()
            }
          }
          OfflineReadyPill()
        }

        // Active Staff + Role summary pill
        val roleLabel = uiState.session.role?.let { stringResource(it.labelRes) }.orEmpty()
        val staffName = uiState.session.staffName.orEmpty()
        if (staffName.isNotEmpty()) {
          Surface(
            onClick = onProfileClick,
            modifier = Modifier.heightIn(min = 48.dp).testTag("active_staff_banner"),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.cd_role_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.staff_role_header_format, staffName, roleLabel),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        // Search by room name / type / staff
        OutlinedTextField(
          value = uiState.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = {
            Text(
              text = stringResource(R.string.rooms_search_placeholder),
              style = MaterialTheme.typography.bodyLarge,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = stringResource(R.string.cd_search_icon),
            )
          },
          trailingIcon = {
            if (uiState.searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchQueryChange("") },
                modifier = Modifier.testTag("clear_search_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Clear,
                  contentDescription = stringResource(R.string.cd_clear_search),
                )
              }
            }
          },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge,
          shape = MaterialTheme.shapes.medium,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
          modifier = Modifier.fillMaxWidth().testTag("search_rooms_input"),
        )
      }

      // Filter chips per status
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          val allSelected = uiState.selectedFilter == null
          FilterChip(
            selected = allSelected,
            onClick = { onSelectFilter(null) },
            label = {
              Text(
                text =
                  stringResource(
                    R.string.filter_count_format,
                    stringResource(R.string.status_all),
                    uiState.allRooms.size,
                  ),
                style = MaterialTheme.typography.labelLarge,
              )
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("filter_chip_all"),
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurface,
              ),
          )
        }
        items(RoomStatus.entries) { status ->
          val isSelected = uiState.selectedFilter == status
          val count = uiState.statusCounts[status] ?: 0
          val statusColors = status.colorSpec()
          FilterChip(
            selected = isSelected,
            onClick = { onSelectFilter(if (isSelected) null else status) },
            label = {
              Text(
                text =
                  stringResource(
                    R.string.filter_count_format,
                    stringResource(status.labelRes),
                    count,
                  ),
                style = MaterialTheme.typography.labelLarge,
              )
            },
            modifier =
              Modifier.heightIn(min = 48.dp).testTag("filter_chip_${status.name.lowercase()}"),
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = statusColors.chipBackground,
                selectedLabelColor = statusColors.chipContent,
                containerColor = statusColors.softTintBackground,
                labelColor = statusColors.softTintContent,
              ),
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSelected,
                borderColor = statusColors.softTintContent.copy(alpha = 0.25f),
                selectedBorderColor = statusColors.chipBackground,
              ),
          )
        }
      }

      Text(
        text =
          stringResource(
            R.string.rooms_summary_format,
            uiState.filteredRooms.size,
            uiState.allRooms.size,
          ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      if (uiState.isLoading) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.testTag("rooms_loading_indicator"),
          )
        }
      } else if (uiState.filteredRooms.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.rooms_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = stringResource(R.string.rooms_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Button(
            onClick = {
              onSelectFilter(null)
              onSearchQueryChange("")
            },
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text(text = stringResource(R.string.rooms_clear_filters))
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().testTag("rooms_list"),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(uiState.filteredRooms, key = { it.id }) { room ->
            RoomCard(
              room = room,
              openTicketCount = uiState.openTicketsByRoomId[room.id] ?: 0,
              urgentOpenCount = uiState.urgentOpenByRoomId[room.id] ?: 0,
              returnedFlag = uiState.returnedFlagsByRoomId[room.id],
              onClick = { onRoomClick(room.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RoomCard(
  room: RoomEntity,
  openTicketCount: Int,
  urgentOpenCount: Int,
  returnedFlag: RunReturnFlag?,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 96.dp)
        .testTag("room_card_${room.name}"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.room_title_format, room.name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              stringResource(
                R.string.room_meta_format,
                localizedRoomTypeName(room.roomTypeName),
                localizedFloorLabel(room.floor, room.block),
              ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        RoomStatusChip(status = room.status, large = true)
      }

      if (returnedFlag != null) {
        val chipLabel =
          runCatching { ReturnReason.valueOf(returnedFlag.returnReasonKey) }
            .map { stringResource(it.labelRes) }
            .getOrDefault(returnedFlag.returnReasonKey)
        Surface(
          shape = RoundedCornerShapePill,
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("room_returned_badge_${room.name}"),
        ) {
          Text(
            text =
              stringResource(
                R.string.room_returned_badge_format,
                formatReturnReason(chipLabel, returnedFlag.returnNote),
              ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val assignedText =
          if (room.assignedTo != null) {
            stringResource(R.string.assigned_to_format, room.assignedTo)
          } else {
            stringResource(R.string.unassigned_label)
          }
        Text(
          text = assignedText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (urgentOpenCount > 0) {
            Surface(
              shape = RoundedCornerShapePill,
              color = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
              modifier = Modifier.testTag("room_urgent_badge_${room.name}"),
            ) {
              Text(
                text = stringResource(R.string.urgent_badge_format, urgentOpenCount),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
          if (openTicketCount > 0) {
            Surface(
              shape = RoundedCornerShapePill,
              color = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Build,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = stringResource(R.string.open_tickets_badge_format, openTicketCount),
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
          Text(
            text = stringResource(R.string.updated_at_format, formatTimeShort(room.updatedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private val RoundedCornerShapePill = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
