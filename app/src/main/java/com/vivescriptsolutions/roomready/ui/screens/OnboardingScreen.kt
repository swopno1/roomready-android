package com.vivescriptsolutions.roomready.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.vivescriptsolutions.roomready.domain.StaffRole
import com.vivescriptsolutions.roomready.ui.components.OfflineReadyPill
import com.vivescriptsolutions.roomready.ui.components.StaffPickerSection

@Composable
fun OnboardingScreen(
  staffRoster: List<String>,
  initialRole: StaffRole?,
  initialStaffName: String?,
  onComplete: (StaffRole, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedRole by remember(initialRole) { mutableStateOf(initialRole) }
  var selectedStaffName by remember(initialStaffName) { mutableStateOf(initialStaffName) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OfflineReadyPill()
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.onboarding_title),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.onboarding_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = stringResource(R.string.onboarding_step_role),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        RoleSelectionCard(
          role = StaffRole.HOUSEKEEPER,
          selected = selectedRole == StaffRole.HOUSEKEEPER,
          onClick = { selectedRole = StaffRole.HOUSEKEEPER },
        )
        RoleSelectionCard(
          role = StaffRole.SUPERVISOR,
          selected = selectedRole == StaffRole.SUPERVISOR,
          onClick = { selectedRole = StaffRole.SUPERVISOR },
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = stringResource(R.string.onboarding_step_staff),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        StaffPickerSection(
          roster = staffRoster,
          selectedName = selectedStaffName,
          onSelectName = { selectedStaffName = it },
        )
      }

      val canContinue = selectedRole != null && !selectedStaffName.isNullOrBlank()
      if (!canContinue) {
        Text(
          text = stringResource(R.string.onboarding_select_prompt),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Button(
        onClick = {
          val role = selectedRole
          val staff = selectedStaffName?.trim()
          if (role != null && !staff.isNullOrEmpty()) {
            onComplete(role, staff)
          }
        },
        enabled = canContinue,
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("onboarding_continue_button"),
        shape = MaterialTheme.shapes.medium,
      ) {
        Text(
          text = stringResource(R.string.onboarding_continue),
          style = MaterialTheme.typography.titleSmall,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
fun RoleSelectionCard(
  role: StaffRole,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val icon =
    when (role) {
      StaffRole.HOUSEKEEPER -> Icons.Filled.CleaningServices
      StaffRole.SUPERVISOR -> Icons.Filled.VerifiedUser
      StaffRole.SYSTEM -> Icons.Filled.Sync
    }
  Surface(
    onClick = onClick,
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
        .testTag("role_card_${role.name.lowercase()}"),
    shape = MaterialTheme.shapes.medium,
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outline,
      ),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurfaceVariant,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = stringResource(R.string.cd_role_icon),
          modifier = Modifier.padding(10.dp).size(24.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(role.labelRes),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(role.descriptionRes),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (selected) {
        Icon(
          imageVector = Icons.Filled.Badge,
          contentDescription = stringResource(R.string.cd_role_icon),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}
