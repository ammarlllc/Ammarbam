package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.example.model.InstalledAppInfo
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "ammar_share_prefs")

class ShareRepository(private val context: Context) {

  private object PrefKeys {
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val TURBO_MODE = booleanPreferencesKey("turbo_mode")
  }

  private val historyFile: File
    get() = File(context.filesDir, "transfer_history.json")

  val deviceNameFlow: Flow<String> =
    context.settingsDataStore.data.map { prefs ->
      prefs[PrefKeys.DEVICE_NAME] ?: Build.MODEL.ifBlank { "Ammar Phone" }
    }

  val turboModeFlow: Flow<Boolean> =
    context.settingsDataStore.data.map { prefs -> prefs[PrefKeys.TURBO_MODE] ?: true }

  suspend fun setDeviceName(name: String) {
    val clean = name.trim().take(28).ifEmpty { Build.MODEL }
    context.settingsDataStore.edit { it[PrefKeys.DEVICE_NAME] = clean }
  }

  suspend fun setTurboMode(enabled: Boolean) {
    context.settingsDataStore.edit { it[PrefKeys.TURBO_MODE] = enabled }
  }

  suspend fun loadInstalledApps(): List<InstalledAppInfo> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val launchIntent =
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
      val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.queryIntentActivities(
            launchIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          pm.queryIntentActivities(launchIntent, 0)
        }

      val seenPackages = HashSet<String>()
      val apps = ArrayList<InstalledAppInfo>()

