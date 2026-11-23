package com.example.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.example.model.InstallResult
import com.example.model.InstalledAppInfo
import com.example.model.LocalMediaEntry
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "fast_share_prefs")

class ShareRepository(private val context: Context) {

  private object PrefKeys {
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val TURBO_MODE = booleanPreferencesKey("turbo_mode")
  }

  private val historyFile: File
    get() = File(context.filesDir, "transfer_history.json")

  val deviceNameFlow: Flow<String> =
    context.settingsDataStore.data.map { prefs ->
      prefs[PrefKeys.DEVICE_NAME] ?: (Build.MODEL ?: "FastShare Phone").ifBlank { "FastShare Phone" }
    }

  val turboModeFlow: Flow<Boolean> =
    context.settingsDataStore.data.map { prefs -> prefs[PrefKeys.TURBO_MODE] ?: true }

  suspend fun setDeviceName(name: String) {
    val fallback = (Build.MODEL ?: "FastShare Phone").ifBlank { "FastShare Phone" }
    val clean = name.trim().take(28).ifEmpty { fallback }
    context.settingsDataStore.edit { it[PrefKeys.DEVICE_NAME] = clean }
  }

  suspend fun setTurboMode(enabled: Boolean) {
    context.settingsDataStore.edit { it[PrefKeys.TURBO_MODE] = enabled }
  }

  fun publicAmmarshareDir(): File = File(Environment.getExternalStorageDirectory(), "Ammarshare")

