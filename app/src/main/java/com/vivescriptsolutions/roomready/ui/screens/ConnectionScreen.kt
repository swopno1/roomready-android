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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
  onSaveConnection: (String, String) -> Unit,
  onSyncNow: () -> Unit,
  onDisconnect: () -> Unit,
  modifier: Modifier = Modifier,
  isSetup: Boolean = false,
  onScanQr: () -> Unit = {},
  onScannedPairingApplied: () -> Unit = {},
) {
  var urlDraft by remember(uiState.baseUrl) { mutableStateOf(uiState.baseUrl.orEmpty()) }
  var keyDraft by remember { mutableStateOf("") }
  var showDisconnectConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.scannedPairing?.eventId) {
    val scanned = uiState.scannedPairing ?: return@LaunchedEffect
    urlDraft = scanned.baseUrl
    keyDraft = scanned.apiKey
    onScannedPairingApplied()
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("connection_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text =
              stringResource(
                if (isSetup) R.string.connection_setup_title else R.string.connection_title
              ),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          if (!isSetup) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.size(48.dp).testTag("connection_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
              )
            }
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
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (isSetup) {
        item {
          Text(
            text = stringResource(R.string.connection_setup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item {
        CalmSectionCard(contentPadding = 12.dp) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
              onClick = onScanQr,
              enabled = !uiState.syncing,
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("connection_scan_qr_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.connection_scan_qr_button),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              )
            }
            OutlinedTextField(
              value = urlDraft,
              onValueChange = { urlDraft = it },
              label = { Text(text = stringResource(R.string.connection_url_label)) },
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
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("connection_save_button"),
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
        CalmSectionCard(contentPadding = 12.dp) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (uiState.isConfigured) {
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                  onClick = onSyncNow,
                  enabled = !uiState.syncing,
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("connection_sync_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Text(text = stringResource(R.string.connection_sync_now))
                }
                OutlinedButton(
                  onClick = {
                    if (uiState.pendingCount > 0) showDisconnectConfirm = true
                    else onDisconnect()
                  },
                  enabled = !uiState.syncing,
                  modifier =
                    Modifier.weight(1f).heightIn(min = 48.dp).testTag("connection_disconnect_button"),
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

  if (showDisconnectConfirm) {
    AlertDialog(
      onDismissRequest = { showDisconnectConfirm = false },
      title = { Text(text = stringResource(R.string.connection_disconnect_confirm_title)) },
      text = {
        Text(
          text =
            digitString(
              R.string.connection_disconnect_confirm_body,
              uiState.pendingCount,
            )
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDisconnectConfirm = false
            onDisconnect()
          },
          modifier = Modifier.testTag("disconnect_confirm_button"),
        ) {
          Text(text = stringResource(R.string.connection_disconnect_confirm_button))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDisconnectConfirm = false },
          modifier = Modifier.testTag("disconnect_cancel_button"),
        ) {
          Text(text = stringResource(R.string.dialog_cancel))
        }
      },
      modifier = Modifier.testTag("disconnect_confirm_dialog"),
    )
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
