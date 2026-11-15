package com.vivescriptsolutions.roomready.data.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-private evidence photo storage (`files/photos/`). Photos never leave
 * the device except through [PhotoShare], which fires only on explicit
 * user tap via FileProvider.
 */
class PhotoStore(context: Context) {
  private val photosDir: File =
    File(context.applicationContext.filesDir, "photos").apply { mkdirs() }

  fun newPhotoFile(prefix: String): File {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(photosDir, "${prefix}_${stamp}.jpg")
  }

  fun savePhoto(prefix: String, bytes: ByteArray): String {
    val file = newPhotoFile(prefix)
    file.writeBytes(bytes)
    return file.name
  }

  fun photoFile(name: String): File = File(photosDir, File(name).name)

  fun exists(name: String?): Boolean = name != null && photoFile(name).exists()

  /** Decodes a memory-safe downsampled bitmap, or null when unreadable. */
  fun loadThumbnail(name: String, maxDimPx: Int = 512): android.graphics.Bitmap? {
    val file = photoFile(name)
    if (!file.exists()) return null
    return runCatching {
      val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
      android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
      val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
      var sample = 1
      while (longest / (sample * 2) >= maxDimPx) sample *= 2
      val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
      android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
    }.getOrNull()
  }
}

object PhotoShare {
  fun sharePhoto(context: Context, store: PhotoStore, photoName: String) {
    val file = store.photoFile(photoName)
    if (!file.exists()) return
    val uri: Uri =
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(intent, null))
  }
}