      for (ri in resolveInfos) {
        val pkg = ri.activityInfo?.packageName ?: continue
        if (!seenPackages.add(pkg)) continue

        val appInfo =
          try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
              @Suppress("DEPRECATION")
              pm.getApplicationInfo(pkg, 0)
            }
          } catch (_: Exception) {
            continue
          }

        val apkPath = appInfo.sourceDir ?: continue
        val apkFile = File(apkPath)
        if (!apkFile.exists() || !apkFile.canRead()) continue

        val label = ri.loadLabel(pm).toString().ifBlank { pkg }
        val versionName =
          try {
            val pkgInfo =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
              } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
              }
            pkgInfo.versionName ?: "1.0"
          } catch (_: Exception) {
            "1.0"
          }

        apps.add(
          InstalledAppInfo(
            packageName = pkg,
            appName = label,
            versionName = versionName,
            apkPath = apkPath,
            sizeBytes = apkFile.length(),
          )
        )
      }

      apps.sortedBy { it.appName.lowercase() }
    }

  suspend fun resolvePickedUris(uris: List<Uri>, category: ShareCategory): List<ShareableItem> =
    withContext(Dispatchers.IO) {
      uris.mapNotNull { uri ->
        val cr = context.contentResolver
        var displayName = "file_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
          ?.use { cursor ->
            if (cursor.moveToFirst()) {
              val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
              if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                displayName = cursor.getString(nameIdx)
              }
              if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                sizeBytes = cursor.getLong(sizeIdx)
              }
            }
          }

        val mime = cr.getType(uri) ?: guessMimeType(displayName)
        ShareableItem(
          id = "uri_${uri.toString().hashCode()}_${displayName}",
          displayName = displayName,
          relativePath = displayName,
          sizeBytes = sizeBytes,
          mimeType = mime,
          category = category,
          uri = uri,
        )
      }
    }

  suspend fun resolvePickedFolder(treeUri: Uri): List<ShareableItem> =
    withContext(Dispatchers.IO) {
      val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
      val rootName = (root.name ?: "SharedFolder").replace(Regex("[/\\\\]"), "_")
      val items = ArrayList<ShareableItem>()

      fun walk(node: DocumentFile, currentRelPath: String) {
        for (child in node.listFiles()) {
          val childName = child.name ?: continue
          if (child.isDirectory) {
            walk(child, "$currentRelPath/$childName")
          } else if (child.isFile) {
            val relPath = "$currentRelPath/$childName"
            val mime = child.type ?: guessMimeType(childName)
            items.add(
              ShareableItem(
                id = "folder_${child.uri.toString().hashCode()}",
                displayName = childName,
                relativePath = relPath,
                sizeBytes = child.length(),
                mimeType = mime,
                category = ShareCategory.FOLDERS,
                uri = child.uri,
                folderGroupName = rootName,
              )
            )
          }
        }
      }

      walk(root, rootName)
      items
    }

  fun createIncomingOutputStream(
    relativePath: String,
    mimeType: String,
  ): Pair<OutputStream, SavedLocation> {
    val cleanRel =
      relativePath
        .replace("\\", "/")
        .split("/")
        .filter { it.isNotBlank() && it != "." && it != ".." }
        .joinToString("/")
        .ifEmpty { "received_${System.currentTimeMillis()}.bin" }

    val lastSlash = cleanRel.lastIndexOf('/')
    val subFolder = if (lastSlash > 0) cleanRel.substring(0, lastSlash) else ""
    val fileName = if (lastSlash >= 0) cleanRel.substring(lastSlash + 1) else cleanRel

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val mediaRelPath =
        if (subFolder.isNotEmpty()) "${Environment.DIRECTORY_DOWNLOADS}/AmmarShare/$subFolder"
        else "${Environment.DIRECTORY_DOWNLOADS}/AmmarShare"

      val values =
        ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, fileName)
          put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { guessMimeType(fileName) })
          put(MediaStore.Downloads.RELATIVE_PATH, mediaRelPath)
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
      val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
      if (uri != null) {
        val out = context.contentResolver.openOutputStream(uri)
        if (out != null) {
          return out to SavedLocation(uri = uri, filePath = null, mediaStorePending = true)
        }
      }
    }

    // Fallback for API 24-28 or app-private Downloads if MediaStore insert fails
    val baseDir =
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
          "AmmarShare",
        )
      } else {
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AmmarShare")
      }
    val targetFile = File(baseDir, cleanRel)
    targetFile.parentFile?.mkdirs()
    return FileOutputStream(targetFile) to
      SavedLocation(uri = null, filePath = targetFile.absolutePath, mediaStorePending = false)
  }

  fun finalizeSavedLocation(location: SavedLocation) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && location.mediaStorePending && location.uri != null) {
      try {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(location.uri, values, null, null)
      } catch (_: Exception) {}
    }
  }

  fun loadTransferHistory(): List<TransferRecord> {
    return try {
      if (!historyFile.exists()) return emptyList()
      val arr = JSONArray(historyFile.readText())
      val list = ArrayList<TransferRecord>(arr.length())
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        list.add(
          TransferRecord(
            id = obj.optString("id", UUID.randomUUID().toString()),
            fileName = obj.optString("fileName", "Unknown"),
            relativePath = obj.optString("relativePath", ""),
            sizeBytes = obj.optLong("sizeBytes", 0L),
            direction =
              TransferDirection.valueOf(obj.optString("direction", TransferDirection.RECEIVE.name)),
            category = ShareCategory.valueOf(obj.optString("category", ShareCategory.FILES.name)),
            timestampMs = obj.optLong("timestampMs", 0L),
            speedMBps = obj.optDouble("speedMBps", 0.0),
            peerName = obj.optString("peerName", "Nearby Device"),
            savedUriString = obj.optString("savedUriString").takeIf { it.isNotBlank() },
            savedFilePath = obj.optString("savedFilePath").takeIf { it.isNotBlank() },
            mimeType = obj.optString("mimeType", "application/octet-stream"),
          )
        )
      }
      list.sortedByDescending { it.timestampMs }
    } catch (_: Exception) {
      emptyList()
    }
  }

  @Synchronized
  fun appendHistoryRecords(newRecords: List<TransferRecord>) {
    if (newRecords.isEmpty()) return
    val existing = loadTransferHistory().toMutableList()
    existing.addAll(0, newRecords)
    val capped = existing.take(250)
    saveHistoryList(capped)
  }

  @Synchronized
  fun clearHistory() {
    saveHistoryList(emptyList())
  }

  private fun saveHistoryList(records: List<TransferRecord>) {
    val arr = JSONArray()
    for (r in records) {
      arr.put(
        JSONObject().apply {
          put("id", r.id)
          put("fileName", r.fileName)
          put("relativePath", r.relativePath)
          put("sizeBytes", r.sizeBytes)
          put("direction", r.direction.name)
          put("category", r.category.name)
          put("timestampMs", r.timestampMs)
          put("speedMBps", r.speedMBps)
          put("peerName", r.peerName)
          put("savedUriString", r.savedUriString ?: "")
          put("savedFilePath", r.savedFilePath ?: "")
          put("mimeType", r.mimeType)
        }
      )
    }
    historyFile.writeText(arr.toString())
  }

  fun openRecord(record: TransferRecord): Boolean {
    return try {
      val targetUri: Uri? =
        when {
          !record.savedUriString.isNullOrBlank() -> Uri.parse(record.savedUriString)
          !record.savedFilePath.isNullOrBlank() -> {
            val file = File(record.savedFilePath)
            if (file.exists()) {
              FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else null
          }
          else -> null
        }

      if (targetUri == null) return false
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(targetUri, record.mimeType.ifBlank { guessMimeType(record.fileName) })
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(Intent.createChooser(intent, "Open ${record.fileName}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      })
      true
    } catch (_: Exception) {
      false
    }
  }

  fun shareRecord(record: TransferRecord): Boolean {
    return try {
      val targetUri: Uri? =
        when {
          !record.savedUriString.isNullOrBlank() -> Uri.parse(record.savedUriString)
          !record.savedFilePath.isNullOrBlank() -> {
            val file = File(record.savedFilePath)
            if (file.exists()) {
              FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else null
          }
          else -> null
        }
      if (targetUri == null) return false
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = record.mimeType.ifBlank { "*/*" }
          putExtra(Intent.EXTRA_STREAM, targetUri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(
        Intent.createChooser(intent, "Share ${record.fileName}").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      )
      true
    } catch (_: Exception) {
      false
    }
  }

  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    return try {
      val hints = mapOf(EncodeHintType.MARGIN to 1)
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
      for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
          bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.parseColor("#130C24") else AndroidColor.WHITE)
        }
      }
      bmp
    } catch (_: Exception) {
      null
    }
  }

  companion object {
    fun guessMimeType(fileName: String): String {
      val ext = fileName.substringAfterLast('.', "").lowercase()
      if (ext == "apk") return "application/vnd.android.package-archive"
      return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun inferCategory(fileName: String, mimeType: String, relativePath: String): ShareCategory {
      if (relativePath.contains('/')) return ShareCategory.FOLDERS
      val lower = fileName.lowercase()
      return when {
        lower.endsWith(".apk") -> ShareCategory.APPS
        mimeType.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") ->
          ShareCategory.VIDEOS
        mimeType.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") ->
          ShareCategory.PHOTOS
        else -> ShareCategory.FILES
      }
    }
  }
}

data class SavedLocation(
  val uri: Uri?,
  val filePath: String?,
  val mediaStorePending: Boolean,
)
