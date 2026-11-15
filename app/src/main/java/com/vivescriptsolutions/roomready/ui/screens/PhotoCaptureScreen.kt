package com.vivescriptsolutions.roomready.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.photos.PhotoStore
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import java.io.File

sealed interface PhotoCaptureMode {
  data class Run(val roomName: String) : PhotoCaptureMode

  data object Ticket : PhotoCaptureMode
}

/**
 * Evidence photo capture. Run mode requires a photo or a one-line skip
 * reason; ticket mode treats the photo as optional. The camera permission
 * is requested on first use with a plain-language rationale; a denied
 * permission degrades gracefully to the skip form (run) or plain back
 * navigation (ticket). Captured files land in app-private storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(
  mode: PhotoCaptureMode,
  onPhotoTaken: (File) -> Unit,
  onSkip: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val store = remember(context) { PhotoStore(context) }

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED

  var granted by remember { mutableStateOf(hasPermission()) }
  var showSkipForm by remember { mutableStateOf(mode is PhotoCaptureMode.Run && !granted) }
  var skipReason by remember { mutableStateOf("") }
  var captured by remember { mutableStateOf<File?>(null) }
  val imageCapture = remember {
    ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      granted = isGranted
      if (!isGranted && mode is PhotoCaptureMode.Run) showSkipForm = true
    }

  fun openAppSettings() {
    val intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
      }
    context.startActivity(intent)
  }

  fun takePhoto() {
    val prefix = if (mode is PhotoCaptureMode.Run) "run" else "ticket"
    val file = store.newPhotoFile(prefix)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
      options,
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
          captured = file
        }

        override fun onError(exception: ImageCaptureException) {
          // Capture failed (e.g., no camera on low-end hardware): fall back
          // to the skip path for runs instead of stranding the user.
          if (mode is PhotoCaptureMode.Run) showSkipForm = true
        }
      },
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("photo_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text =
              when (mode) {
                is PhotoCaptureMode.Run -> stringResource(R.string.photo_title_run_format, mode.roomName)
                PhotoCaptureMode.Ticket -> stringResource(R.string.photo_title_ticket)
              },
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("photo_back_button"),
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
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!granted) {
        CalmSectionCard(modifier = Modifier.testTag("photo_rationale")) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.photo_rationale_title),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = stringResource(R.string.photo_rationale_body),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.heightIn(min = 52.dp).testTag("photo_allow_button"),
              ) {
                Text(text = stringResource(R.string.photo_allow_button))
              }
              TextButton(
                onClick = ::openAppSettings,
                modifier = Modifier.heightIn(min = 52.dp).testTag("photo_open_settings_button"),
              ) {
                Text(text = stringResource(R.string.photo_open_settings))
              }
            }
          }
        }
      }

      val capturedFile = captured
      if (granted && capturedFile == null) {
        AndroidView(
          factory = { ctx ->
            PreviewView(ctx).also { previewView ->
              val future = ProcessCameraProvider.getInstance(ctx)
              future.addListener(
                {
                  val provider = future.get()
                  val preview = Preview.Builder().build()
                  preview.surfaceProvider = previewView.surfaceProvider
                  try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                      lifecycleOwner,
                      CameraSelector.DEFAULT_BACK_CAMERA,
                      preview,
                      imageCapture,
                    )
                  } catch (_: Exception) {
                    // No usable camera: fall through to the skip path below.
                  }
                },
                ContextCompat.getMainExecutor(ctx),
              )
            }
          },
          modifier = Modifier.fillMaxWidth().weight(1f).testTag("photo_preview"),
        )
        Button(
          onClick = ::takePhoto,
          modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("photo_capture_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = stringResource(R.string.photo_capture_button),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
        if (mode is PhotoCaptureMode.Run && !showSkipForm) {
          OutlinedButton(
            onClick = { showSkipForm = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("photo_skip_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(text = stringResource(R.string.photo_skip))
          }
        }
      }

      if (capturedFile != null) {
        val bitmap = remember(capturedFile) { store.loadThumbnail(capturedFile.name, 1080) }
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("photo_captured_preview"),
          )
        } else {
          Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedButton(
            onClick = {
              capturedFile.delete()
              captured = null
            },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("photo_retake_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(text = stringResource(R.string.photo_retake))
          }
          Button(
            onClick = { onPhotoTaken(capturedFile) },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("photo_use_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = stringResource(R.string.photo_use),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }

      if (mode is PhotoCaptureMode.Run && showSkipForm && capturedFile == null) {
        CalmSectionCard(modifier = Modifier.testTag("photo_skip_form")) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.photo_denied_title),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = stringResource(R.string.photo_denied_body),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = skipReason,
              onValueChange = { skipReason = it },
              placeholder = { Text(text = stringResource(R.string.photo_skip_reason_hint)) },
              textStyle = MaterialTheme.typography.bodyLarge,
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("photo_skip_reason_input"),
            )
            Button(
              onClick = { onSkip(skipReason) },
              enabled = skipReason.isNotBlank(),
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("photo_skip_confirm_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(
                text = stringResource(R.string.photo_skip_confirm),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}
