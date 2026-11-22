package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ShareRepository
import com.example.model.BenchmarkResult
import com.example.model.DiscoveredDevice
import com.example.model.DiscoverySource
import com.example.model.InstalledAppInfo
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.model.TransferProgressState
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.transfer.NearbyDiscoveryManager
import com.example.transfer.TransferEngine
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReceiverSessionInfo(
  val isListening: Boolean = false,
  val localIp: String = "0.0.0.0",
  val port: Int = TransferEngine.DEFAULT_PORT,
  val pin: String = "4825",
  val qrBitmap: Bitmap? = null,
)

class AmmarShareViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = ShareRepository(application)
  private val discoveryManager = NearbyDiscoveryManager(application)
  private val transferEngine = TransferEngine(application, repository)

  private val _showSplash = MutableStateFlow(true)
  val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

  val deviceName: StateFlow<String> =
    repository.deviceNameFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      android.os.Build.MODEL.ifBlank { "Ammar Phone" },
    )

  val turboMode: StateFlow<Boolean> =
    repository.turboModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

  private val _appsLoading = MutableStateFlow(false)
  val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

  private val _appSearchQuery = MutableStateFlow("")
  val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

  private val _selectedQueue = MutableStateFlow<List<ShareableItem>>(emptyList())
  val selectedQueue: StateFlow<List<ShareableItem>> = _selectedQueue.asStateFlow()

  private val _receiverInfo = MutableStateFlow(ReceiverSessionInfo())
  val receiverInfo: StateFlow<ReceiverSessionInfo> = _receiverInfo.asStateFlow()

  private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
  val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()

  private val _benchmarkResult = MutableStateFlow<BenchmarkResult?>(null)
  val benchmarkResult: StateFlow<BenchmarkResult?> = _benchmarkResult.asStateFlow()

  private val _isBenchmarkRunning = MutableStateFlow(false)
  val isBenchmarkRunning: StateFlow<Boolean> = _isBenchmarkRunning.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
  val isScanning: StateFlow<Boolean> = discoveryManager.isScanning
  val wifiDirectStatus: StateFlow<String> = discoveryManager.wifiDirectStatus
  val transferProgress: StateFlow<TransferProgressState> = transferEngine.progress

  init {
    viewModelScope.launch {
      delay(2100L)
      _showSplash.value = false
    }
    refreshHistory()
    loadApps()
  }

  fun dismissSplash() {
    _showSplash.value = false
  }

  fun clearBanner() {
    _statusBanner.value = null
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

  fun toggleAppSelection(app: InstalledAppInfo) {
    val id = "app_${app.packageName}"
    val current = _selectedQueue.value.toMutableList()
    val existingIdx = current.indexOfFirst { it.id == id }
    if (existingIdx >= 0) {
      current.removeAt(existingIdx)
    } else {
      current.add(
        ShareableItem(
          id = id,
          displayName = app.exportFileName,
          relativePath = app.exportFileName,
          sizeBytes = app.sizeBytes,
          mimeType = "application/vnd.android.package-archive",
          category = ShareCategory.APPS,
          localFilePath = app.apkPath,
          packageName = app.packageName,
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
    discoveryManager.startNsdDiscovery()
    if (withWifiDirect) {
      discoveryManager.startWifiDirectDiscovery()
    }
  }

  fun stopSenderRadar() {
    discoveryManager.stopNsdDiscovery()
    discoveryManager.stopWifiDirect()
  }

  fun startReceiveMode() {
    val pin = Random.nextInt(1000, 9999).toString()
    val localIp = discoveryManager.getLocalWifiIpAddress()
    val name = deviceName.value

    transferEngine.startReceiver(
      expectedPin = pin,
      onBound = { actualPort ->
        discoveryManager.registerReceiverService(name, actualPort, pin)
        val payload = "ammarshare://$localIp:$actualPort?pin=$pin&name=${Uri.encode(name)}"
        val qr = repository.generateQrBitmap(payload, 520)
        _receiverInfo.value =
          ReceiverSessionInfo(
            isListening = true,
            localIp = localIp,
            port = actualPort,
            pin = pin,
            qrBitmap = qr,
          )
      },
      onHistoryUpdated = { refreshHistory() },
    )
  }

  fun stopReceiveMode() {
    discoveryManager.unregisterReceiverService()
    transferEngine.stopReceiver()
    _receiverInfo.value = ReceiverSessionInfo(isListening = false)
  }

  fun connectAndSendToDevice(device: DiscoveredDevice) {
    val items = _selectedQueue.value
    if (items.isEmpty()) {
      _statusBanner.value = "Select at least 1 app, video, folder, or file first"
      return
    }
    if (device.source == DiscoverySource.WIFI_DIRECT && device.host.contains(":")) {
      // MAC address from Wi-Fi Direct peer list — initiate Wi-Fi Direct pairing first
      discoveryManager.connectWifiDirectPeer(device.host) { _, msg ->
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
    val trimmed = hostOrUri.trim()
    if (trimmed.isEmpty()) {
      _statusBanner.value = "Enter receiver IP address or pairing link"
      return
    }

    var host = trimmed
    var port = portText.toIntOrNull() ?: TransferEngine.DEFAULT_PORT
    var pin = pinText.trim()

    if (trimmed.startsWith("ammarshare://")) {
      try {
        val parsed = Uri.parse(trimmed)
        host = parsed.host ?: host
        if (parsed.port > 0) port = parsed.port
        pin = parsed.getQueryParameter("pin") ?: pin
      } catch (_: Exception) {}
    } else if (trimmed.contains(":") && !trimmed.contains("::")) {
      val parts = trimmed.split(":")
      host = parts[0]
      parts.getOrNull(1)?.toIntOrNull()?.let { port = it }
    }

    connectAndSendToDevice(
      DiscoveredDevice(
        id = "manual_$host:$port",
        name = "Receiver ($host)",
        host = host,
        port = port,
        pin = pin,
        source = DiscoverySource.MANUAL,
      )
    )
  }

  fun cancelTransfer() {
    transferEngine.cancelActiveTransfer()
  }

  fun dismissTransferSummary() {
    if (transferProgress.value.status == TransferStatus.COMPLETED) {
      // Keep queue or clear? Leaving queue or clearing is convenient; let's keep queue intact or let user clear.
    }
    transferEngine.resetProgress()
  }

  fun runTurboBenchmark() {
    if (_isBenchmarkRunning.value) return
    viewModelScope.launch {
      _isBenchmarkRunning.value = true
      _benchmarkResult.value = transferEngine.runTurboLoopbackBenchmark(payloadMb = 96, streams = 6)
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
      _statusBanner.value = "File is located in Downloads/AmmarShare/${record.relativePath}"
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
  }
}
