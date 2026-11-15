package com.vivescriptsolutions.roomready.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.local.DemoSeeder
import com.vivescriptsolutions.roomready.domain.ChecklistStepKey
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.ui.theme.colorSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun RoomStatus.icon(): ImageVector =
  when (this) {
    RoomStatus.DIRTY -> Icons.Filled.ReportProblem
    RoomStatus.CLEANING -> Icons.Filled.CleaningServices
    RoomStatus.INSPECTED -> Icons.Filled.Verified
    RoomStatus.READY -> Icons.Filled.CheckCircle
    RoomStatus.OUT_OF_SERVICE -> Icons.Filled.Build
  }

@Composable
fun RoomStatusChip(
  status: RoomStatus,
  modifier: Modifier = Modifier,
  large: Boolean = true,
) {
  val colors = status.colorSpec()
  val label = stringResource(status.labelRes)
  Surface(
    modifier =
      modifier
        .heightIn(min = if (large) 40.dp else 34.dp)
        .testTag("status_chip_${status.name.lowercase()}"),
    shape = RoundedCornerShape(12.dp),
    color = colors.chipBackground,
    contentColor = colors.chipContent,
  ) {
    Row(
      modifier =
        Modifier.padding(
          horizontal = if (large) 14.dp else 10.dp,
          vertical = if (large) 8.dp else 6.dp,
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = status.icon(),
        contentDescription = stringResource(R.string.cd_status_icon),
        modifier = Modifier.size(if (large) 18.dp else 16.dp),
      )
      Text(
        text = label,
        style =
          MaterialTheme.typography.labelLarge.copy(
            fontSize = if (large) 15.sp else 14.sp,
            fontWeight = FontWeight.Bold,
          ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun DemoModePill(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.testTag("demo_mode_badge"),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Text(
      text = stringResource(R.string.demo_mode_badge),
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun OfflineReadyPill(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.CloudOff,
        contentDescription = stringResource(R.string.cd_offline_icon),
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = stringResource(R.string.offline_badge),
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }
}

@Composable
fun localizedFloorLabel(floor: Int): String =
  if (floor == 0) {
    stringResource(R.string.floor_ground)
  } else {
    stringResource(R.string.floor_number, floor)
  }

@Composable
fun localizedRoomTypeName(rawName: String): String =
  when (rawName) {
    DemoSeeder.TYPE_DELUXE_KING -> stringResource(R.string.room_type_deluxe_king)
    DemoSeeder.TYPE_FAMILY_SUITE -> stringResource(R.string.room_type_family_suite)
    DemoSeeder.TYPE_GARDEN_COTTAGE -> stringResource(R.string.room_type_garden_cottage)
    else -> rawName
  }

@Composable
fun localizedChecklistStepTitle(step: ChecklistStepState): String {
  val known = ChecklistStepKey.fromKeyOrTitle(step.key.ifBlank { step.title })
  return if (known != null) stringResource(known.labelRes) else step.title
}

fun formatTimeShort(millis: Long): String {
  val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
  return formatter.format(Date(millis))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffPickerSection(
  roster: List<String>,
  selectedName: String?,
  onSelectName: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var customName by remember { mutableStateOf("") }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      roster.forEach { name ->
        val isSelected = name == selectedName
        FilterChip(
          selected = isSelected,
          onClick = { onSelectName(name) },
          label = {
            Text(
              text = name,
              style = MaterialTheme.typography.bodyLarge,
            )
          },
          modifier =
            Modifier.heightIn(min = 48.dp).testTag("staff_chip_${name.lowercase().replace(" ", "_")}"),
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              containerColor = MaterialTheme.colorScheme.surface,
              labelColor = MaterialTheme.colorScheme.onSurface,
            ),
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSelected,
              borderColor = MaterialTheme.colorScheme.outline,
              selectedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = customName,
        onValueChange = { customName = it },
        label = { Text(text = stringResource(R.string.staff_custom_hint)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f).testTag("custom_staff_input"),
      )
      Button(
        onClick = {
          val trimmed = customName.trim()
          if (trimmed.isNotEmpty()) {
            onSelectName(trimmed)
            customName = ""
          }
        },
        enabled = customName.trim().isNotEmpty(),
        modifier = Modifier.heightIn(min = 52.dp).testTag("add_custom_staff_button"),
      ) {
        Text(text = stringResource(R.string.staff_add_button))
      }
    }
  }
}

@Composable
fun CalmSectionCard(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .background(
          color = MaterialTheme.colorScheme.surface,
          shape = MaterialTheme.shapes.medium,
        )
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outline,
          shape = MaterialTheme.shapes.medium,
        )
        .padding(16.dp),
  ) {
    content()
  }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = modifier,
  )
  Spacer(modifier = Modifier.height(4.dp))
}
