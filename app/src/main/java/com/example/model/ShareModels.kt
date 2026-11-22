package com.example.model

import android.net.Uri

enum class ShareCategory(val label: String) {
  APPS("Apps"),
  VIDEOS("Videos"),
  PHOTOS("Photos"),
  FOLDERS("Folders"),
  FILES("Files"),
}

data class InstalledAppInfo(
  val packageName: String,
  val appName: String,
  val versionName: String,
  val apkPath: String,
  val sizeBytes: Long,
) {
  val exportFileName: String
    get() {
      val safeName = appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val safeVer = versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      return "${safeName}_v${safeVer}.apk"
    }
}

data class ShareableItem(
  val id: String,
  val displayName: String,
  val relativePath: String,
  val sizeBytes: Long,
  val mimeType: String,
  val category: ShareCategory,
  val uri: Uri? = null,
  val localFilePath: String? = null,
  val packageName: String? = null,
  val folderGroupName: String? = null,
)

enum class DiscoverySource {
  NSD_WIFI,
  WIFI_DIRECT,
  MANUAL,
}

data class DiscoveredDevice(
  val id: String,
  val name: String,
  val host: String,
  val port: Int,
  val pin: String,
  val source: DiscoverySource,
)

enum class TransferDirection {
  SEND,
  RECEIVE,
}

enum class TransferStatus {
  IDLE,
  LISTENING,
  CONNECTING,
  TRANSFERRING,
  COMPLETED,
  FAILED,
  CANCELLED,
}

data class TransferProgressState(
  val direction: TransferDirection = TransferDirection.SEND,
  val status: TransferStatus = TransferStatus.IDLE,
  val peerName: String = "",
  val currentFileName: String = "",
  val currentFileIndex: Int = 0,
  val totalFiles: Int = 0,
  val bytesTransferred: Long = 0L,
  val totalBytes: Long = 0L,
  val speedMBps: Double = 0.0,
  val peakSpeedMBps: Double = 0.0,
  val elapsedMs: Long = 0L,
  val activeStreams: Int = 1,
  val errorMessage: String? = null,
) {
  val progressFraction: Float
    get() =
      if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
      else 0f

  val etaSeconds: Long
    get() {
      if (speedMBps <= 0.05 || totalBytes <= bytesTransferred) return 0L
      val remainingBytes = (totalBytes - bytesTransferred).coerceAtLeast(0L)
      val bytesPerSec = speedMBps * 1024.0 * 1024.0
      return (remainingBytes / bytesPerSec).toLong().coerceAtLeast(1L)
    }
}

data class TransferRecord(
  val id: String,
  val fileName: String,
  val relativePath: String,
  val sizeBytes: Long,
  val direction: TransferDirection,
  val category: ShareCategory,
  val timestampMs: Long,
  val speedMBps: Double,
  val peerName: String,
  val savedUriString: String? = null,
  val savedFilePath: String? = null,
  val mimeType: String = "application/octet-stream",
)

data class BenchmarkResult(
  val throughputMBps: Double,
  val peakMBps: Double,
  val durationMs: Long,
  val payloadMegabytes: Int,
  val streamsUsed: Int,
)

object Formatters {
  fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
  }

  fun formatSpeed(speedMBps: Double): String {
    return if (speedMBps >= 100.0) {
      String.format(java.util.Locale.US, "%.0f MB/s", speedMBps)
    } else if (speedMBps >= 1.0) {
      String.format(java.util.Locale.US, "%.1f MB/s", speedMBps)
    } else {
      val kbps = speedMBps * 1024.0
      String.format(java.util.Locale.US, "%.0f KB/s", kbps)
    }
  }
}
