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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.domain.TicketStatus
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.DemoModePill
import com.vivescriptsolutions.roomready.ui.components.OfflineReadyPill
import com.vivescriptsolutions.roomready.ui.components.RoomStatusChip
import com.vivescriptsolutions.roomready.ui.components.SectionHeading
import com.vivescriptsolutions.roomready.ui.components.StaffPickerSection
import com.vivescriptsolutions.roomready.ui.components.formatTimeShort
import com.vivescriptsolutions.roomready.ui.theme.badgeColors
import com.vivescriptsolutions.roomready.ui.viewmodel.DashboardTabUiState
import com.vivescriptsolutions.roomready.ui.viewmodel.TicketsTabUiState

@Composable
fun TicketsScreen(
  uiState: TicketsTabUiState,
  onToggleTicketStatus: (TicketEntity) -> Unit,
  onOpenRoom: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("tickets_screen"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.tickets_header_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          if (uiState.session.isDemoMode) {
            DemoModePill()
          }
        }
      }

      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Text(
            text = stringResource(R.string.tickets_placeholder_note),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      if (uiState.tickets.isEmpty()) {
        item {
          CalmSectionCard {
            Text(
              text = stringResource(R.string.tickets_empty_state),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(uiState.tickets, key = { it.id }) { ticket ->
          val (badgeBg, badgeFg) = ticket.priority.badgeColors()
          CalmSectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Surface(
                  onClick = { onOpenRoom(ticket.roomId) },
                  modifier = Modifier.heightIn(min = 48.dp),
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                  Text(
                    text = stringResource(R.string.tickets_room_badge_format, ticket.roomName),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                  )
                }
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = badgeBg,
                  contentColor = badgeFg,
                ) {
                  Text(
                    text = stringResource(ticket.priority.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }

              Text(
                text = ticket.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text =
                    stringResource(
                      R.string.ticket_meta_format,
                      ticket.createdBy,
                      stringResource(ticket.status.labelRes),
                      formatTimeShort(ticket.createdAt),
                    ),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                  onClick = { onToggleTicketStatus(ticket) },
                  modifier = Modifier.heightIn(min = 48.dp),
                ) {
                  Text(
                    text =
                      if (ticket.status == TicketStatus.OPEN) stringResource(R.string.ticket_mark_done)
                      else stringResource(R.string.ticket_reopen),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun DashboardScreen(
  uiState: DashboardTabUiState,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("dashboard_screen"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.dashboard_header_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          if (uiState.session.isDemoMode) {
            DemoModePill()
          }
        }
      }

      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Text(
            text = stringResource(R.string.dashboard_placeholder_note),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stringResource(R.string.dashboard_total_rooms_label),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = uiState.totalRooms.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            RoomStatus.entries.forEach { status ->
              val count = uiState.statusCounts[status] ?: 0
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                RoomStatusChip(status = status, large = false)
                Text(
                  text = count.toString(),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }

      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading(text = stringResource(R.string.dashboard_recent_activity))
            if (uiState.recentLogs.isEmpty()) {
              Text(
                text = stringResource(R.string.dashboard_no_activity),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              uiState.recentLogs.forEachIndexed { idx, log ->
                if (idx > 0) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
                Column(
                  modifier = Modifier.padding(vertical = 4.dp),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text =
                      stringResource(
                        R.string.dashboard_log_room_format,
                        stringResource(R.string.room_title_format, log.roomName),
                        stringResource(
                          R.string.status_log_transition_format,
                          stringResource(log.fromStatus.labelRes),
                          stringResource(log.toStatus.labelRes),
                        ),
                      ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      stringResource(
                        R.string.status_log_by_format,
                        log.changedBy,
                        stringResource(log.changedByRole.labelRes),
                        formatTimeShort(log.changedAt),
                      ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MeScreen(
  session: UserSession,
  onSwitchRole: (StaffRole) -> Unit,
  onSwitchStaffName: (String) -> Unit,
  onResetDemoData: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showResetDialog by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.me_header_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (session.isDemoMode) {
            DemoModePill()
          }
          OfflineReadyPill()
        }
      }

      CalmSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SectionHeading(text = stringResource(R.string.me_switch_role_title))
          RoleSelectionCard(
            role = StaffRole.HOUSEKEEPER,
            selected = session.role == StaffRole.HOUSEKEEPER,
            onClick = { onSwitchRole(StaffRole.HOUSEKEEPER) },
          )
          RoleSelectionCard(
            role = StaffRole.SUPERVISOR,
            selected = session.role == StaffRole.SUPERVISOR,
            onClick = { onSwitchRole(StaffRole.SUPERVISOR) },
          )
        }
      }

      CalmSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SectionHeading(text = stringResource(R.string.me_who_are_you_title))
          Text(
            text =
              stringResource(
                R.string.me_active_staff_format,
                session.staffName.orEmpty(),
              ),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
          )
          StaffPickerSection(
            roster = session.staffRoster,
            selectedName = session.staffName,
            onSelectName = onSwitchStaffName,
          )
        }
      }

      CalmSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionHeading(text = stringResource(R.string.me_offline_card_title))
          Text(
            text = stringResource(R.string.me_offline_card_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      CalmSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SectionHeading(text = stringResource(R.string.me_demo_section_title))
          Text(
            text = stringResource(R.string.me_demo_section_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("reset_demo_button"),
            colors =
              ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
          ) {
            Text(
              text = stringResource(R.string.me_reset_demo_button),
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text(text = stringResource(R.string.me_reset_dialog_title)) },
      text = {
        Text(
          text = stringResource(R.string.me_reset_dialog_body),
          style = MaterialTheme.typography.bodyLarge,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onResetDemoData()
            showResetDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(text = stringResource(R.string.me_reset_confirm))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showResetDialog = false },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(text = stringResource(R.string.dialog_cancel))
        }
      },
    )
  }
}
