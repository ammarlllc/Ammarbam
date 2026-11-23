package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ShareRepository
import com.example.model.BenchmarkResult
import com.example.model.DiscoveredDevice
import com.example.model.DiscoverySource
import com.example.model.InstallResult
import com.example.model.InstalledAppInfo
import com.example.model.LocalMediaEntry
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferProgressState
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.transfer.DeviceReadiness
import com.example.transfer.LocalHotspotInfo
import com.example.transfer.NearbyDiscoveryManager
import com.example.transfer.TransferEngine
import java.io.File
import java.net.URI
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReceiverSessionInfo(
  val isListening: Boolean = false,
  val localIp: String = "0.0.0.0",
  val port: Int = TransferEngine.DEFAULT_PORT,
  val pin: String = "4825",
  val qrBitmap: Bitmap? = null,
  val hotspotSsid: String = "",
  val hotspotPassphrase: String = "",
)

data class ParsedPairingTarget(
  val host: String,
  val port: Int,
  val pin: String,
  val peerName: String,
  val hotspotSsid: String? = null,
  val hotspotPassword: String? = null,
)

object PairingUriParser {
  private val HOST_REGEX = Regex("^[a-zA-Z0-9._-]+$")

  fun parse(rawInput: String, fallbackPort: Int, fallbackPin: String): ParsedPairingTarget? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null

    if (
      trimmed.startsWith("ammarshare://", ignoreCase = true) ||
        trimmed.startsWith("fastshare://", ignoreCase = true)
    ) {
      return try {
        val uri = URI(trimmed)
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = if (uri.port in 1..65535) uri.port else fallbackPort
        val params = parseQueryParams(uri.rawQuery)
        val pin = params["pin"].orEmpty().ifBlank { fallbackPin.trim() }
        val peerName = params["name"].orEmpty().ifBlank { "Receiver ($host)" }
        val ssid = params["ssid"]?.takeIf { it.isNotBlank() }
        val pwd = params["pwd"]
        ParsedPairingTarget(
          host = host,
          port = port,
          pin = pin,
          peerName = peerName,
          hotspotSsid = ssid,
          hotspotPassword = pwd,
        )
      } catch (_: Exception) {
        null
      }
    }

    if (trimmed.contains("://")) return null

    var host = trimmed
    var port = fallbackPort
    if (trimmed.contains(":") && !trimmed.contains("::")) {
      val parts = trimmed.split(":")
      if (parts.size != 2) return null
      host = parts[0].trim()
      val parsedPort = parts[1].trim().toIntOrNull() ?: return null
      if (parsedPort !in 1..65535) return null
      port = parsedPort
    }

    if (!HOST_REGEX.matches(host)) return null
    return ParsedPairingTarget(
      host = host,
      port = port,
      pin = fallbackPin.trim(),
      peerName = "Receiver ($host)",
    )
  }

  private fun parseQueryParams(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<String, String>()
    for (pair in rawQuery.split("&")) {
      val idx = pair.indexOf('=')
      if (idx <= 0) continue
      val key = Uri.decode(pair.substring(0, idx))
      val value = Uri.decode(pair.substring(idx + 1))
      result[key] = value
    }
    return result
  }
}

class AmmarShareViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = ShareRepository(application)
  private val discoveryManager = NearbyDiscoveryManager(application)
  private val transferEngine = TransferEngine(application, repository)

  private val _showSplash = MutableStateFlow(true)
  val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

  private val _showCredits = MutableStateFlow(false)
  val showCredits: StateFlow<Boolean> = _showCredits.asStateFlow()

  val deviceName: StateFlow<String> =
    repository.deviceNameFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      (Build.MODEL ?: "FastShare Phone").ifBlank { "FastShare Phone" },
    )

  val turboMode: StateFlow<Boolean> =
    repository.turboModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

  private val _appsLoading = MutableStateFlow(false)
  val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

  private val _appSearchQuery = MutableStateFlow("")
  val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

  private val _localMedia = MutableStateFlow<List<LocalMediaEntry>>(emptyList())
  val localMedia: StateFlow<List<LocalMediaEntry>> = _localMedia.asStateFlow()

  private val _localMediaLoading = MutableStateFlow(false)
  val localMediaLoading: StateFlow<Boolean> = _localMediaLoading.asStateFlow()

  private val _selectedQueue = MutableStateFlow<List<ShareableItem>>(emptyList())
  val selectedQueue: StateFlow<List<ShareableItem>> = _selectedQueue.asStateFlow()

  private val _receiverInfo = MutableStateFlow(ReceiverSessionInfo())
  val receiverInfo: StateFlow<ReceiverSessionInfo> = _receiverInfo.asStateFlow()

  private val _isStartingHotspot = MutableStateFlow(false)
  val isStartingHotspot: StateFlow<Boolean> = _isStartingHotspot.asStateFlow()

  private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
  val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()

  private val _hasPublicStorageAccess = MutableStateFlow(repository.hasPublicStorageAccess())
  val hasPublicStorageAccess: StateFlow<Boolean> = _hasPublicStorageAccess.asStateFlow()

  private val _canInstallApks = MutableStateFlow(repository.canInstallPackages())
  val canInstallApks: StateFlow<Boolean> = _canInstallApks.asStateFlow()

  private val _benchmarkResult = MutableStateFlow<BenchmarkResult?>(null)
  val benchmarkResult: StateFlow<BenchmarkResult?> = _benchmarkResult.asStateFlow()

  private val _isBenchmarkRunning = MutableStateFlow(false)
  val isBenchmarkRunning: StateFlow<Boolean> = _isBenchmarkRunning.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private var pendingP2pPeerName: String? = null

  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
  val isScanning: StateFlow<Boolean> = discoveryManager.isScanning
  val wifiDirectStatus: StateFlow<String> = discoveryManager.wifiDirectStatus
  val readiness: StateFlow<DeviceReadiness> = discoveryManager.readiness
  val hotspotInfo: StateFlow<LocalHotspotInfo> = discoveryManager.hotspotInfo
  val transferProgress: StateFlow<TransferProgressState> = transferEngine.progress

  init {
    viewModelScope.launch {
      delay(2600L)
      _showSplash.value = false
    }
    viewModelScope.launch {
      discoveryManager.connectedP2pHost.collectLatest { ownerHost ->
        val peerName = pendingP2pPeerName
        val items = _selectedQueue.value
        if (!ownerHost.isNullOrBlank() && peerName != null && items.isNotEmpty()) {
          pendingP2pPeerName = null
          delay(250L)
          stopSenderRadar()
          transferEngine.sendFiles(
            items = items,
            host = ownerHost,
            port = TransferEngine.DEFAULT_PORT,
            pin = "",
            senderDeviceName = deviceName.value,
            peerDeviceName = peerName,
            turboMode = turboMode.value,
            onHistoryUpdated = { refreshHistory() },
          )
        }
      }
    }
    refreshReadiness()
    refreshHistory()
    loadApps()
    refreshLocalMedia()
  }

  fun dismissSplash() {
    _showSplash.value = false
  }

  fun openCredits() {
    _showCredits.value = true
  }

  fun closeCredits() {
    _showCredits.value = false
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun refreshReadiness(): DeviceReadiness {
    _hasPublicStorageAccess.value = repository.hasPublicStorageAccess()
    _canInstallApks.value = repository.canInstallPackages()
    return discoveryManager.checkReadiness()
  }

  fun setDeviceName(name: String) {
    viewModelScope.launch { repository.setDeviceName(name) }
  }

  fun setTurboMode(enabled: Boolean) {
    viewModelScope.launch { repository.setTurboMode(enabled) }
  }

  fun updateAppSearch(query: String) {
    _appSearchQuery.value = query
  }

  fun loadApps() {
    if (_appsLoading.value) return
    viewModelScope.launch {
      _appsLoading.value = true
      _installedApps.value = repository.loadInstalledApps()
      _appsLoading.value = false
    }
  }

  fun refreshLocalMedia() {
    if (_localMediaLoading.value) return
    viewModelScope.launch {
      _localMediaLoading.value = true
      _hasPublicStorageAccess.value = repository.hasPublicStorageAccess()
      _localMedia.value = repository.scanMovieBoxAndPublicMedia()
      _localMediaLoading.value = false
    }
  }

  fun toggleAppSelection(app: InstalledAppInfo) {
    val current = _selectedQueue.value.toMutableList()
    val alreadyQueued = current.any { it.packageName == app.packageName }
    if (alreadyQueued) {
      current.removeAll { it.packageName == app.packageName }
    } else if (!app.hasSplitsOrData) {
      current.add(
        ShareableItem(
          id = "app_${app.packageName}",
          displayName = app.exportFileName,
          relativePath = "Apps/${app.exportFileName}",
          sizeBytes = app.sizeBytes,
          mimeType = "application/vnd.android.package-archive",
          category = ShareCategory.APPS,
          localFilePath = app.apkPath,
          packageName = app.packageName,
        )
      )
    } else {
      val bundleFolder = "Apps/${app.safeAppSlug}"
      // Base APK
      current.add(
        ShareableItem(
          id = "app_${app.packageName}_base",
          displayName = "${app.appName} (base.apk)",
          relativePath = "$bundleFolder/base.apk",
          sizeBytes = app.sizeBytes,
          mimeType = "application/vnd.android.package-archive",
          category = ShareCategory.APPS,
          localFilePath = app.apkPath,
          packageName = app.packageName,
          folderGroupName = app.appName,
        )
      )
      // Split APKs (architecture/resources splits)
      app.splitPaths.forEachIndexed { idx, splitPath ->
        val splitFile = File(splitPath)
        if (splitFile.exists() && splitFile.canRead()) {
          val splitName =
            if (splitFile.name.startsWith("split_", ignoreCase = true)) splitFile.name
            else "split_${idx}_${splitFile.name}"
          current.add(
            ShareableItem(
              id = "app_${app.packageName}_split_$idx",
              displayName = "${app.appName} ($splitName)",
              relativePath = "$bundleFolder/$splitName",
              sizeBytes = splitFile.length(),
              mimeType = "application/vnd.android.package-archive",
              category = ShareCategory.APPS,
              localFilePath = splitFile.absolutePath,
              packageName = app.packageName,
              folderGroupName = app.appName,
            )
          )
        }
      }
      // Accessible external OBB / data files
      app.extraDataPaths.forEachIndexed { idx, dataPath ->
        val dataFile = File(dataPath)
        if (dataFile.exists() && dataFile.canRead()) {
          current.add(
            ShareableItem(
              id = "app_${app.packageName}_data_$idx",
              displayName = "${app.appName} (${dataFile.name})",
              relativePath = "$bundleFolder/obb/${dataFile.name}",
              sizeBytes = dataFile.length(),
              mimeType = ShareRepository.guessMimeType(dataFile.name),
              category = ShareCategory.APPS,
              localFilePath = dataFile.absolutePath,
              packageName = app.packageName,
              folderGroupName = app.appName,
            )
          )
        }
      }
    }
    _selectedQueue.value = current
  }

  fun toggleLocalMediaSelection(entry: LocalMediaEntry) {
    val itemId = "local_${entry.filePath.hashCode()}_${entry.displayName}"
    val current = _selectedQueue.value.toMutableList()
    val idx = current.indexOfFirst { it.id == itemId || it.localFilePath == entry.filePath }
    if (idx >= 0) {
      current.removeAt(idx)
    } else {
      val prefixFolder =
        when (entry.sourceTag) {
          "MovieBox" -> "MovieBox/"
          else -> ""
        }
      current.add(
        ShareableItem(
          id = itemId,
          displayName = entry.displayName,
          relativePath = "$prefixFolder${entry.displayName}",
          sizeBytes = entry.sizeBytes,
          mimeType = entry.mimeType,
          category = entry.category,
          localFilePath = entry.filePath,
        )
      )
    }
    _selectedQueue.value = current
  }

  fun addPickedMediaOrFiles(uris: List<Uri>, category: ShareCategory) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val resolved = repository.resolvePickedUris(uris, category)
      mergeIntoQueue(resolved)
      _statusBanner.value = "Added ${resolved.size} ${category.label.lowercase()} item(s) to send queue"
    }
  }

  fun addPickedFolder(treeUri: Uri?) {
    if (treeUri == null) return
    viewModelScope.launch {
      _statusBanner.value = "Scanning folder tree..."
      val resolved = repository.resolvePickedFolder(treeUri)
      if (resolved.isEmpty()) {
        _statusBanner.value = "Selected folder has no files"
      } else {
        mergeIntoQueue(resolved)
        val rootName = resolved.firstOrNull()?.folderGroupName ?: "Folder"
        _statusBanner.value = "Added folder '$rootName' (${resolved.size} files)"
      }
    }
  }

  private fun mergeIntoQueue(newItems: List<ShareableItem>) {
    val current = _selectedQueue.value.toMutableList()
    val existingIds = current.map { it.id }.toHashSet()
    for (item in newItems) {
      if (existingIds.add(item.id)) {
        current.add(item)
      }
    }
    _selectedQueue.value = current
  }

  fun removeQueueItem(id: String) {
    _selectedQueue.value = _selectedQueue.value.filterNot { it.id == id }
  }

  fun clearQueue() {
    _selectedQueue.value = emptyList()
  }

  fun startSenderRadar(withWifiDirect: Boolean = false) {
    val state = refreshReadiness()
    discoveryManager.startNsdDiscovery()
    if (withWifiDirect || state.isHotspotAndP2pReady) {
      discoveryManager.startWifiDirectDiscovery()
    } else if (!state.locationEnabled) {
      _statusBanner.value = "Turn ON Location Services for Hotspot & Wi-Fi Direct auto-connect"
    }
  }

  fun stopSenderRadar() {
    pendingP2pPeerName = null
    discoveryManager.stopNsdDiscovery()
    discoveryManager.stopWifiDirect()
  }

  fun startReceiveMode() {
    val ready = refreshReadiness()
    val pin = Random.nextInt(1000, 9999).toString()
    val name = deviceName.value

    transferEngine.startReceiver(
      expectedPin = pin,
      onBound = { actualPort ->
        updateReceiverQrAndNsd(name, actualPort, pin)
        if (ready.isHotspotAndP2pReady && !discoveryManager.hotspotInfo.value.active) {
          viewModelScope.launch(Dispatchers.Main) { startAutoHotspotInternal() }
        }
      },
      onHistoryUpdated = {
        refreshHistory()
        refreshLocalMedia()
      },
    )
  }

  fun ensureAutoHotspotOnResume() {
    val ready = refreshReadiness()
    if (
      ready.isHotspotAndP2pReady &&
        _receiverInfo.value.isListening &&
        !discoveryManager.hotspotInfo.value.active &&
        !_isStartingHotspot.value
    ) {
      startAutoHotspotInternal()
    }
  }

  fun toggleReceiveHotspot() {
    if (discoveryManager.hotspotInfo.value.active) {
      stopReceiveHotspot()
      return
    }
    startAutoHotspotInternal()
  }

  private fun startAutoHotspotInternal() {
    if (_isStartingHotspot.value) return
    _isStartingHotspot.value = true

    discoveryManager.startLocalHotspot(deviceName.value) { info ->
      viewModelScope.launch {
        if (info.active) {
          var resolvedIp =
            info.hostIp.ifBlank { discoveryManager.getLocalWifiIpAddress(preferHotspot = true) }
          repeat(6) {
            if (resolvedIp != "127.0.0.1" && !resolvedIp.startsWith("127.")) return@repeat
            delay(250L)
            resolvedIp =
              discoveryManager.hotspotInfo.value.hostIp.ifBlank {
                discoveryManager.getLocalWifiIpAddress(preferHotspot = true)
              }
          }
          val session = _receiverInfo.value
          if (session.isListening) {
            updateReceiverQrAndNsd(deviceName.value, session.port, session.pin, resolvedIp)
          } else {
            startReceiveMode()
          }
          _statusBanner.value = "Fast Share Hotspot '${info.ssid}' active! Ready to auto-connect."
        } else if (!info.error.isNullOrBlank()) {
          _statusBanner.value = info.error
        }
        _isStartingHotspot.value = false
      }
    }
  }

  fun stopReceiveHotspot() {
    discoveryManager.stopLocalHotspot()
    val session = _receiverInfo.value
    if (session.isListening) {
      updateReceiverQrAndNsd(deviceName.value, session.port, session.pin)
    }
  }

  private fun updateReceiverQrAndNsd(
    name: String,
    port: Int,
    pin: String,
    overrideIp: String? = null,
  ) {
    val hotspot = discoveryManager.hotspotInfo.value
    val ip =
      overrideIp
        ?: hotspot.hostIp.takeIf { hotspot.active && it.isNotBlank() }
        ?: discoveryManager.getLocalWifiIpAddress(preferHotspot = hotspot.active)
    discoveryManager.registerReceiverService(name, port, pin)

    val payloadBuilder = StringBuilder("fastshare://$ip:$port?pin=$pin&name=${Uri.encode(name)}")
    if (hotspot.active && hotspot.ssid.isNotBlank()) {
      payloadBuilder.append("&ssid=${Uri.encode(hotspot.ssid)}")
      if (hotspot.passphrase.isNotBlank()) {
        payloadBuilder.append("&pwd=${Uri.encode(hotspot.passphrase)}")
      }
    }
    val qr = repository.generateQrBitmap(payloadBuilder.toString(), 520)
    _receiverInfo.value =
      ReceiverSessionInfo(
        isListening = true,
        localIp = ip,
        port = port,
        pin = pin,
        qrBitmap = qr,
        hotspotSsid = if (hotspot.active) hotspot.ssid else "",
        hotspotPassphrase = if (hotspot.active) hotspot.passphrase else "",
      )
  }

  fun stopReceiveMode() {
    discoveryManager.unregisterReceiverService()
    discoveryManager.stopLocalHotspot()
    transferEngine.stopReceiver()
    _isStartingHotspot.value = false
    _receiverInfo.value = ReceiverSessionInfo(isListening = false)
  }

  fun connectAndSendToDevice(device: DiscoveredDevice) {
    val items = _selectedQueue.value
    if (items.isEmpty()) {
      _statusBanner.value = "Select at least 1 app, video, folder, or file first"
      return
    }
    if (device.source == DiscoverySource.WIFI_DIRECT && device.host.contains(":")) {
      pendingP2pPeerName = device.name
      _statusBanner.value = "Auto-connecting to ${device.name} via Wi-Fi Direct..."
      discoveryManager.connectWifiDirectPeer(device.host) { ok, msg ->
        if (!ok) pendingP2pPeerName = null
        _statusBanner.value = msg
      }
      return
    }
    stopSenderRadar()
    transferEngine.sendFiles(
      items = items,
      host = device.host,
      port = device.port,
      pin = device.pin,
      senderDeviceName = deviceName.value,
      peerDeviceName = device.name,
      turboMode = turboMode.value,
      onHistoryUpdated = { refreshHistory() },
    )
  }

  fun connectAndSendManual(hostOrUri: String, portText: String, pinText: String) {
    if (_selectedQueue.value.isEmpty()) {
      _statusBanner.value = "Select at least 1 app, video, folder, or file first"
      return
    }
    val fallbackPort = portText.toIntOrNull() ?: TransferEngine.DEFAULT_PORT
    val target = PairingUriParser.parse(hostOrUri, fallbackPort, pinText)
    if (target == null) {
      _statusBanner.value = "Invalid Fast Share QR code or IP address"
      return
    }

    val ssid = target.hotspotSsid
    val resolvedHost =
      if (
        (target.host == "127.0.0.1" || target.host == "0.0.0.0") &&
          !ssid.isNullOrBlank() &&
          ssid.startsWith("DIRECT-", ignoreCase = true)
      ) {
        "192.168.49.1"
      } else {
        target.host
      }

    val device =
      DiscoveredDevice(
        id = "manual_${resolvedHost}:${target.port}",
        name = target.peerName,
        host = resolvedHost,
        port = target.port,
        pin = target.pin,
        source = DiscoverySource.MANUAL,
      )

    if (!ssid.isNullOrBlank()) {
      val ready = discoveryManager.checkReadiness()
      if (!ready.wifiEnabled) {
        _statusBanner.value = "Turn ON Wi-Fi first to auto-join Hotspot '$ssid'"
        return
      }
      _statusBanner.value = "Auto-joining Fast Share Hotspot '$ssid'..."
      discoveryManager.connectToHotspot(ssid, target.hotspotPassword.orEmpty()) { connected, msg ->
        viewModelScope.launch(Dispatchers.Main) {
          if (connected) {
            _statusBanner.value = msg
            delay(300L)
            connectAndSendToDevice(device)
          } else {
            _statusBanner.value = msg
          }
        }
      }
    } else {
      connectAndSendToDevice(device)
    }
  }

  fun cancelTransfer() {
    pendingP2pPeerName = null
    transferEngine.cancelActiveTransfer()
    discoveryManager.releaseBoundHotspotNetwork()
  }

  fun dismissTransferSummary() {
    transferEngine.resetProgress()
    discoveryManager.releaseBoundHotspotNetwork()
  }

  fun runTurboBenchmark() {
    if (_isBenchmarkRunning.value) return
    viewModelScope.launch {
      _isBenchmarkRunning.value = true
      _benchmarkResult.value = transferEngine.runTurboLoopbackBenchmark(payloadMb = 128, streams = 8)
      _isBenchmarkRunning.value = false
    }
  }

  fun refreshHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      _history.value = repository.loadTransferHistory()
    }
  }

  fun clearHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      repository.clearHistory()
      _history.value = emptyList()
    }
  }

  fun openHistoryRecord(record: TransferRecord) {
    val ok = repository.openRecord(record)
    if (!ok) {
      _statusBanner.value = "Saved in /storage/emulated/0/Ammarshare/${record.relativePath}"
    }
  }

  fun installHistoryApk(record: TransferRecord) {
    handleInstallResult(repository.installApkFromRecord(record))
  }

  fun installLocalApk(filePath: String) {
    handleInstallResult(repository.installApkFile(File(filePath)))
  }

  private fun handleInstallResult(result: InstallResult) {
    when (result) {
      InstallResult.Started -> {
        _statusBanner.value = "Launching Android APK Installer..."
      }
      InstallResult.NeedsPermission -> {
        _statusBanner.value = "Allow 'Install unknown apps' for Fast Share, then tap Install again."
        try {
          getApplication<Application>().startActivity(repository.buildInstallPermissionIntent())
        } catch (_: Throwable) {}
      }
      is InstallResult.Failed -> {
        _statusBanner.value = result.reason
      }
    }
  }

  fun openPublicStorageSettings() {
    try {
      getApplication<Application>().startActivity(repository.buildAllFilesAccessIntent())
    } catch (_: Throwable) {
      _statusBanner.value = "Open Settings > Apps > Fast Share > Allow All Files Access"
    }
  }

  fun shareHistoryRecord(record: TransferRecord) {
    val ok = repository.shareRecord(record)
    if (!ok) {
      _statusBanner.value = "Unable to open share sheet for ${record.fileName}"
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopSenderRadar()
    stopReceiveMode()
    discoveryManager.releaseBoundHotspotNetwork()
  }
}
