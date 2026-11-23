package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.DiscoveredDevice
import com.example.model.DiscoverySource
import com.example.model.Formatters
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferProgressState
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.transfer.DeviceReadiness
import com.example.transfer.LocalHotspotInfo
import com.example.transfer.TransferEngine
import com.example.ui.ReceiverSessionInfo
import com.example.ui.components.AppIcons
import com.example.ui.components.QrScannerDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.DeepRoyalPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.ObsidianSurfaceHighest

private fun nearbyWifiPermissionName(): String =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.NEARBY_WIFI_DEVICES
  } else {
    Manifest.permission.ACCESS_FINE_LOCATION
  }

private fun openLocationSettings(context: Context) {
  try {
    context.startActivity(
      Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  } catch (_: Exception) {
    Toast.makeText(context, "Open Settings > Location and turn it ON", Toast.LENGTH_LONG).show()
  }
}

private fun openWifiSettings(context: Context) {
  try {
    val action =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
      else Settings.ACTION_WIFI_SETTINGS
    context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: Exception) {
    try {
      context.startActivity(
        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (_: Exception) {
      Toast.makeText(context, "Open Settings > Wi-Fi and turn it ON", Toast.LENGTH_LONG).show()
    }
  }
}

@Composable
private fun ResumeReadinessEffect(onResume: () -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        onResume()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

@Composable
private fun DeviceReadinessCard(
  readiness: DeviceReadiness,
  onRequestNearbyPermission: () -> Unit,
) {
  if (readiness.isHotspotAndP2pReady) return

  val context = LocalContext.current
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = AmberWarning.copy(alpha = 0.12f),
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, AmberWarning.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
        .testTag("device_readiness_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(AppIcons.ErrorOutline, contentDescription = null, tint = AmberWarning)
        Text(
          text = "Turn On Location & Wi-Fi for Fast Transfer",
          style = MaterialTheme.typography.titleMedium,
          color = AmberWarning,
          fontWeight = FontWeight.Bold,
        )
      }

      if (!readiness.locationEnabled) {
        ReadinessActionRow(
          label = "Location Services is OFF — Android requires Location ON for Hotspot & Wi-Fi Direct",
          buttonText = "Turn On Location",
          tag = "turn_on_location_button",
          onClick = { openLocationSettings(context) },
        )
      }

      if (!readiness.wifiEnabled) {
        ReadinessActionRow(
          label = "Wi-Fi is OFF — turn ON Wi-Fi for direct high-speed pairing",
          buttonText = "Turn On Wi-Fi",
          tag = "turn_on_wifi_button",
          onClick = { openWifiSettings(context) },
        )
      }

      if (!readiness.hasNearbyPermission) {
        ReadinessActionRow(
          label = "Nearby Wi-Fi permission is needed for radar & Fast Share Auto-Hotspot",
          buttonText = "Grant Permission",
          tag = "grant_nearby_permission_button",
          onClick = onRequestNearbyPermission,
        )
      }
    }
  }
}

@Composable
private fun ReadinessActionRow(
  label: String,
  buttonText: String,
  tag: String,
  onClick: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Button(
      onClick = onClick,
      colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = ObsidianBg),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag(tag),
    ) {
      Text(buttonText, fontWeight = FontWeight.Bold)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendRadarScreen(
  queuedItems: List<ShareableItem>,
  discoveredDevices: List<DiscoveredDevice>,
  isScanning: Boolean,
  wifiDirectStatus: String,
  readiness: DeviceReadiness,
  onRefreshReadiness: () -> Unit,
  onStartRadar: (Boolean) -> Unit,
  onStopRadar: () -> Unit,
  onSelectDevice: (DiscoveredDevice) -> Unit,
  onConnectManual: (String, String, String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  ResumeReadinessEffect(onRefreshReadiness)

  val context = LocalContext.current
  var manualHost by remember { mutableStateOf("") }
  var manualPort by remember { mutableStateOf(TransferEngine.DEFAULT_PORT.toString()) }
  var manualPin by remember { mutableStateOf("") }
  var showQrScanner by remember { mutableStateOf(false) }

  val nearbyPermission = nearbyWifiPermissionName()

  val wifiDirectPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onRefreshReadiness()
      onStartRadar(granted)
    }

  val cameraPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        showQrScanner = true
      } else {
        Toast.makeText(
            context,
            "Camera permission is required to scan the receiver QR code",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
    }

  LaunchedEffect(Unit) { onStartRadar(false) }
  DisposableEffect(Unit) { onDispose { onStopRadar() } }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      containerColor = ObsidianBg,
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("send_radar_back")) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
              )
            }
          },
          title = {
            Column {
              Text("Nearby Receivers", style = MaterialTheme.typography.titleLarge)
              Text(
                text =
                  "${queuedItems.size} item(s) • ${Formatters.formatBytes(queuedItems.sumOf { it.sizeBytes })}",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
              )
            }
          },
          actions = {
            IconButton(
              onClick = {
                onRefreshReadiness()
                onStartRadar(false)
              },
              modifier = Modifier.testTag("rescan_button"),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Rescan", tint = NeonViolet)
            }
          },
        )
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        LazyColumn(
          modifier = Modifier.fillMaxSize().widthIn(max = 680.dp),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          if (!readiness.isHotspotAndP2pReady) {
            item {
              DeviceReadinessCard(
                readiness = readiness,
                onRequestNearbyPermission = { wifiDirectPermLauncher.launch(nearbyPermission) },
              )
            }
          }

          // Animated Radar Sweep + Scan Receiver QR Button
          item {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = ObsidianSurfaceHigh,
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, ObsidianCardBorder, RoundedCornerShape(24.dp)),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                RadarSweepVisual(isActive = isScanning)
                Text(
                  text =
                    if (discoveredDevices.isEmpty()) "Scanning nearby phones on Wi-Fi / Hotspot..."
                    else "Tap any phone below to send at Turbo speed",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  textAlign = TextAlign.Center,
                )

                Button(
                  onClick = {
                    try {
                      cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    } catch (_: Throwable) {}
                  },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = CyberCyan,
                      contentColor = ObsidianBg,
                    ),
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("scan_qr_button"),
                ) {
                  Icon(AppIcons.QrCodeScanner, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Scan QR to Auto-Connect & Send", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                  onClick = {
                    if (!readiness.locationEnabled) {
                      Toast.makeText(
                          context,
                          "Turn ON Location Services first for Wi-Fi Direct",
                          Toast.LENGTH_SHORT,
                        )
                        .show()
                    }
                    try {
                      wifiDirectPermLauncher.launch(nearbyPermission)
                    } catch (_: Throwable) {}
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("wifi_direct_scan_btn"),
                ) {
                  Icon(AppIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    "Scan Wi-Fi Direct ($wifiDirectStatus)",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }

          // Discovered Devices List
          if (discoveredDevices.isNotEmpty()) {
            item {
              Text(
                text = "Discovered Devices (${discoveredDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                color = CyberCyan,
              )
            }
            items(discoveredDevices, key = { it.id }) { device ->
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurfaceHigh,
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, ElectricViolet, RoundedCornerShape(18.dp))
                    .clickable { onSelectDevice(device) }
                    .minimumInteractiveComponentSize()
                    .testTag("discovered_device_${device.id}"),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier =
                      Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(AppIcons.PhoneAndroid, contentDescription = null, tint = CyberCyan)
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = device.name,
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    val sourceLabel =
                      when (device.source) {
                        DiscoverySource.NSD_WIFI ->
                          "Wi-Fi / Hotspot • ${device.host}:${device.port}"
                        DiscoverySource.WIFI_DIRECT -> "Wi-Fi Direct • ${device.host}"
                        DiscoverySource.MANUAL -> "Direct IP • ${device.host}"
                      }
                    Text(
                      text = sourceLabel,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send to ${device.name}",
                    tint = CyberCyan,
                  )
                }
              }
            }
          }

          // Manual IP / Hotspot / QR Code Link Connect
          item {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = ObsidianSurface,
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, ObsidianCardBorder, RoundedCornerShape(22.dp)),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = "Direct Hotspot / IP Connect",
                  style = MaterialTheme.typography.titleMedium,
                  color = NeonViolet,
                )
                Text(
                  text =
                    "Or enter the receiver's IP & 4-digit PIN (or paste their fastshare:// link).",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                  value = manualHost,
                  onValueChange = { manualHost = it },
                  singleLine = true,
                  label = { Text("Receiver IP or fastshare:// link") },
                  placeholder = { Text("192.168.49.1") },
                  modifier = Modifier.fillMaxWidth().testTag("manual_host_input"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it },
                    singleLine = true,
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("manual_port_input"),
                  )
                  OutlinedTextField(
                    value = manualPin,
                    onValueChange = { manualPin = it.take(4) },
                    singleLine = true,
                    label = { Text("4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("manual_pin_input"),
                  )
                }
                Button(
                  onClick = { onConnectManual(manualHost, manualPort, manualPin) },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = ElectricViolet,
                      contentColor = Color.White,
                    ),
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("manual_connect_send_button"),
                ) {
                  Icon(AppIcons.Bolt, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Connect & Send Now")
                }
              }
            }
          }
        }
      }
    }

    if (showQrScanner) {
      QrScannerDialog(
        onQrScanned = { scanned ->
          showQrScanner = false
          onConnectManual(scanned, manualPort, manualPin)
        },
        onDismiss = { showQrScanner = false },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveRadarScreen(
  deviceName: String,
  receiverInfo: ReceiverSessionInfo,
  readiness: DeviceReadiness,
  hotspotInfo: LocalHotspotInfo,
  isStartingHotspot: Boolean,
  onRefreshReadiness: () -> Unit,
  onEnsureAutoHotspot: () -> Unit,
  onStartReceive: () -> Unit,
  onStopReceive: () -> Unit,
  onToggleHotspot: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  ResumeReadinessEffect {
    onRefreshReadiness()
    onEnsureAutoHotspot()
  }

  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val nearbyPermission = nearbyWifiPermissionName()

  val nearbyPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onRefreshReadiness()
      if (granted) {
        onToggleHotspot()
      } else {
        Toast.makeText(
            context,
            "Nearby Wi-Fi / Location permission is required for Fast Share Auto-Hotspot",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
    }

  LaunchedEffect(Unit) { onStartReceive() }
  DisposableEffect(Unit) { onDispose { onStopReceive() } }

  val pairingLink =
    remember(receiverInfo, deviceName) {
      val base =
        "fastshare://${receiverInfo.localIp}:${receiverInfo.port}?pin=${receiverInfo.pin}&name=${Uri.encode(deviceName)}"
      if (receiverInfo.hotspotSsid.isNotBlank()) {
        val pwdParam =
          if (receiverInfo.hotspotPassphrase.isNotBlank())
            "&pwd=${Uri.encode(receiverInfo.hotspotPassphrase)}"
          else ""
        "$base&ssid=${Uri.encode(receiverInfo.hotspotSsid)}$pwdParam"
      } else {
        base
      }
    }

  Scaffold(
    containerColor = ObsidianBg,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("receive_radar_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        title = {
          Column {
            Text("Receive Mode", style = MaterialTheme.typography.titleLarge)
            Text(
              text = "Saving to /storage/emulated/0/Ammarshare",
              style = MaterialTheme.typography.labelSmall,
              color = CyberCyan,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 600.dp),
        contentPadding = PaddingValues(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (!readiness.isHotspotAndP2pReady) {
          item {
            DeviceReadinessCard(
              readiness = readiness,
              onRequestNearbyPermission = {
                try {
                  nearbyPermLauncher.launch(nearbyPermission)
                } catch (_: Throwable) {}
              },
            )
          }
        }

        // Direct Fast Share Auto-Hotspot Card (like SHAREit)
        item {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color =
              if (hotspotInfo.active) EmeraldSuccess.copy(alpha = 0.12f) else ObsidianSurfaceHigh,
            modifier =
              Modifier.fillMaxWidth()
                .border(
                  1.5.dp,
                  if (hotspotInfo.active) EmeraldSuccess else ElectricViolet.copy(alpha = 0.7f),
                  RoundedCornerShape(22.dp),
                ),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = AppIcons.Wifi,
                  contentDescription = null,
                  tint = if (hotspotInfo.active) EmeraldSuccess else CyberCyan,
                )
                Text(
                  text =
                    if (hotspotInfo.active) "Fast Share Auto-Hotspot Active"
                    else "Fast Share Auto-Hotspot",
                  style = MaterialTheme.typography.titleMedium,
                  color = if (hotspotInfo.active) EmeraldSuccess else CyberCyan,
                  fontWeight = FontWeight.Bold,
                )
              }

              Text(
                text =
                  if (hotspotInfo.active)
                    "QR code below carries Hotspot credentials. Sender scanning this QR will auto-join & transfer immediately!"
                  else
                    "Starts automatically when Wi-Fi & Location are ready — bypasses slow routers for 100+ MB/s SHAREit-style transfers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              if (hotspotInfo.active && hotspotInfo.ssid.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = ObsidianSurface,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(
                      text = "Hotspot Wi-Fi: ${hotspotInfo.ssid}",
                      style = MaterialTheme.typography.labelMedium,
                      fontFamily = JetBrainsMonoFamily,
                      color = CyberCyan,
                      modifier = Modifier.testTag("hotspot_ssid_text"),
                    )
                    if (hotspotInfo.passphrase.isNotBlank()) {
                      Text(
                        text = "Password: ${hotspotInfo.passphrase}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = NeonViolet,
                      )
                    }
                    if (hotspotInfo.modeLabel.isNotBlank()) {
                      Text(
                        text = "Engine: ${hotspotInfo.modeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess,
                      )
                    }
                  }
                }
              }

              if (!hotspotInfo.error.isNullOrBlank()) {
                Text(
                  text = hotspotInfo.error,
                  style = MaterialTheme.typography.bodyMedium,
                  color = AmberWarning,
                )
              }

              if (hotspotInfo.active) {
                OutlinedButton(
                  onClick = onToggleHotspot,
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("toggle_hotspot_button"),
                ) {
                  Text("Stop Fast Share Hotspot", color = CrimsonError)
                }
              } else {
                Button(
                  enabled = !isStartingHotspot,
                  onClick = {
                    if (!readiness.hasNearbyPermission) {
                      try {
                        nearbyPermLauncher.launch(nearbyPermission)
                      } catch (_: Throwable) {}
                    } else if (!readiness.locationEnabled) {
                      Toast.makeText(
                          context,
                          "Turn ON Location Services first to start Hotspot",
                          Toast.LENGTH_SHORT,
                        )
                        .show()
                      openLocationSettings(context)
                    } else if (!readiness.wifiEnabled) {
                      Toast.makeText(context, "Turn ON Wi-Fi first", Toast.LENGTH_SHORT).show()
                      openWifiSettings(context)
                    } else {
                      onToggleHotspot()
                    }
                  },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = CyberCyan,
                      contentColor = ObsidianBg,
                    ),
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("toggle_hotspot_button"),
                ) {
                  if (isStartingHotspot) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(18.dp),
                      strokeWidth = 2.dp,
                      color = ObsidianBg,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starting Hotspot...", fontWeight = FontWeight.Bold)
                  } else {
                    Icon(AppIcons.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Fast Share Hotspot", fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }

        // QR Code & PIN Card
        item {
          Surface(
            shape = RoundedCornerShape(26.dp),
            color = ObsidianSurfaceHigh,
            modifier =
              Modifier.fillMaxWidth()
                .border(
                  1.5.dp,
                  Brush.linearGradient(listOf(CyberCyan, ElectricViolet)),
                  RoundedCornerShape(26.dp),
                ),
          ) {
            Column(
              modifier = Modifier.padding(22.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(AppIcons.Radar, contentDescription = null, tint = CyberCyan)
                Text(
                  text =
                    if (receiverInfo.isListening) "Scan QR on Sender Phone ($deviceName)"
                    else "Starting Turbo Receiver...",
                  style = MaterialTheme.typography.titleMedium,
                  color = CyberCyan,
                )
              }

              val qr = receiverInfo.qrBitmap
              if (qr != null) {
                Surface(
                  shape = RoundedCornerShape(20.dp),
                  color = Color.White,
                  modifier = Modifier.size(224.dp),
                ) {
                  Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_qr_code),
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "PAIRING PIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = receiverInfo.pin,
                    style = MaterialTheme.typography.displayLarge,
                    fontFamily = JetBrainsMonoFamily,
                    color = NeonViolet,
                    letterSpacing = 6.sp,
                    modifier = Modifier.testTag("receiver_pin_text"),
                  )
                  Text(
                    text = "${receiverInfo.localIp}:${receiverInfo.port}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = CyberCyan,
                    modifier = Modifier.testTag("receiver_ip_text"),
                  )
                }
              }

              OutlinedButton(
                onClick = {
                  clipboard.setText(AnnotatedString(pairingLink))
                  Toast.makeText(context, "Pairing link copied", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("copy_pairing_link_button"),
              ) {
                Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy QR Pairing Link")
              }

              Text(
                text =
                  "Sender can scan this QR code directly, tap '$deviceName' on Radar, or connect via Hotspot IP & PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TransferProgressOverlay(
  state: TransferProgressState,
  recentHistory: List<TransferRecord> = emptyList(),
  onInstallApk: (TransferRecord) -> Unit = {},
  onCancel: () -> Unit,
  onDone: () -> Unit,
) {
  if (state.status == TransferStatus.IDLE || state.status == TransferStatus.LISTENING) return

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ObsidianBg.copy(alpha = 0.92f))
        .safeDrawingPadding()
        .padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = ObsidianSurfaceHigh,
      tonalElevation = 12.dp,
      modifier =
        Modifier.widthIn(max = 500.dp)
          .fillMaxWidth()
          .border(
            1.5.dp,
            Brush.linearGradient(listOf(ElectricViolet, CyberCyan, CyberMagenta)),
            RoundedCornerShape(28.dp),
          )
          .testTag("transfer_progress_card"),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        val statusLabel =
          when (state.status) {
            TransferStatus.CONNECTING -> "CONNECTING TO ${state.peerName.uppercase()}"
            TransferStatus.TRANSFERRING ->
              if (state.direction == TransferDirection.SEND) "SENDING TO ${state.peerName}"
              else "RECEIVING FROM ${state.peerName}"
            TransferStatus.COMPLETED -> "TRANSFER COMPLETE"
            TransferStatus.FAILED -> "TRANSFER FAILED"
            TransferStatus.CANCELLED -> "TRANSFER CANCELLED"
            else -> ""
          }

        Surface(
          shape = RoundedCornerShape(50),
          color = ObsidianSurfaceHighest,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                when (state.status) {
                  TransferStatus.COMPLETED -> Icons.Rounded.CheckCircle
                  TransferStatus.FAILED, TransferStatus.CANCELLED -> AppIcons.ErrorOutline
                  else -> AppIcons.Bolt
                },
              contentDescription = null,
              tint =
                when (state.status) {
                  TransferStatus.COMPLETED -> EmeraldSuccess
                  TransferStatus.FAILED -> CrimsonError
                  else -> CyberCyan
                },
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        // Big Speedometer Readout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = Formatters.formatSpeed(state.speedMBps),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = JetBrainsMonoFamily,
            color = CyberCyan,
            modifier = Modifier.testTag("live_speed_text"),
          )
          Text(
            text =
              "Peak: ${Formatters.formatSpeed(state.peakSpeedMBps)} • ${state.activeStreams}x Stream • ${state.elapsedMs} ms",
            style = MaterialTheme.typography.labelSmall,
            color = NeonViolet,
          )
        }

        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = state.currentFileName.ifBlank { "Preparing stream..." },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${state.currentFileIndex}/${state.totalFiles}",
              style = MaterialTheme.typography.labelSmall,
              color = CyberCyan,
            )
          }

          LinearProgressIndicator(
            progress = { state.progressFraction },
            color = CyberCyan,
            trackColor = DeepRoyalPurple.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text =
                "${Formatters.formatBytes(state.bytesTransferred)} / ${Formatters.formatBytes(state.totalBytes)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                if (state.status == TransferStatus.TRANSFERRING && state.etaSeconds > 0)
                  "ETA ${state.etaSeconds}s"
                else "${(state.progressFraction * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (!state.errorMessage.isNullOrBlank()) {
          Text(
            text = state.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = CrimsonError,
            textAlign = TextAlign.Center,
          )
        }

        if (state.status == TransferStatus.CONNECTING || state.status == TransferStatus.TRANSFERRING) {
          OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("cancel_transfer_button"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = CrimsonError)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Transfer", color = CrimsonError)
          }
        } else {
          val installableApk =
            if (state.status == TransferStatus.COMPLETED && state.direction == TransferDirection.RECEIVE) {
              recentHistory.take(12).firstOrNull { rec ->
                rec.direction == TransferDirection.RECEIVE &&
                  (rec.fileName.equals("base.apk", ignoreCase = true) ||
                    (rec.fileName.endsWith(".apk", ignoreCase = true) &&
                      !rec.fileName.startsWith("split_", ignoreCase = true)))
              }
            } else null

          if (installableApk != null) {
            Button(
              onClick = { onInstallApk(installableApk) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = EmeraldSuccess,
                  contentColor = ObsidianBg,
                ),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("overlay_install_apk_button"),
            ) {
              Icon(AppIcons.InstallApk, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Install ${installableApk.fileName}", fontWeight = FontWeight.Bold)
            }
          }

          Button(
            onClick = onDone,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("done_transfer_button"),
          ) {
            Text("Done")
          }
        }
      }
    }
  }
}

@Composable
private fun RadarSweepVisual(isActive: Boolean) {
  val infinite = rememberInfiniteTransition(label = "radarSweep")
  val angle by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
      label = "sweepAngle",
    )

  Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val radius = size.minDimension / 2f
      drawCircle(
        color = ElectricViolet.copy(alpha = 0.25f),
        radius = radius * 0.95f,
        style = Stroke(width = 1.5.dp.toPx()),
      )
      drawCircle(
        color = ElectricViolet.copy(alpha = 0.35f),
        radius = radius * 0.65f,
        style = Stroke(width = 1.5.dp.toPx()),
      )
      drawCircle(
        color = CyberCyan.copy(alpha = 0.45f),
        radius = radius * 0.35f,
        style = Stroke(width = 1.5.dp.toPx()),
      )
      if (isActive) {
        rotate(angle) {
          drawLine(
            brush = Brush.linearGradient(listOf(CyberCyan, ElectricViolet.copy(alpha = 0f))),
            start = center,
            end = Offset(center.x, center.y - radius * 0.95f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
          )
        }
      }
    }
    Box(
      modifier = Modifier.size(36.dp).clip(CircleShape).background(ElectricViolet),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = AppIcons.Radar,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
