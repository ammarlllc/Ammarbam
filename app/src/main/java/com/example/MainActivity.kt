package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ShareCategory
import com.example.ui.AmmarShareViewModel
import com.example.ui.components.AppIcons
import com.example.ui.screens.CreditsOverlay
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ReceiveRadarScreen
import com.example.ui.screens.SelectContentScreen
import com.example.ui.screens.SendRadarScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransferHubScreen
import com.example.ui.screens.TransferProgressOverlay
import com.example.ui.theme.AmmarShareTheme
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface

enum class MainTab(val routeTag: String, val icon: ImageVector) {
  TRANSFER("tab_transfer", AppIcons.SwapVert),
  SELECT("tab_select", AppIcons.FolderCopy),
  HISTORY("tab_history", AppIcons.History),
}

enum class SubFlow {
  NONE,
  SEND_RADAR,
  RECEIVE_RADAR,
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AmmarShareTheme {
        AmmarShareRoot()
      }
    }
  }
}

@Composable
fun AmmarShareRoot(vm: AmmarShareViewModel = viewModel()) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val showSplash by vm.showSplash.collectAsStateWithLifecycle()
  val showCredits by vm.showCredits.collectAsStateWithLifecycle()
  val deviceName by vm.deviceName.collectAsStateWithLifecycle()
  val turboMode by vm.turboMode.collectAsStateWithLifecycle()
  val installedApps by vm.installedApps.collectAsStateWithLifecycle()
  val appsLoading by vm.appsLoading.collectAsStateWithLifecycle()
  val appSearchQuery by vm.appSearchQuery.collectAsStateWithLifecycle()
  val localMedia by vm.localMedia.collectAsStateWithLifecycle()
  val localMediaLoading by vm.localMediaLoading.collectAsStateWithLifecycle()
  val hasPublicStorageAccess by vm.hasPublicStorageAccess.collectAsStateWithLifecycle()
  val queuedItems by vm.selectedQueue.collectAsStateWithLifecycle()
  val discoveredDevices by vm.discoveredDevices.collectAsStateWithLifecycle()
  val isScanning by vm.isScanning.collectAsStateWithLifecycle()
  val wifiDirectStatus by vm.wifiDirectStatus.collectAsStateWithLifecycle()
  val readiness by vm.readiness.collectAsStateWithLifecycle()
  val hotspotInfo by vm.hotspotInfo.collectAsStateWithLifecycle()
  val isStartingHotspot by vm.isStartingHotspot.collectAsStateWithLifecycle()
  val receiverInfo by vm.receiverInfo.collectAsStateWithLifecycle()
  val transferProgress by vm.transferProgress.collectAsStateWithLifecycle()
  val history by vm.history.collectAsStateWithLifecycle()
  val benchmarkResult by vm.benchmarkResult.collectAsStateWithLifecycle()
  val isBenchmarkRunning by vm.isBenchmarkRunning.collectAsStateWithLifecycle()
  val statusBanner by vm.statusBanner.collectAsStateWithLifecycle()

  var currentTab by rememberSaveable { mutableStateOf(MainTab.TRANSFER) }
  var selectedCategory by rememberSaveable { mutableStateOf(ShareCategory.APPS) }
  var activeSubFlow by rememberSaveable { mutableStateOf(SubFlow.NONE) }

  val legacyStoragePermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      vm.refreshReadiness()
      vm.refreshLocalMedia()
    }

  val requestPublicStorageAccess = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      vm.openPublicStorageSettings()
    } else {
      val needsWrite =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
          PackageManager.PERMISSION_GRANTED
      if (needsWrite) {
        try {
          legacyStoragePermLauncher.launch(
            arrayOf(
              Manifest.permission.READ_EXTERNAL_STORAGE,
              Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
          )
        } catch (_: Throwable) {}
      } else {
        vm.refreshReadiness()
        vm.refreshLocalMedia()
      }
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        vm.refreshReadiness()
        vm.refreshLocalMedia()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(statusBanner) {
    val msg = statusBanner
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      vm.clearBanner()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    when (activeSubFlow) {
      SubFlow.SEND_RADAR -> {
        SendRadarScreen(
          queuedItems = queuedItems,
          discoveredDevices = discoveredDevices,
          isScanning = isScanning,
          wifiDirectStatus = wifiDirectStatus,
          readiness = readiness,
          onRefreshReadiness = { vm.refreshReadiness() },
          onStartRadar = { withP2p -> vm.startSenderRadar(withP2p) },
          onStopRadar = { vm.stopSenderRadar() },
          onSelectDevice = { dev -> vm.connectAndSendToDevice(dev) },
          onConnectManual = { host, port, pin -> vm.connectAndSendManual(host, port, pin) },
          onBack = { activeSubFlow = SubFlow.NONE },
        )
      }

      SubFlow.RECEIVE_RADAR -> {
        ReceiveRadarScreen(
          deviceName = deviceName,
          receiverInfo = receiverInfo,
          readiness = readiness,
          hotspotInfo = hotspotInfo,
          isStartingHotspot = isStartingHotspot,
          onRefreshReadiness = { vm.refreshReadiness() },
          onEnsureAutoHotspot = { vm.ensureAutoHotspotOnResume() },
          onStartReceive = { vm.startReceiveMode() },
          onStopReceive = { vm.stopReceiveMode() },
          onToggleHotspot = { vm.toggleReceiveHotspot() },
          onBack = { activeSubFlow = SubFlow.NONE },
        )
      }

      SubFlow.NONE -> {
        if (currentTab != MainTab.TRANSFER) {
          BackHandler { currentTab = MainTab.TRANSFER }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val useRail = maxWidth >= 600.dp

          Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
              if (!useRail) {
                NavigationBar(
                  containerColor = ObsidianSurface,
                  modifier = Modifier.navigationBarsPadding(),
                ) {
                  MainTab.entries.forEach { tab ->
                    val label =
                      when (tab) {
                        MainTab.TRANSFER -> stringResource(R.string.nav_transfer)
                        MainTab.SELECT -> stringResource(R.string.nav_select)
                        MainTab.HISTORY -> stringResource(R.string.nav_history)
                      }
                    NavigationBarItem(
                      selected = currentTab == tab,
                      onClick = { currentTab = tab },
                      icon = {
                        if (tab == MainTab.SELECT && queuedItems.isNotEmpty()) {
                          BadgedBox(badge = { Badge { Text("${queuedItems.size}") } }) {
                            Icon(tab.icon, contentDescription = label)
                          }
                        } else {
                          Icon(tab.icon, contentDescription = label)
                        }
                      },
                      label = { Text(label) },
                      colors =
                        NavigationBarItemDefaults.colors(
                          selectedIconColor = CyberCyan,
                          selectedTextColor = CyberCyan,
                          indicatorColor = ElectricViolet.copy(alpha = 0.25f),
                          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                      modifier = Modifier.testTag(tab.routeTag),
                    )
                  }
                }
              }
            },
          ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
              if (useRail) {
                NavigationRail(
                  containerColor = ObsidianSurface,
                  modifier = Modifier.fillMaxHeight(),
                ) {
                  MainTab.entries.forEach { tab ->
                    val label =
                      when (tab) {
                        MainTab.TRANSFER -> stringResource(R.string.nav_transfer)
                        MainTab.SELECT -> stringResource(R.string.nav_select)
                        MainTab.HISTORY -> stringResource(R.string.nav_history)
                      }
                    NavigationRailItem(
                      selected = currentTab == tab,
                      onClick = { currentTab = tab },
                      icon = {
                        if (tab == MainTab.SELECT && queuedItems.isNotEmpty()) {
                          BadgedBox(badge = { Badge { Text("${queuedItems.size}") } }) {
                            Icon(tab.icon, contentDescription = label)
                          }
                        } else {
                          Icon(tab.icon, contentDescription = label)
                        }
                      },
                      label = { Text(label) },
                      colors =
                        NavigationRailItemDefaults.colors(
                          selectedIconColor = CyberCyan,
                          selectedTextColor = CyberCyan,
                          indicatorColor = ElectricViolet.copy(alpha = 0.25f),
                        ),
                      modifier = Modifier.testTag(tab.routeTag),
                    )
                  }
                }
              }

              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (currentTab) {
                  MainTab.TRANSFER -> {
                    TransferHubScreen(
                      deviceName = deviceName,
                      turboMode = turboMode,
                      queuedItems = queuedItems,
                      recentHistory = history,
                      hasPublicStorageAccess = hasPublicStorageAccess,
                      benchmarkResult = benchmarkResult,
                      isBenchmarkRunning = isBenchmarkRunning,
                      onRenameDevice = { vm.setDeviceName(it) },
                      onToggleTurbo = { vm.setTurboMode(it) },
                      onOpenSendFlow = {
                        if (queuedItems.isNotEmpty()) {
                          activeSubFlow = SubFlow.SEND_RADAR
                        } else {
                          currentTab = MainTab.SELECT
                        }
                      },
                      onOpenReceiveFlow = { activeSubFlow = SubFlow.RECEIVE_RADAR },
                      onOpenCategory = { cat ->
                        selectedCategory = cat
                        currentTab = MainTab.SELECT
                      },
                      onRequestPublicStorage = requestPublicStorageAccess,
                      onOpenCredits = { vm.openCredits() },
                      onRunBenchmark = { vm.runTurboBenchmark() },
                      onOpenHistoryTab = { currentTab = MainTab.HISTORY },
                      onInstallApkRecord = { vm.installHistoryApk(it) },
                      onOpenRecord = { vm.openHistoryRecord(it) },
                      onShareRecord = { vm.shareHistoryRecord(it) },
                    )
                  }

                  MainTab.SELECT -> {
                    SelectContentScreen(
                      selectedCategory = selectedCategory,
                      onSelectCategory = { selectedCategory = it },
                      installedApps = installedApps,
                      appsLoading = appsLoading,
                      appSearchQuery = appSearchQuery,
                      onSearchQueryChange = { vm.updateAppSearch(it) },
                      localMedia = localMedia,
                      localMediaLoading = localMediaLoading,
                      hasPublicStorageAccess = hasPublicStorageAccess,
                      queuedItems = queuedItems,
                      onToggleApp = { vm.toggleAppSelection(it) },
                      onToggleLocalMedia = { vm.toggleLocalMediaSelection(it) },
                      onRefreshLocalMedia = { vm.refreshLocalMedia() },
                      onRequestPublicStorage = requestPublicStorageAccess,
                      onInstallLocalApk = { vm.installLocalApk(it) },
                      onAddMediaOrFiles = { uris, cat -> vm.addPickedMediaOrFiles(uris, cat) },
                      onAddFolder = { uri -> vm.addPickedFolder(uri) },
                      onRemoveQueueItem = { id -> vm.removeQueueItem(id) },
                      onClearQueue = { vm.clearQueue() },
                      onProceedToSendRadar = { activeSubFlow = SubFlow.SEND_RADAR },
                    )
                  }

                  MainTab.HISTORY -> {
                    HistoryScreen(
                      records = history,
                      hasPublicStorageAccess = hasPublicStorageAccess,
                      onRequestPublicStorage = requestPublicStorageAccess,
                      onInstallApk = { vm.installHistoryApk(it) },
                      onOpenRecord = { vm.openHistoryRecord(it) },
                      onShareRecord = { vm.shareHistoryRecord(it) },
                      onClearHistory = { vm.clearHistory() },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Active Transfer Speedometer Overlay
    TransferProgressOverlay(
      state = transferProgress,
      recentHistory = history,
      onInstallApk = { vm.installHistoryApk(it) },
      onCancel = { vm.cancelTransfer() },
      onDone = { vm.dismissTransferSummary() },
    )

    // Animated Developer Credits Overlay ("CREDITS", Dev Icon, "AMMAR H4CK34")
    AnimatedVisibility(
      visible = showCredits,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      CreditsOverlay(onDismiss = { vm.closeCredits() })
    }

    // Startup Branded Splash Screen ("FAST SHARE" + Animated Credits)
    AnimatedVisibility(
      visible = showSplash,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      SplashScreen(onSkip = { vm.dismissSplash() })
    }
  }
}
