package com.vivescriptsolutions.roomready.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.domain.ConnectionMode
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.formatTimeShort
import com.vivescriptsolutions.roomready.ui.locale.digitString
import com.vivescriptsolutions.roomready.ui.viewmodel.ConnectionMessage
import com.vivescriptsolutions.roomready.ui.viewmodel.ConnectionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
  uiState: ConnectionUiState,
  onBack: () -> Unit,
  onSetMode: (ConnectionMode) -> Unit,
  onSaveConnection: (String, String) -> Unit,
  onSyncNow: () -> Unit,
  onDisconnect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var urlDraft by remember(uiState.baseUrl) { mutableStateOf(uiState.baseUrl.orEmpty()) }
  var keyDraft by remember { mutableStateOf("") }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("connection_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.connection_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("connection_back_button"),
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
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        CalmSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              ConnectionMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                  selected = uiState.mode == mode,
                  onClick = { onSetMode(mode) },
                  shape = SegmentedButtonDefaults.itemShape(index, ConnectionMode.entries.size),
                  modifier =
                    Modifier.heightIn(min = 52.dp)
                      .testTag("connection_mode_${mode.name.lowercase()}"),
                ) {
                  Text(
                    text =
                      stringResource(
                        if (mode == ConnectionMode.DEMO) R.string.connection_mode_demo
                        else R.string.connection_mode_live
                      ),
                    style = MaterialTheme.typography.titleSmall,
                  )
                }
              }
            }
            if (uiState.mode == ConnectionMode.DEMO) {
              Text(
                text = stringResource(R.string.connection_demo_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (uiState.mode == ConnectionMode.LIVE) {
        item {
          CalmSectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text(text = stringResource(R.string.connection_url_label)) },
                placeholder = { Text(text = stringResource(R.string.connection_url_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("connection_url_input"),
              )
              OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text(text = stringResource(R.string.connection_key_label)) },
                placeholder = { Text(text = stringResource(R.string.connection_key_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("connection_key_input"),
              )
              Button(
                onClick = { onSaveConnection(urlDraft, keyDraft) },
                enabled = !uiState.syncing && urlDraft.isNotBlank() && keyDraft.isNotBlank(),
                modifier =
                  Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connection_save_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text(
                  text = stringResource(R.string.connection_save_connect),
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
              }
            }
          }
        }

        item {
          CalmSectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              val property = uiState.propertyName
              val businessDate = uiState.businessDate
              if (property != null && businessDate != null) {
                Text(
                  text = stringResource(R.string.connection_property_format, property, businessDate),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.syncing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag("connection_syncing_indicator"),
                  )
                }
                Text(
                  text =
                    when {
                      uiState.syncing -> stringResource(R.string.connection_status_syncing)
                      uiState.lastError != null ->
                        stringResource(R.string.connection_status_error_format, uiState.lastError)
                      uiState.lastSyncAt != null ->
                        digitString(
                          R.string.connection_status_synced_format,
                          formatTimeShort(uiState.lastSyncAt),
                          uiState.pendingCount,
                        )
                      else -> stringResource(R.string.connection_status_idle)
                    },
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(start = if (uiState.syncing) 8.dp else 0.dp),
                )
              }
              ConnectionMessageText(message = uiState.message)
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                  onClick = onSyncNow,
                  enabled = !uiState.syncing,
                  modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("connection_sync_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Text(text = stringResource(R.string.connection_sync_now))
                }
                OutlinedButton(
                  onClick = onDisconnect,
                  enabled = !uiState.syncing,
                  modifier =
                    Modifier.weight(1f).heightIn(min = 52.dp).testTag("connection_disconnect_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Text(text = stringResource(R.string.connection_disconnect))
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
private fun ConnectionMessageText(message: ConnectionMessage?) {
  val text =
    when (message) {
      null -> return
      is ConnectionMessage.Saved -> stringResource(R.string.connection_saved)
      is ConnectionMessage.Synced -> stringResource(R.string.connection_saved)
      is ConnectionMessage.Disconnected -> stringResource(R.string.connection_status_idle)
      is ConnectionMessage.Unauthorized -> stringResource(R.string.connection_unauthorized)
      is ConnectionMessage.UpdateRequired ->
        stringResource(R.string.connection_update_required)
      is ConnectionMessage.Failed ->
        stringResource(R.string.connection_save_failed_format, message.detail)
    }
  val isError =
    message is ConnectionMessage.Failed ||
      message is ConnectionMessage.Unauthorized ||
      message is ConnectionMessage.UpdateRequired
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
    color =
      if (isError) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.primary,
    modifier = Modifier.testTag("connection_message"),
  )
}
