package com.vivescriptsolutions.roomready.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.RoomStatusChip
import com.vivescriptsolutions.roomready.ui.components.localizedChecklistStepTitle
import com.vivescriptsolutions.roomready.ui.viewmodel.RoomDetailUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
  uiState: RoomDetailUiState,
  onBack: () -> Unit,
  onPass: () -> Unit,
  onReturn: (ReturnReason?, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  var showReturnSheet by remember { mutableStateOf(false) }

  val room = uiState.room
  val run = uiState.latestChecklistRun

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("inspection_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text =
              if (room != null) stringResource(R.string.inspection_title_format, room.name)
              else stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("inspection_back_button"),
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
    if (room == null || run == null) {
      Spacer(modifier = Modifier.padding(innerPadding))
      return@Scaffold
    }
    val elapsedMillis = (run.finishedAt ?: System.currentTimeMillis()) - run.startedAt

    LazyColumn(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .testTag("inspection_steps_list"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text =
                stringResource(
                  R.string.inspection_elapsed_format,
                  ChecklistTemplates.formatElapsed(elapsedMillis.coerceAtLeast(0L)),
                ),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("inspection_elapsed"),
            )
            Text(
              text = stringResource(R.string.checklist_by_format, run.performedBy),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            run.steps.forEachIndexed { index, step ->
              if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
              }
              InspectionStepRow(step = step)
            }
          }
        }
      }

      item {
        Button(
          onClick = onPass,
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("inspection_pass_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = stringResource(R.string.inspection_pass),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }

      item {
        OutlinedButton(
          onClick = { showReturnSheet = true },
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("inspection_return_button"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
          Text(
            text = stringResource(R.string.inspection_return),
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }
  }

  if (showReturnSheet) {
    var reasonDraft by remember { mutableStateOf<ReturnReason?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    ModalBottomSheet(
      onDismissRequest = { showReturnSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      modifier = Modifier.testTag("inspection_return_sheet"),
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(R.string.return_sheet_title),
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.return_sheet_body),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        items(ReturnReason.entries) { reason ->
          FilterChip(
            selected = reason == reasonDraft,
            onClick = { reasonDraft = reason },
            label = {
              Text(
                text = stringResource(reason.labelRes),
                style = MaterialTheme.typography.titleSmall,
              )
            },
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .testTag("return_reason_${reason.name.lowercase()}"),
          )
        }
        item {
          OutlinedTextField(
            value = noteDraft,
            onValueChange = { noteDraft = it },
            placeholder = { Text(text = stringResource(R.string.return_note_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("return_note_input"),
          )
        }
        item {
          Button(
            onClick = {
              onReturn(reasonDraft, noteDraft)
              showReturnSheet = false
            },
            enabled = reasonDraft != null,
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("return_confirm_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = stringResource(R.string.return_confirm),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
          Spacer(modifier = Modifier.size(24.dp))
        }
      }
    }
  }
}

@Composable
private fun InspectionStepRow(step: ChecklistStepState) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .testTag("inspection_step_${step.stepIndex}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Checkbox(
      checked = step.isDone,
      onCheckedChange = null,
      modifier = Modifier.size(48.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = localizedChecklistStepTitle(step),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (step.note.isNotBlank()) {
        Text(
          text = stringResource(R.string.checklist_note_label_format, step.note),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
