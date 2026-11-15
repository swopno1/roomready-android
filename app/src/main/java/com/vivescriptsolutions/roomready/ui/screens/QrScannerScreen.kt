package com.vivescriptsolutions.roomready.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.sync.PairingQrParser
import com.vivescriptsolutions.roomready.data.sync.PairingQrPayload
import com.vivescriptsolutions.roomready.data.sync.PairingQrResult
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface QrScanError {
  data object InvalidCode : QrScanError

  data object UpdateRequired : QrScanError
}

/**
 * Offline QR pairing scanner built on CameraX + bundled ML Kit barcode scanning.
 * Reuses the existing CAMERA permission with the same plain-language rationale pattern.
 * Raw scanned payloads and API keys are never logged or written outside the Keystore-backed
 * connection flow, and frames are discarded after the first valid code or while an error is shown.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
  onPayloadScanned: (PairingQrPayload) -> Unit,
  onTypeInstead: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  initialError: QrScanError? = null,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED

  var granted by remember { mutableStateOf(hasPermission()) }
  var cameraUnavailable by remember { mutableStateOf(false) }
  var scanError by remember(initialError) { mutableStateOf(initialError) }
  val scanLocked = remember { AtomicBoolean(initialError != null) }
  val screenActive = remember { AtomicBoolean(true) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      granted = isGranted
    }

  fun openAppSettings() {
    val intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
      }
    context.startActivity(intent)
  }

  fun handleScannedRaw(raw: String) {
    if (!screenActive.get()) return
    if (!scanLocked.compareAndSet(false, true)) return
    when (val parsed = PairingQrParser.parse(raw)) {
      is PairingQrResult.Valid -> {
        screenActive.set(false)
        onPayloadScanned(parsed.payload)
      }
      PairingQrResult.UpdateRequired -> scanError = QrScanError.UpdateRequired
      PairingQrResult.InvalidCode -> scanError = QrScanError.InvalidCode
    }
  }

  var barcodeScanner by remember { mutableStateOf<BarcodeScanner?>(null) }
  val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
  var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      screenActive.set(false)
      scanLocked.set(true)
      boundProvider?.unbindAll()
      barcodeScanner?.close()
      analysisExecutor.shutdown()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("qr_scanner_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.qr_scanner_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              screenActive.set(false)
              scanLocked.set(true)
              onBack()
            },
            modifier = Modifier.size(48.dp).testTag("qr_back_button"),
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
        CalmSectionCard(modifier = Modifier.testTag("qr_rationale")) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = stringResource(R.string.qr_rationale_title),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = stringResource(R.string.qr_rationale_body),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.heightIn(min = 52.dp).testTag("qr_allow_button"),
              ) {
                Text(text = stringResource(R.string.photo_allow_button))
              }
              TextButton(
                onClick = ::openAppSettings,
                modifier = Modifier.heightIn(min = 52.dp).testTag("qr_open_settings_button"),
              ) {
                Text(text = stringResource(R.string.photo_open_settings))
              }
            }
          }
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
          onClick = {
            screenActive.set(false)
            onTypeInstead()
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("qr_type_instead_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(text = stringResource(R.string.qr_type_instead_button))
        }
      } else if (cameraUnavailable) {
        CalmSectionCard(modifier = Modifier.testTag("qr_camera_unavailable")) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = stringResource(R.string.qr_camera_unavailable),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = {
                screenActive.set(false)
                onTypeInstead()
              },
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("qr_type_instead_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(text = stringResource(R.string.qr_type_instead_button))
            }
          }
        }
      } else {
        CalmSectionCard(modifier = Modifier.testTag("qr_scanner_hint")) {
          Text(
            text = stringResource(R.string.qr_scanner_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          AndroidView(
            factory = { ctx ->
              PreviewView(ctx).also { previewView ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener(
                  {
                    if (!screenActive.get()) return@addListener
                    val provider =
                      try {
                        future.get()
                      } catch (_: Exception) {
                        cameraUnavailable = true
                        return@addListener
                      }
                    boundProvider = provider
                    val scanner =
                      barcodeScanner
                        ?: try {
                          val options =
                            BarcodeScannerOptions.Builder()
                              .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                              .build()
                          BarcodeScanning.getClient(options).also { barcodeScanner = it }
                        } catch (_: Exception) {
                          cameraUnavailable = true
                          return@addListener
                        }
                    val preview = Preview.Builder().build().also {
                      it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis =
                      ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                          imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeFrame(
                              imageProxy = imageProxy,
                              scanner = scanner,
                              shouldProcess = { screenActive.get() && !scanLocked.get() },
                              mainExecutor = ContextCompat.getMainExecutor(ctx),
                              onCode = ::handleScannedRaw,
                            )
                          }
                        }
                    try {
                      provider.unbindAll()
                      provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                      )
                    } catch (_: Exception) {
                      cameraUnavailable = true
                    }
                  },
                  ContextCompat.getMainExecutor(ctx),
                )
              }
            },
            modifier = Modifier.fillMaxSize().testTag("qr_preview"),
          )
        }

        val currentError = scanError
        if (currentError != null) {
          QrScanErrorCard(
            error = currentError,
            onRescan = {
              scanError = null
              scanLocked.set(false)
            },
            onTypeInstead = {
              screenActive.set(false)
              scanLocked.set(true)
              onTypeInstead()
            },
          )
        } else {
          OutlinedButton(
            onClick = {
              screenActive.set(false)
              scanLocked.set(true)
              onTypeInstead()
            },
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("qr_type_instead_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(text = stringResource(R.string.qr_type_instead_button))
          }
        }
      }
    }
  }
}

@Composable
internal fun QrScanErrorCard(
  error: QrScanError,
  onRescan: () -> Unit,
  onTypeInstead: () -> Unit,
  modifier: Modifier = Modifier,
) {
  CalmSectionCard(modifier = modifier.testTag("qr_error_card")) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = stringResource(R.string.qr_invalid_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.error,
      )
      Text(
        text =
          stringResource(
            when (error) {
              QrScanError.InvalidCode -> R.string.qr_invalid_message
              QrScanError.UpdateRequired -> R.string.qr_update_required_message
            }
          ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("qr_error_message"),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
          onClick = onRescan,
          modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("qr_rescan_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(text = stringResource(R.string.qr_rescan_button))
        }
        OutlinedButton(
          onClick = onTypeInstead,
          modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("qr_type_instead_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(text = stringResource(R.string.qr_type_instead_button))
        }
      }
    }
  }
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
  imageProxy: ImageProxy,
  scanner: BarcodeScanner,
  shouldProcess: () -> Boolean,
  mainExecutor: java.util.concurrent.Executor,
  onCode: (String) -> Unit,
) {
  if (!shouldProcess()) {
    imageProxy.close()
    return
  }
  val mediaImage = imageProxy.image
  if (mediaImage == null) {
    imageProxy.close()
    return
  }
  val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
  scanner
    .process(inputImage)
    .addOnSuccessListener(mainExecutor) { barcodes ->
      if (!shouldProcess()) return@addOnSuccessListener
      val value = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
      if (value != null) {
        onCode(value)
      }
    }
    .addOnCompleteListener {
      imageProxy.close()
    }
}
