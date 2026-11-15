package com.vivescriptsolutions.roomready.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import com.vivescriptsolutions.roomready.domain.formatReturnReason
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.localizedChecklistStepTitle
import com.vivescriptsolutions.roomready.ui.locale.digitString
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailUiState
import kotlinx.coroutines.delay

/**
 * Active turnover run: large step rows, live progress + elapsed timer, and a
 * Finish button that enables only when every step is checked. Leaving this
 * screen (back or Pause) keeps the run alive in Room; resume from room detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRunScreen(
  uiState: RoomDetailUiState,
  onBack: () -> Unit,
  onToggleStep: (Int) -> Unit,
  onSaveStepNote: (Int, String) -> Unit,
  onFinish: () -> Unit,
  onOpenTicket: (Long) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  var editingStep by remember { mutableStateOf<ChecklistStepState?>(null) }

  val room = uiState.room
  val run = uiState.latestChecklistRun
  val steps = run?.steps.orEmpty()
  val doneCount = ChecklistTemplates.doneCount(steps)
  val canFinish = run?.finishedAt == null && ChecklistTemplates.canFinish(steps)

  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(run?.id) {
    while (true) {
      delay(1000)
      nowMillis = System.currentTimeMillis()
    }
  }
  val elapsedText =
    if (run != null) {
      ChecklistTemplates.formatElapsed((nowMillis - run.startedAt).coerceAtLeast(0L))
    } else {
      ChecklistTemplates.formatElapsed(0L)
    }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("run_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text =
              if (room != null) {
                digitString(R.string.run_title_format, room.name)
              } else {
                stringResource(R.string.app_name)
              },
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("run_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        actions = {
          TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp).testTag("run_pause_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Pause,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = stringResource(R.string.run_pause))
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
    if (room == null || run == null) {
      Spacer(modifier = Modifier.padding(innerPadding))
      return@Scaffold
    }

    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
    LazyColumn(
      modifier =
        Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize()
          .testTag("run_steps_list"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val returnReasonKey = run.returnReasonKey
      if (run.finishedAt == null && returnReasonKey != null) {
        item(key = "returned_banner") {
          val chipLabel =
            runCatching { ReturnReason.valueOf(returnReasonKey) }
              .map { stringResource(it.labelRes) }
              .getOrDefault(returnReasonKey)
          androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().testTag("run_returned_banner"),
          ) {
            Text(
              text =
                stringResource(
                  R.string.run_returned_banner_format,
                  formatReturnReason(chipLabel, run.returnNote),
                ),
              style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }

      val urgentTickets =
        uiState.tickets.filter {
          it.status == TicketStatus.OPEN && it.priority == TicketPriority.URGENT
        }
      urgentTickets.forEach { ticket ->
        item(key = "urgent_${ticket.id}") {
          androidx.compose.material3.Surface(
            onClick = { onOpenTicket(ticket.id) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().testTag("run_urgent_ticket_banner"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = stringResource(R.string.run_urgent_banner_title),
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                  text = ticket.title,
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = 2,
                )
              }
              Text(
                text = stringResource(R.string.run_urgent_view),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }

      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text =
                  stringResource(
                    if (run.runKind == ChecklistRunKind.STAYOVER) {
                      R.string.run_kind_stayover
                    } else {
                      R.string.run_kind_full
                    }
                  ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = digitString(R.string.run_elapsed_format, elapsedText),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("run_elapsed_timer"),
              )
            }
            LinearProgressIndicator(
              progress = { if (steps.isEmpty()) 0f else doneCount.toFloat() / steps.size },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text = digitString(R.string.checklist_progress_format, doneCount, steps.size),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("run_progress_text"),
            )
            Text(
              text = stringResource(R.string.run_started_by_format, run.performedBy),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      itemsIndexed(steps, key = { _, step -> step.stepIndex }) { _, step ->
        RunStepRow(
          step = step,
          onToggle = { onToggleStep(step.stepIndex) },
          onEditNote = { editingStep = step },
        )
      }

      item {
        Button(
          onClick = onFinish,
          enabled = canFinish,
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(min = 56.dp)
              .testTag("run_finish_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = stringResource(R.string.run_finish),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
        if (!canFinish) {
          Text(
            text = digitString(R.string.run_finish_locked_hint, steps.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        OutlinedButton(
          onClick = onBack,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
              .heightIn(min = 52.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(text = stringResource(R.string.run_pause))
        }
      }
    }
    }
  }

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
            modifier = Modifier.fillMaxWidth().testTag("run_step_note_input"),
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
}

@Composable
private fun RunStepRow(
  step: ChecklistStepState,
  onToggle: () -> Unit,
  onEditNote: () -> Unit,
) {
  CalmSectionCard {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 72.dp)
          .clickable(onClick = onToggle)
          .testTag("run_step_${step.stepIndex}"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Checkbox(
        checked = step.isDone,
        onCheckedChange = { onToggle() },
        modifier = Modifier.size(48.dp).testTag("run_step_checkbox_${step.stepIndex}"),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = localizedChecklistStepTitle(step),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
      IconButton(
        onClick = onEditNote,
        modifier = Modifier.size(48.dp).testTag("run_step_note_button_${step.stepIndex}"),
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
