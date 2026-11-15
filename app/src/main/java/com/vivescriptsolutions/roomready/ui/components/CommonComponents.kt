package com.vivescriptsolutions.roomready.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.vivescriptsolutions.roomready.ui.locale.digitString
import com.vivescriptsolutions.roomready.ui.locale.localizedDigits
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.photos.PhotoStore
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.ui.theme.colorSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun RoomStatus.icon(): ImageVector =
  when (this) {
    RoomStatus.DIRTY -> Icons.Filled.ReportProblem
    RoomStatus.CLEANING -> Icons.Filled.CleaningServices
    RoomStatus.CLEANED -> Icons.Filled.Verified
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
fun localizedFloorLabel(floor: Int, block: String? = null): String {
  if (floor < 0) {
    return if (!block.isNullOrBlank()) {
      digitString(R.string.floor_block_format, block)
    } else {
      stringResource(R.string.floor_unknown)
    }
  }
  return if (floor == 0) {
    stringResource(R.string.floor_ground)
  } else {
    digitString(R.string.floor_number, floor)
  }
}

@Composable
fun localizedChecklistStepTitle(step: ChecklistStepState): String {
  val labelRes = ChecklistTemplates.labelResForKey(step.key.ifBlank { step.title })
  return if (labelRes != null) stringResource(labelRes) else step.title
}

/**
 * Evidence photo thumbnail with an explicit share action. Renders nothing
 * when there is no photo or the file is unreadable. Sharing goes through
 * FileProvider and only fires on tap.
 */
@Composable
fun EvidencePhotoThumbnail(
  photoName: String?,
  onShare: (String) -> Unit,
  modifier: Modifier = Modifier,
  testTagPrefix: String = "photo",
) {
  if (photoName.isNullOrBlank()) return
  val context = LocalContext.current
  val store = remember(context) { PhotoStore(context) }
  val bitmap = remember(photoName) { store.loadThumbnail(photoName) }
  if (bitmap == null) return
  Row(
    modifier = modifier.testTag("${testTagPrefix}_thumbnail"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier =
        Modifier.size(72.dp)
          .clip(RoundedCornerShape(8.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    )
    IconButton(
      onClick = { onShare(photoName) },
      modifier = Modifier.size(48.dp).testTag("${testTagPrefix}_share_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.Share,
        contentDescription = stringResource(R.string.cd_share_photo),
      )
    }
  }
}

@Composable
fun formatTimeShort(millis: Long): String {
  val formatter = remember(millis) { SimpleDateFormat("h:mm a", Locale.getDefault()) }
  return localizedDigits(formatter.format(Date(millis)))
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
  contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
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
        .padding(contentPadding),
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