  fun hasPublicStorageAccess(): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
      } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
          PackageManager.PERMISSION_GRANTED
      }
    } catch (_: Throwable) {
      false
    }
  }

  fun buildAllFilesAccessIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun canInstallPackages(): Boolean {
    return try {
      Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    } catch (_: Throwable) {
      true
    }
  }

  fun buildInstallPermissionIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  suspend fun loadInstalledApps(): List<InstalledAppInfo> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val launchIntent =
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
      val resolveInfos =
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
              launchIntent,
              PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
          } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launchIntent, 0)
          }
        } catch (_: Throwable) {
          emptyList()
        }

      val seenPackages = HashSet<String>()
      val apps = ArrayList<InstalledAppInfo>()
      val extRoot =
        try {
          Environment.getExternalStorageDirectory()
        } catch (_: Throwable) {
          null
        }

      for (ri in resolveInfos) {
        try {
          val pkg = ri.activityInfo?.packageName ?: continue
          if (!seenPackages.add(pkg)) continue

          val appInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
              @Suppress("DEPRECATION")
              pm.getApplicationInfo(pkg, 0)
            }

          val apkPath = appInfo.sourceDir ?: continue
          val apkFile = File(apkPath)
          if (!apkFile.exists() || !apkFile.canRead()) continue

          val splits =
            appInfo.splitSourceDirs
              ?.mapNotNull { path ->
                val f = File(path)
                if (f.exists() && f.canRead() && f.length() > 0L) f else null
              }
              .orEmpty()

          // Collect accessible external OBB / expansion files for this app
          val extraData = ArrayList<File>()
          if (extRoot != null) {
            val obbDir = File(extRoot, "Android/obb/$pkg")
            if (obbDir.exists() && obbDir.isDirectory && obbDir.canRead()) {
              obbDir.listFiles()?.forEach { child ->
                if (child.isFile && child.canRead() && child.length() > 0L) {
                  extraData.add(child)
                }
              }
            }
          }

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
            } catch (_: Throwable) {
              "1.0"
            }

          val baseSize = apkFile.length()
          val bundleTotal =
            baseSize + splits.sumOf { it.length() } + extraData.sumOf { it.length() }

          apps.add(
            InstalledAppInfo(
              packageName = pkg,
              appName = label,
              versionName = versionName,
              apkPath = apkPath,
              sizeBytes = baseSize,
              splitPaths = splits.map { it.absolutePath },
              extraDataPaths = extraData.map { it.absolutePath },
              totalBundleBytes = bundleTotal,
            )
          )
        } catch (_: Throwable) {
          continue
        }
      }

      apps.sortedBy { it.appName.lowercase() }
    }

  suspend fun scanMovieBoxAndPublicMedia(): List<LocalMediaEntry> =
    withContext(Dispatchers.IO) {
      val results = ArrayList<LocalMediaEntry>()
      val seenPaths = HashSet<String>()

      val extRoot =
        try {
          Environment.getExternalStorageDirectory()
        } catch (_: Throwable) {
          null
        }

      if (extRoot != null && extRoot.exists()) {
        val scanTargets =
          listOf(
            "MovieBox" to File(extRoot, "MovieBox"),
            "MovieBox" to File(extRoot, "Download/MovieBox"),
            "MovieBox" to File(extRoot, "Movies/MovieBox"),
            "MovieBox" to File(extRoot, "Android/data/com.community.mbox.in/files"),
            "MovieBox" to File(extRoot, "Android/data/com.transsion.moviebox/files"),
            "MovieBox" to File(extRoot, "Android/data/com.moviebox.pro/files"),
            "Ammarshare" to File(extRoot, "Ammarshare"),
            "Downloads" to File(extRoot, "Download"),
            "Movies" to File(extRoot, "Movies"),
          )

        for ((tag, dir) in scanTargets) {
          if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
          walkLocalDirectory(
            dir = dir,
            sourceTag = tag,
            depth = 0,
            maxDepth = 4,
            seenPaths = seenPaths,
            out = results,
          )
        }
      }

      // Also include MediaStore recent videos/downloads when public files aren't directly listable
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
          val projection =
            arrayOf(
              MediaStore.Video.Media._ID,
              MediaStore.Video.Media.DISPLAY_NAME,
              MediaStore.Video.Media.SIZE,
              MediaStore.Video.Media.MIME_TYPE,
              MediaStore.Video.Media.DATA,
              MediaStore.Video.Media.DATE_MODIFIED,
            )
          context.contentResolver
            .query(
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
              projection,
              null,
              null,
              "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )
            ?.use { cursor ->
              val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
              val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
              val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
              val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
              val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
              val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

              var count = 0
              while (cursor.moveToNext() && count < 120) {
                val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (dataPath.isNullOrBlank()) continue
                val file = File(dataPath)
                if (!file.exists() || !file.canRead() || !seenPaths.add(file.absolutePath)) continue

                val lowerPath = dataPath.lowercase()
                val sourceTag =
                  when {
                    lowerPath.contains("moviebox") -> "MovieBox"
                    lowerPath.contains("ammarshare") -> "Ammarshare"
                    lowerPath.contains("download") -> "Downloads"
                    else -> "Videos"
                  }
                val name =
                  if (nameCol >= 0) cursor.getString(nameCol) ?: file.name else file.name
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else file.length()
                val mime =
                  if (mimeCol >= 0) cursor.getString(mimeCol) ?: guessMimeType(name)
                  else guessMimeType(name)
                val modMs =
                  if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else file.lastModified()
                val idNum = if (idCol >= 0) cursor.getLong(idCol) else count.toLong()
                if (size < 4096L) continue

                results.add(
                  LocalMediaEntry(
                    id = "ms_${idNum}_${file.absolutePath.hashCode()}",
                    filePath = file.absolutePath,
                    displayName = name,
                    sizeBytes = size,
                    mimeType = mime,
                    category = ShareCategory.VIDEOS,
                    sourceTag = sourceTag,
                    modifiedMs = modMs,
                  )
                )
                count++
              }
            }
        } catch (_: Throwable) {}
      }

      results.sortedWith(
        compareByDescending<LocalMediaEntry> { it.sourceTag == "MovieBox" }
          .thenByDescending { it.sourceTag == "Ammarshare" }
          .thenByDescending { it.modifiedMs }
      )
    }

  private fun walkLocalDirectory(
    dir: File,
    sourceTag: String,
    depth: Int,
    maxDepth: Int,
    seenPaths: HashSet<String>,
    out: MutableList<LocalMediaEntry>,
  ) {
    if (depth > maxDepth || out.size >= 400) return
    val children =
      try {
        dir.listFiles()
      } catch (_: Throwable) {
        null
      } ?: return

    for (child in children) {
      if (out.size >= 400) break
      val name = child.name
      if (name.startsWith(".")) continue
      if (child.isDirectory) {
        if (name.equals("thumbnails", ignoreCase = true) || name.equals("cache", ignoreCase = true)) {
          continue
        }
        walkLocalDirectory(child, sourceTag, depth + 1, maxDepth, seenPaths, out)
      } else if (child.isFile && child.canRead()) {
        val len = child.length()
        if (len < 1024L) continue
        if (!seenPaths.add(child.absolutePath)) continue
        val mime = guessMimeType(name)
        val cat = inferCategory(name, mime, "")
        val resolvedTag =
          if (child.absolutePath.lowercase().contains("moviebox")) "MovieBox" else sourceTag
        out.add(
          LocalMediaEntry(
            id = "local_${out.size}_${child.absolutePath.hashCode()}",
            filePath = child.absolutePath,
            displayName = name,
            sizeBytes = len,
            mimeType = mime,
            category = cat,
            sourceTag = resolvedTag,
            modifiedMs = child.lastModified(),
          )
        )
      }
    }
  }

  suspend fun resolvePickedUris(uris: List<Uri>, category: ShareCategory): List<ShareableItem> =
    withContext(Dispatchers.IO) {
      uris.mapIndexedNotNull { index, uri ->
        try {
          val cr = context.contentResolver
          var displayName = "file_${System.currentTimeMillis()}_$index"
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
            id = "uri_${index}_${uri.toString().hashCode()}_${displayName}",
            displayName = displayName,
            relativePath = displayName,
            sizeBytes = sizeBytes,
            mimeType = mime,
            category = category,
            uri = uri,
          )
        } catch (_: Throwable) {
          null
        }
      }
    }

  suspend fun resolvePickedFolder(treeUri: Uri): List<ShareableItem> =
    withContext(Dispatchers.IO) {
      val root =
        try {
          DocumentFile.fromTreeUri(context, treeUri)
        } catch (_: Throwable) {
          null
        } ?: return@withContext emptyList()
      val rootName = (root.name ?: "SharedFolder").replace(Regex("[/\\\\]"), "_")
      val items = ArrayList<ShareableItem>()

      fun walk(node: DocumentFile, currentRelPath: String) {
        val children =
          try {
            node.listFiles()
          } catch (_: Throwable) {
            emptyArray()
          }
        for (child in children) {
          val childName = child.name ?: continue
          if (child.isDirectory) {
            walk(child, "$currentRelPath/$childName")
          } else if (child.isFile) {
            val relPath = "$currentRelPath/$childName"
            val mime = child.type ?: guessMimeType(childName)
            val idx = items.size
            items.add(
              ShareableItem(
                id = "folder_${idx}_${child.uri.toString().hashCode()}",
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

  /**
   * Saves incoming received files into the public top-level folder
   * `/storage/emulated/0/Ammarshare/<relativePath>` (publicly visible in File Managers & Gallery).
   * Falls back cleanly to MediaStore Downloads/Ammarshare only if Android 11+ All Files Access
   * hasn't been granted yet, preventing any write crash.
   */
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

    // Primary public destination: /storage/emulated/0/Ammarshare/<cleanRel>
    try {
      val publicRoot = publicAmmarshareDir()
      if (!publicRoot.exists()) {
        publicRoot.mkdirs()
      }
      val targetFile = deduplicateFile(File(publicRoot, cleanRel))
      targetFile.parentFile?.mkdirs()
      val stream = FileOutputStream(targetFile)
      return stream to
        SavedLocation(
          uri = null,
          filePath = targetFile.absolutePath,
          mediaStorePending = false,
          mimeType = mimeType,
        )
    } catch (_: Throwable) {
      // Fallback when scoped storage blocks direct root /Ammarshare write prior to All Files grant
    }

    val lastSlash = cleanRel.lastIndexOf('/')
    val subFolder = if (lastSlash > 0) cleanRel.substring(0, lastSlash) else ""
    val fileName = if (lastSlash >= 0) cleanRel.substring(lastSlash + 1) else cleanRel

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val mediaRelPath =
          if (subFolder.isNotEmpty()) "${Environment.DIRECTORY_DOWNLOADS}/Ammarshare/$subFolder"
          else "${Environment.DIRECTORY_DOWNLOADS}/Ammarshare"

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
            return out to
              SavedLocation(
                uri = uri,
                filePath = null,
                mediaStorePending = true,
                mimeType = mimeType,
              )
          }
        }
      } catch (_: Throwable) {}
    }

    val fallbackDir = File(context.getExternalFilesDir(null), "Ammarshare")
    val targetFile = deduplicateFile(File(fallbackDir, cleanRel))
    targetFile.parentFile?.mkdirs()
    return FileOutputStream(targetFile) to
      SavedLocation(
        uri = null,
        filePath = targetFile.absolutePath,
        mediaStorePending = false,
        mimeType = mimeType,
      )
  }

  /**
   * Opens a random-access FileChannel target pre-sized to [totalSize] so 8 parallel TCP workers
   * can write non-overlapping 4 MB stripes lock-free via pwrite (`FileChannel.write(buf, pos)`).
   */
  fun createIncomingRandomAccessTarget(
    relativePath: String,
    mimeType: String,
    totalSize: Long,
  ): IncomingFileTarget {
    val cleanRel =
      relativePath
        .replace("\\", "/")
        .split("/")
        .filter { it.isNotBlank() && it != "." && it != ".." }
        .joinToString("/")
        .ifEmpty { "received_${System.currentTimeMillis()}.bin" }

    // 1. Primary public destination: /storage/emulated/0/Ammarshare/<cleanRel>
    try {
      val publicRoot = publicAmmarshareDir()
      if (!publicRoot.exists()) {
        publicRoot.mkdirs()
      }
      val targetFile = deduplicateFile(File(publicRoot, cleanRel))
      targetFile.parentFile?.mkdirs()
      val raf = RandomAccessFile(targetFile, "rw")
      if (totalSize >= 0L) {
        raf.setLength(totalSize)
      }
      val channel = raf.channel
      return IncomingFileTarget(
        savedLocation =
          SavedLocation(
            uri = null,
            filePath = targetFile.absolutePath,
            mediaStorePending = false,
            mimeType = mimeType,
          ),
        channel = channel,
        closeables = listOf(channel, raf),
      )
    } catch (_: Throwable) {
      // Scoped storage fallback below
    }

    val lastSlash = cleanRel.lastIndexOf('/')
    val subFolder = if (lastSlash > 0) cleanRel.substring(0, lastSlash) else ""
    val fileName = if (lastSlash >= 0) cleanRel.substring(lastSlash + 1) else cleanRel

    // 2. Android 10+ MediaStore Downloads/Ammarshare via ParcelFileDescriptor("rw") FileChannel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val mediaRelPath =
          if (subFolder.isNotEmpty()) "${Environment.DIRECTORY_DOWNLOADS}/Ammarshare/$subFolder"
          else "${Environment.DIRECTORY_DOWNLOADS}/Ammarshare"

        val values =
          ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { guessMimeType(fileName) })
            put(MediaStore.Downloads.RELATIVE_PATH, mediaRelPath)
            put(MediaStore.Downloads.IS_PENDING, 1)
          }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
          val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
          if (pfd != null) {
            val fos = FileOutputStream(pfd.fileDescriptor)
            val channel = fos.channel
            return IncomingFileTarget(
              savedLocation =
                SavedLocation(
                  uri = uri,
                  filePath = null,
                  mediaStorePending = true,
                  mimeType = mimeType,
                ),
              channel = channel,
              closeables = listOf(channel, fos, pfd),
            )
          }
        }
      } catch (_: Throwable) {}
    }

    // 3. App-scoped external fallback
    val fallbackDir = File(context.getExternalFilesDir(null), "Ammarshare")
    val targetFile = deduplicateFile(File(fallbackDir, cleanRel))
    targetFile.parentFile?.mkdirs()
    val raf = RandomAccessFile(targetFile, "rw")
    if (totalSize >= 0L) {
      raf.setLength(totalSize)
    }
    val channel = raf.channel
    return IncomingFileTarget(
      savedLocation =
        SavedLocation(
          uri = null,
          filePath = targetFile.absolutePath,
          mediaStorePending = false,
          mimeType = mimeType,
        ),
      channel = channel,
      closeables = listOf(channel, raf),
    )
  }

  private fun deduplicateFile(candidate: File): File {
    if (!candidate.exists()) return candidate
    // Inside Apps/<AppSlug>/ allow overwriting clean APK/split names or keep numbered file
    val name = candidate.name
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var counter = 1
    while (counter < 999) {
      val alt = File(candidate.parentFile, "${stem}_($counter)$ext")
      if (!alt.exists()) return alt
      counter++
    }
    return candidate
  }

  fun finalizeSavedLocation(location: SavedLocation) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && location.mediaStorePending && location.uri != null) {
      try {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(location.uri, values, null, null)
      } catch (_: Throwable) {}
    }
    if (!location.filePath.isNullOrBlank()) {
      try {
        MediaScannerConnection.scanFile(
          context,
          arrayOf(location.filePath),
          arrayOf(location.mimeType.ifBlank { guessMimeType(location.filePath) }),
          null,
        )
      } catch (_: Throwable) {}
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
    } catch (_: Throwable) {
      emptyList()
    }
  }

  @Synchronized
  fun appendHistoryRecords(newRecords: List<TransferRecord>) {
    if (newRecords.isEmpty()) return
    try {
      val existing = loadTransferHistory().toMutableList()
      existing.addAll(0, newRecords)
      val capped = existing.take(250)
      saveHistoryList(capped)
    } catch (_: Throwable) {}
  }

  @Synchronized
  fun clearHistory() {
    try {
      saveHistoryList(emptyList())
    } catch (_: Throwable) {}
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

  fun installApkFromRecord(record: TransferRecord): InstallResult {
    if (!canInstallPackages()) return InstallResult.NeedsPermission

    // Check if there are sibling split_*.apk files in the same saved directory
    if (!record.savedFilePath.isNullOrBlank()) {
      val file = File(record.savedFilePath)
      if (!file.exists()) return InstallResult.Failed("APK file no longer exists on storage")
      val parentDir = file.parentFile
      val siblingSplits =
        parentDir
          ?.listFiles { f ->
            f.isFile &&
              f.name.endsWith(".apk", ignoreCase = true) &&
              f.absolutePath != file.absolutePath &&
              f.name.startsWith("split_", ignoreCase = true)
          }
          ?.toList()
          .orEmpty()

      if (siblingSplits.isNotEmpty()) {
        return installSplitApks(listOf(file) + siblingSplits)
      }
      return installSingleApkUri(
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      )
    }

    if (!record.savedUriString.isNullOrBlank()) {
      return installSingleApkUri(Uri.parse(record.savedUriString))
    }

    return InstallResult.Failed("APK path unavailable — check /storage/emulated/0/Ammarshare")
  }

  fun installApkFile(file: File): InstallResult {
    if (!canInstallPackages()) return InstallResult.NeedsPermission
    if (!file.exists() || !file.canRead()) {
      return InstallResult.Failed("Cannot read ${file.name}")
    }
    val parentDir = file.parentFile
    val siblingSplits =
      parentDir
        ?.listFiles { f ->
          f.isFile &&
            f.name.endsWith(".apk", ignoreCase = true) &&
            f.absolutePath != file.absolutePath &&
            f.name.startsWith("split_", ignoreCase = true)
        }
        ?.toList()
        .orEmpty()
    if (siblingSplits.isNotEmpty()) {
      return installSplitApks(listOf(file) + siblingSplits)
    }
    return try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      installSingleApkUri(uri)
    } catch (t: Throwable) {
      InstallResult.Failed(t.localizedMessage ?: "Failed to open APK installer")
    }
  }

  private fun installSingleApkUri(uri: Uri): InstallResult {
    return try {
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      InstallResult.Started
    } catch (t: Throwable) {
      InstallResult.Failed(t.localizedMessage ?: "Unable to launch Android package installer")
    }
  }

  private fun installSplitApks(apkFiles: List<File>): InstallResult {
    var session: PackageInstaller.Session? = null
    return try {
      val installer = context.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
      val sessionId = installer.createSession(params)
      session = installer.openSession(sessionId)

      apkFiles.forEachIndexed { idx, file ->
        val splitName = "apk_${idx}_${file.name}"
        session.openWrite(splitName, 0, file.length()).use { out ->
          FileInputStream(file).use { inp -> inp.copyTo(out, 64 * 1024) }
          session.fsync(out)
        }
      }

      val action = "${context.packageName}.INSTALL_COMMIT_$sessionId"
      val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
              @Suppress("DEPRECATION")
              val confirmIntent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                  intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
              confirmIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                  ctx.startActivity(it)
                } catch (_: Throwable) {}
              }
            }
            try {
              context.applicationContext.unregisterReceiver(this)
            } catch (_: Throwable) {}
          }
        }
      val filter = IntentFilter(action)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.applicationContext.registerReceiver(receiver, filter)
      }

      val flags =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
          PendingIntent.FLAG_UPDATE_CURRENT
        }
      val callbackIntent =
        PendingIntent.getBroadcast(context, sessionId, Intent(action), flags)
      session.commit(callbackIntent.intentSender)
      InstallResult.Started
    } catch (t: Throwable) {
      try {
        session?.abandon()
      } catch (_: Throwable) {}
      InstallResult.Failed(t.localizedMessage ?: "Split APK install failed")
    }
  }

  fun openRecord(record: TransferRecord): Boolean {
    return try {
      val targetUri: Uri? = resolveRecordUri(record)
      if (targetUri == null) return false
      val mime = record.mimeType.ifBlank { guessMimeType(record.fileName) }
      if (mime == "application/vnd.android.package-archive" || record.fileName.endsWith(".apk", true)) {
        return installApkFromRecord(record) is InstallResult.Started
      }
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(targetUri, mime)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(
        Intent.createChooser(intent, "Open ${record.fileName}").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      )
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun shareRecord(record: TransferRecord): Boolean {
    return try {
      val targetUri: Uri? = resolveRecordUri(record) ?: return false
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
    } catch (_: Throwable) {
      false
    }
  }

  private fun resolveRecordUri(record: TransferRecord): Uri? {
    return when {
      !record.savedUriString.isNullOrBlank() -> Uri.parse(record.savedUriString)
      !record.savedFilePath.isNullOrBlank() -> {
        val file = File(record.savedFilePath)
        if (file.exists()) {
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else null
      }
      else -> null
    }
  }

  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    return try {
      val hints = mapOf(EncodeHintType.MARGIN to 1)
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
      for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
          bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.parseColor("#0B1228") else AndroidColor.WHITE)
        }
      }
      bmp
    } catch (_: Throwable) {
      null
    }
  }

  companion object {
    fun guessMimeType(fileName: String): String {
      val ext = fileName.substringAfterLast('.', "").lowercase()
      return when (ext) {
        "apk" -> "application/vnd.android.package-archive"
        "mkv" -> "video/x-matroska"
        "mp4" -> "video/mp4"
        "ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
      }
    }

    fun inferCategory(fileName: String, mimeType: String, relativePath: String): ShareCategory {
      val lower = fileName.lowercase()
      if (lower.endsWith(".apk")) return ShareCategory.APPS
      if (relativePath.contains('/')) return ShareCategory.FOLDERS
      return when {
        mimeType.startsWith("video/") ||
          lower.endsWith(".mp4") ||
          lower.endsWith(".mkv") ||
          lower.endsWith(".mov") ||
          lower.endsWith(".ts") ||
          lower.endsWith(".avi") ||
          lower.endsWith(".webm") -> ShareCategory.VIDEOS
        mimeType.startsWith("image/") ||
          lower.endsWith(".jpg") ||
          lower.endsWith(".jpeg") ||
          lower.endsWith(".png") ||
          lower.endsWith(".webp") -> ShareCategory.PHOTOS
        else -> ShareCategory.FILES
      }
    }
  }
}

data class SavedLocation(
  val uri: Uri?,
  val filePath: String?,
  val mediaStorePending: Boolean,
  val mimeType: String = "application/octet-stream",
)

class IncomingFileTarget(
  val savedLocation: SavedLocation,
  private val channel: FileChannel,
  private val closeables: List<Closeable>,
) {
  fun writeAt(buffer: ByteBuffer, position: Long) {
    var currentPos = position
    while (buffer.hasRemaining()) {
      val written = channel.write(buffer, currentPos)
      if (written <= 0) throw IOException("Short disk write at offset $currentPos")
      currentPos += written
    }
  }

  fun close() {
    for (c in closeables) {
      try {
        c.close()
      } catch (_: Throwable) {}
    }
  }
}
