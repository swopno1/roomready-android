package com.vivescriptsolutions.roomready.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.local.RoomEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.formatTimeShort
import com.vivescriptsolutions.roomready.ui.theme.badgeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketCreateSheet(
  rooms: List<RoomEntity>,
  fixedRoomId: Long?,
  staffName: String,
  staffRoster: List<String>,
  onSubmit: (roomId: Long, title: String, description: String, priority: TicketPriority, createdBy: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var titleDraft by remember { mutableStateOf("") }
  var descDraft by remember { mutableStateOf("") }
  var priorityDraft by remember { mutableStateOf(TicketPriority.NORMAL) }
  var roomIdDraft by remember { mutableStateOf(fixedRoomId) }
  var reporterDraft by remember { mutableStateOf(staffName) }
  var roomExpanded by remember { mutableStateOf(false) }
  var reporterExpanded by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier = Modifier.testTag("ticket_create_sheet"),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = stringResource(R.string.ticket_create_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      item {
        val roomOptions = if (fixedRoomId != null) rooms.filter { it.id == fixedRoomId } else rooms
        val selectedRoom = roomOptions.firstOrNull { it.id == roomIdDraft }
        ExposedDropdownMenuBox(
          expanded = roomExpanded && fixedRoomId == null,
          onExpandedChange = { if (fixedRoomId == null) roomExpanded = it },
        ) {
          OutlinedTextField(
            value = selectedRoom?.let { stringResource(R.string.room_title_format, it.name) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = fixedRoomId == null,
            label = { Text(text = stringResource(R.string.ticket_room_label)) },
            placeholder = { Text(text = stringResource(R.string.ticket_pick_room_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("ticket_room_picker"),
          )
          ExposedDropdownMenu(
            expanded = roomExpanded && fixedRoomId == null,
            onDismissRequest = { roomExpanded = false },
          ) {
            roomOptions.forEach { room ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = stringResource(R.string.room_title_format, room.name),
                    style = MaterialTheme.typography.bodyLarge,
                  )
                },
                onClick = {
                  roomIdDraft = room.id
                  roomExpanded = false
                },
                modifier = Modifier.heightIn(min = 48.dp),
              )
            }
          }
        }
      }

      item {
        OutlinedTextField(
          value = titleDraft,
          onValueChange = { titleDraft = it },
          placeholder = { Text(text = stringResource(R.string.ticket_title_hint)) },
          textStyle = MaterialTheme.typography.bodyLarge,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("ticket_title_input"),
        )
      }

      item {
        OutlinedTextField(
          value = descDraft,
          onValueChange = { descDraft = it },
          placeholder = { Text(text = stringResource(R.string.ticket_desc_hint)) },
          textStyle = MaterialTheme.typography.bodyLarge,
          minLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("ticket_desc_input"),
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.ticket_priority_label),
            style = MaterialTheme.typography.titleSmall,
          )
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TicketPriority.entries.forEachIndexed { index, priority ->
              SegmentedButton(
                selected = priority == priorityDraft,
                onClick = { priorityDraft = priority },
                shape = SegmentedButtonDefaults.itemShape(index, TicketPriority.entries.size),
                modifier = Modifier.heightIn(min = 56.dp).testTag("ticket_priority_${priority.name.lowercase()}"),
              ) {
                Text(
                  text = stringResource(priority.labelRes),
                  style = MaterialTheme.typography.titleSmall,
                )
              }
            }
          }
        }
      }

      item {
        ExposedDropdownMenuBox(
          expanded = reporterExpanded,
          onExpandedChange = { reporterExpanded = it },
        ) {
          OutlinedTextField(
            value = reporterDraft,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.me_who_are_you_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reporterExpanded) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("ticket_reporter_picker"),
          )
          ExposedDropdownMenu(
            expanded = reporterExpanded,
            onDismissRequest = { reporterExpanded = false },
          ) {
            staffRoster.forEach { name ->
              DropdownMenuItem(
                text = { Text(text = name, style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                  reporterDraft = name
                  reporterExpanded = false
                },
                modifier = Modifier.heightIn(min = 48.dp),
              )
            }
          }
        }
      }

      item {
        Button(
          onClick = {
            val roomId = roomIdDraft ?: return@Button
            onSubmit(roomId, titleDraft, descDraft, priorityDraft, reporterDraft)
          },
          enabled = titleDraft.trim().isNotEmpty() && roomIdDraft != null,
          modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ticket_submit_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = stringResource(R.string.ticket_submit),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
        Spacer(modifier = Modifier.size(24.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
  ticket: TicketEntity?,
  staffName: String?,
  onBack: () -> Unit,
  onOpenRoom: (Long) -> Unit,
  onMarkDone: () -> Unit,
  onReopen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize().testTag("ticket_detail_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.tickets_header_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("ticket_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
          ),
      )
    },
  ) { innerPadding ->
    if (ticket == null) {
      Spacer(modifier = Modifier.padding(innerPadding))
      return@Scaffold
    }
    val (badgeBg, badgeFg) = ticket.priority.badgeColors()
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBg,
                contentColor = badgeFg,
              ) {
                Text(
                  text = stringResource(ticket.priority.labelRes),
                  style = MaterialTheme.typography.labelLarge,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
              Text(
                text = stringResource(ticket.status.labelRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text = ticket.title,
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (ticket.description.isNotBlank()) {
              Text(
                text = ticket.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text =
                stringResource(
                  R.string.ticket_created_at_format,
                  ticket.createdBy,
                  formatTimeShort(ticket.createdAt),
                ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val resolvedBy = ticket.resolvedBy
            val resolvedAt = ticket.resolvedAt
            if (ticket.status == TicketStatus.DONE && resolvedBy != null && resolvedAt != null) {
              Text(
                text =
                  stringResource(
                    R.string.ticket_resolved_by_format,
                    resolvedBy,
                    formatTimeShort(resolvedAt),
                  ),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      item {
        OutlinedButton(
          onClick = { onOpenRoom(ticket.roomId) },
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
          Text(text = stringResource(R.string.ticket_open_room_button))
        }
      }

      item {
        if (ticket.status == TicketStatus.OPEN) {
          Button(
            onClick = onMarkDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ticket_mark_done_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = stringResource(R.string.ticket_mark_done),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        } else {
          OutlinedButton(
            onClick = onReopen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ticket_reopen_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = stringResource(R.string.ticket_reopen),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }
    }
  }
}
