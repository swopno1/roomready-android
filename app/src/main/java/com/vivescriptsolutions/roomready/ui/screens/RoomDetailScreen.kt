package com.vivescriptsolutions.roomready.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.local.StatusLogEntity
import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.EvidencePhotoThumbnail
import com.vivescriptsolutions.roomready.ui.components.RoomStatusChip
import com.vivescriptsolutions.roomready.ui.components.SectionHeading
import com.vivescriptsolutions.roomready.ui.components.formatTimeShort
import com.vivescriptsolutions.roomready.ui.locale.digitString
import com.vivescriptsolutions.roomready.ui.components.localizedChecklistStepTitle
import com.vivescriptsolutions.roomready.ui.components.localizedFloorLabel
import com.vivescriptsolutions.roomready.ui.components.localizedRoomTypeName
import com.vivescriptsolutions.roomready.ui.theme.badgeColors
import com.vivescriptsolutions.roomready.ui.theme.colorSpec
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
  uiState: RoomDetailUiState,
  onBack: () -> Unit,
  onTransition: (RoomStatus) -> Unit,
  onToggleStep: (Int) -> Unit,
  onSaveStepNote: (Int, String) -> Unit,
  onCreateTicket: (String, String, TicketPriority, String) -> Unit,
  onToggleTicketStatus: (TicketEntity) -> Unit,
  onOpenTicket: (Long) -> Unit = {},
  onOpenRun: () -> Unit = {},
  onOpenInspection: () -> Unit = {},
  onSharePhoto: (String) -> Unit = {},
  onStartCleaning: (ChecklistRunKind) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  var editingStep by remember { mutableStateOf<ChecklistStepState?>(null) }
  var showTicketDialog by remember { mutableStateOf(false) }
  var showTemplatePicker by remember { mutableStateOf(false) }

  val room = uiState.room
  val activeRun = uiState.latestChecklistRun
  val hasUnfinishedRun =
    room?.status == RoomStatus.CLEANING && activeRun != null && activeRun.finishedAt == null

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          if (room != null) {
            Column {
              Text(
                text = digitString(R.string.room_title_format, room.name),
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text =
                  stringResource(
                    R.string.room_meta_format,
                    localizedRoomTypeName(room.roomTypeName),
                    localizedFloorLabel(room.floor, room.block),
                  ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        actions = {
          if (room != null) {
            RoomStatusChip(
              status = room.status,
              large = true,
              modifier = Modifier.padding(end = 12.dp),
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
    if (room == null) {
      Spacer(modifier = Modifier.padding(innerPadding))
      return@Scaffold
    }

    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.TopCenter,
    ) {
    LazyColumn(
      modifier =
        Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize()
          .testTag("room_detail_list"),
      contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Legal Next Actions
      item {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
          CalmSectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              SectionHeading(text = stringResource(R.string.detail_next_actions))

              if (room.isOccupied && room.status == RoomStatus.DIRTY) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                  Text(
                    text = stringResource(R.string.room_occupied_badge),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }

              uiState.legalActions.forEach { action ->
                // While a run is unfinished, the run screen owns finishing: offer
                // Resume instead of a Finish button that the gate would reject.
                if (action.toStatus == RoomStatus.CLEANED && hasUnfinishedRun) {
                  Button(
                    onClick = onOpenRun,
                    modifier =
                      Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("resume_cleaning_button"),
                    shape = MaterialTheme.shapes.medium,
                  ) {
                    Text(
                      text = stringResource(R.string.action_resume_cleaning),
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                  }
                  return@forEach
                }
                val finishedRun = activeRun?.finishedAt != null
                // On a cleaned room with a finished run, Pass/Return live on the
                // inspection screen (Return needs its reason chips there).
                if (room.status == RoomStatus.CLEANED && finishedRun &&
                  (action.toStatus == RoomStatus.READY || action.toStatus == RoomStatus.CLEANING)
                ) {
                  if (action.isPrimary) {
                    Button(
                      onClick = onOpenInspection,
                      modifier =
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("inspect_room_button"),
                      shape = MaterialTheme.shapes.medium,
                    ) {
                      Text(
                        text = stringResource(R.string.action_inspect_room),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      )
                    }
                  } else {
                    OutlinedButton(
                      onClick = onOpenInspection,
                      modifier =
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                          .testTag("action_button_${action.toStatus.name.lowercase()}"),
                      shape = MaterialTheme.shapes.medium,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                      colors =
                        ButtonDefaults.outlinedButtonColors(
                          contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                      Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                      )
                    }
                  }
                  return@forEach
                }
                val targetColors = action.toStatus.colorSpec()
                val tag = "action_button_${action.toStatus.name.lowercase()}"
                // Start cleaning opens the run screen; occupied rooms pick a template first.
                val startCleaningClick: (() -> Unit)? =
                  if (action.toStatus == RoomStatus.CLEANING && room.status == RoomStatus.DIRTY) {
                    {
                      if (room.isOccupied) {
                        showTemplatePicker = true
                      } else {
                        onStartCleaning(ChecklistRunKind.FULL)
                      }
                    }
                  } else {
                    null
                  }
                if (action.isPrimary) {
                  Button(
                    onClick = startCleaningClick ?: { onTransition(action.toStatus) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = targetColors.chipBackground,
                        contentColor = targetColors.chipContent,
                      ),
                  ) {
                    Text(
                      text = stringResource(action.labelRes),
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                  }
                } else {
                  OutlinedButton(
                    onClick = { onTransition(action.toStatus) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors =
                      ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                      ),
                  ) {
                    Text(
                      text = stringResource(action.labelRes),
                      style = MaterialTheme.typography.titleSmall,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 2. 8-Step Turnover Checklist
      item {
        val steps = activeRun?.steps ?: uiState.templatePreviewSteps
        val doneCount = steps.count { it.isDone }
        val isChecklistInteractive =
          room.status == RoomStatus.CLEANING || room.status == RoomStatus.CLEANED

        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stringResource(R.string.detail_checklist_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = digitString(R.string.checklist_progress_format, doneCount, steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            EvidencePhotoThumbnail(
              photoName = activeRun?.photoPath,
              onShare = onSharePhoto,
              testTagPrefix = "run",
            )

            if (activeRun != null) {
              val runSubtitle =
                if (activeRun.finishedAt != null) {
                  stringResource(
                    R.string.checklist_finished_format,
                    activeRun.performedBy,
                    formatTimeShort(activeRun.finishedAt),
                  )
                } else {
                  stringResource(R.string.checklist_by_format, activeRun.performedBy)
                }
              Text(
                text = runSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text(
                text = stringResource(R.string.checklist_preview_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            steps.forEachIndexed { index, step ->
              if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
              }
              ChecklistStepRow(
                step = step,
                enabled = isChecklistInteractive,
                onToggle = { onToggleStep(step.stepIndex) },
                onEditNote = { editingStep = step },
              )
            }
          }
        }
      }

      // 3. Room Issues / Tickets
      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stringResource(R.string.detail_tickets_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              OutlinedButton(
                onClick = { showTicketDialog = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("report_issue_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = stringResource(R.string.detail_report_issue))
              }
            }

            if (uiState.tickets.isEmpty()) {
              Text(
                text = stringResource(R.string.detail_no_tickets),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              uiState.tickets.forEach { ticket ->
                TicketRow(
                  ticket = ticket,
                  onToggleStatus = { onToggleTicketStatus(ticket) },
                  onOpen = { onOpenTicket(ticket.id) },
                )
              }
            }
          }
        }
      }

      // 4. Status Log
      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading(text = stringResource(R.string.detail_status_log_title))
            if (uiState.statusLogs.isEmpty()) {
              Text(
                text = stringResource(R.string.detail_no_logs),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              uiState.statusLogs.forEachIndexed { idx, log ->
                if (idx > 0) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
                StatusLogRow(log = log)
              }
            }
          }
        }
      }
    }
    }
  }

  // Edit Checklist Step Note Dialog
  val activeEditingStep = editingStep
  if (activeEditingStep != null) {
    var noteDraft by remember(activeEditingStep.stepIndex) { mutableStateOf(activeEditingStep.note) }
    AlertDialog(
      onDismissRequest = { editingStep = null },
      title = { Text(text = stringResource(R.string.checklist_note_dialog_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = localizedChecklistStepTitle(activeEditingStep),
            style = MaterialTheme.typography.titleSmall,
          )
          OutlinedTextField(
            value = noteDraft,
            onValueChange = { noteDraft = it },
            placeholder = { Text(text = stringResource(R.string.checklist_note_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().testTag("step_note_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveStepNote(activeEditingStep.stepIndex, noteDraft)
            editingStep = null
          },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(text = stringResource(R.string.dialog_save))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { editingStep = null },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(text = stringResource(R.string.dialog_cancel))
        }
      },
    )
  }

  // Cleaning Template Picker (occupied rooms only)
  val pickerRoom = room
  if (showTemplatePicker && pickerRoom != null) {
    AlertDialog(
      onDismissRequest = { showTemplatePicker = false },
      title = { Text(text = stringResource(R.string.template_picker_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = digitString(R.string.template_picker_body, pickerRoom.name),
            style = MaterialTheme.typography.bodyLarge,
          )
          Button(
            onClick = {
              showTemplatePicker = false
              onStartCleaning(ChecklistRunKind.FULL)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("template_option_full"),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = stringResource(R.string.template_full_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
              Text(
                text = stringResource(R.string.template_full_desc),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
          OutlinedButton(
            onClick = {
              showTemplatePicker = false
              onStartCleaning(ChecklistRunKind.STAYOVER)
            },
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("template_option_stayover"),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = stringResource(R.string.template_stayover_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
              Text(
                text = stringResource(R.string.template_stayover_desc),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(
          onClick = { showTemplatePicker = false },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(text = stringResource(R.string.dialog_cancel))
        }
      },
      modifier = Modifier.testTag("template_picker_dialog"),
    )
  }

  // Report Room Ticket Sheet (room fixed to this detail room)
  val sheetRoom = room
  if (showTicketDialog && sheetRoom != null) {
    TicketCreateSheet(
      rooms = listOf(sheetRoom),
      fixedRoomId = sheetRoom.id,
      staffName = uiState.session.staffName.orEmpty(),
      staffRoster = uiState.session.staffRoster,
      onSubmit = { _, title, description, priority, createdBy ->
        onCreateTicket(title, description, priority, createdBy)
        showTicketDialog = false
      },
      onDismiss = { showTicketDialog = false },
    )
  }
}

@Composable
private fun ChecklistStepRow(
  step: ChecklistStepState,
  enabled: Boolean,
  onToggle: () -> Unit,
  onEditNote: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clickable(enabled = enabled, onClick = onToggle)
        .padding(vertical = 4.dp)
        .testTag("checklist_step_${step.stepIndex}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Checkbox(
      checked = step.isDone,
      onCheckedChange = if (enabled) ({ onToggle() }) else null,
      enabled = enabled,
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = localizedChecklistStepTitle(step),
        style = MaterialTheme.typography.bodyLarge,
        color =
          if (step.isDone) MaterialTheme.colorScheme.onSurfaceVariant
          else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (step.isDone) TextDecoration.LineThrough else TextDecoration.None,
      )
      if (step.note.isNotBlank()) {
        Text(
          text = stringResource(R.string.checklist_note_label_format, step.note),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    if (enabled) {
      IconButton(
        onClick = onEditNote,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.EditNote,
          contentDescription = stringResource(R.string.cd_add_note),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TicketRow(
  ticket: TicketEntity,
  onToggleStatus: () -> Unit,
  onOpen: () -> Unit,
) {
  val (badgeBg, badgeFg) = ticket.priority.badgeColors()
  Surface(
    onClick = onOpen,
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.testTag("detail_ticket_${ticket.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = ticket.title,
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeBg,
            contentColor = badgeFg,
          ) {
            Text(
              text = stringResource(ticket.priority.labelRes),
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
          Text(
            text = stringResource(ticket.status.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      TextButton(
        onClick = onToggleStatus,
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

@Composable
private fun StatusLogRow(log: StatusLogEntity) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text =
        stringResource(
          R.string.status_log_transition_format,
          stringResource(log.fromStatus.labelRes),
          stringResource(log.toStatus.labelRes),
        ),
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
    )
    val reason = log.reason
    if (!reason.isNullOrBlank()) {
      Text(
        text = reason,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.error,
      )
    }
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
