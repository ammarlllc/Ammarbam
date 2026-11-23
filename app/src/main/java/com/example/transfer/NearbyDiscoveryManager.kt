package com.example.transfer

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.model.DiscoveredDevice
import com.example.model.DiscoverySource
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceReadiness(
  val wifiEnabled: Boolean = true,
  val locationEnabled: Boolean = true,
  val hasNearbyPermission: Boolean = true,
) {
  val isHotspotAndP2pReady: Boolean
    get() = wifiEnabled && locationEnabled && hasNearbyPermission
}

data class LocalHotspotInfo(
  val active: Boolean = false,
  val ssid: String = "",
  val passphrase: String = "",
  val hostIp: String = "",
  val modeLabel: String = "",
  val error: String? = null,
)

class NearbyDiscoveryManager(private val context: Context) {

  companion object {
    const val SERVICE_TYPE = "_ammarshare._tcp."
    private const val SERVICE_PREFIX = "AmmarShare"
    private const val DEFAULT_P2P_GO_IP = "192.168.49.1"
  }

  private val mainHandler = Handler(Looper.getMainLooper())

  private val wifiManager: WifiManager? =
    try {
      context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (_: Throwable) {
      null
    }

  private val locationManager: LocationManager? =
    try {
      context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    } catch (_: Throwable) {
      null
    }

  private val connectivityManager: ConnectivityManager? =
    try {
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (_: Throwable) {
      null
    }

  private val nsdManager: NsdManager? =
    try {
      context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (_: Throwable) {
      null
    }

  private val wifiP2pManager: WifiP2pManager? =
    try {
      context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    } catch (_: Throwable) {
      null
    }

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _wifiDirectStatus = MutableStateFlow("Ready")
  val wifiDirectStatus: StateFlow<String> = _wifiDirectStatus.asStateFlow()

  // Initialized with default value BEFORE init calls checkReadiness() to avoid NPE during construction
  private val _readiness = MutableStateFlow(DeviceReadiness())
  val readiness: StateFlow<DeviceReadiness> = _readiness.asStateFlow()

  private val _hotspotInfo = MutableStateFlow(LocalHotspotInfo())
  val hotspotInfo: StateFlow<LocalHotspotInfo> = _hotspotInfo.asStateFlow()

  private val _connectedP2pHost = MutableStateFlow<String?>(null)
  val connectedP2pHost: StateFlow<String?> = _connectedP2pHost.asStateFlow()

  private var registrationListener: NsdManager.RegistrationListener? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null
  private var p2pChannel: WifiP2pManager.Channel? = null
  private var p2pReceiver: BroadcastReceiver? = null
  private var registeredServiceName: String? = null
  private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
  private var activeHotspotCallback: ConnectivityManager.NetworkCallback? = null
  private var p2pGroupCreated = false

  init {
    checkReadiness()
  }

  fun checkReadiness(): DeviceReadiness {
    val wifiOn =
      try {
        wifiManager?.isWifiEnabled ?: true
      } catch (_: Throwable) {
        true
      }
    val locationOn =
      try {
        locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: true
      } catch (_: Throwable) {
        true
      }
    val nearbyPerm =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
        } else {
          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        }
      } catch (_: Throwable) {
        false
      }
    val state =
      DeviceReadiness(
        wifiEnabled = wifiOn,
        locationEnabled = locationOn,
        hasNearbyPermission = nearbyPerm,
      )
    _readiness.value = state
    return state
  }

  private fun ensureP2pChannel(): Pair<WifiP2pManager, WifiP2pManager.Channel>? {
    val manager = wifiP2pManager ?: return null
    if (p2pChannel == null) {
      p2pChannel =
        try {
          manager.initialize(context, Looper.getMainLooper()) { p2pChannel = null }
        } catch (_: Throwable) {
          null
        }
    }
    val channel = p2pChannel ?: return null
    registerP2pReceiver(manager, channel)
    return manager to channel
  }

  /**
   * Programmatically creates a SHAREit-style direct Wi-Fi Hotspot via WifiP2pManager.createGroup
   * (with custom DIRECT-AS-AmmarShare SSID on Android 10+) and falls back to LocalOnlyHotspot
   * if P2P Group Owner creation is unavailable on the device.
   */
  @SuppressLint("MissingPermission")
  fun startLocalHotspot(
    deviceName: String = "FastShare",
    onReady: (LocalHotspotInfo) -> Unit,
  ) {
    val state = checkReadiness()
    if (!state.wifiEnabled) {
      val info = LocalHotspotInfo(error = "Turn ON Wi-Fi first to create Fast Share Hotspot.")
      _hotspotInfo.value = info
      onReady(info)
      return
    }
    if (!state.locationEnabled) {
      val info =
        LocalHotspotInfo(
          error = "Turn ON Location Services first (required by Android for Wi-Fi Direct Hotspot)."
        )
      _hotspotInfo.value = info
      onReady(info)
      return
    }
    if (!state.hasNearbyPermission) {
      val info =
        LocalHotspotInfo(error = "Grant Nearby Wi-Fi / Location permission to start Hotspot.")
      _hotspotInfo.value = info
      onReady(info)
      return
    }

    stopLocalHotspot()

    val p2p = ensureP2pChannel()
    if (p2p == null) {
      startLocalOnlyHotspotFallback(onReady)
      return
    }

    val (manager, channel) = p2p
    val suffix =
      deviceName.replace(Regex("[^a-zA-Z0-9]"), "").take(8).ifEmpty { "Phone" }
    val desiredSsid = "DIRECT-FS-FastShare_$suffix".take(31)
    val desiredPass = "fast${Random.nextInt(1000, 9999)}"
    val supports5Ghz =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        try {
          wifiManager?.is5GHzBandSupported == true
        } catch (_: Throwable) {
          false
        }

    startP2pCreateGroupDirect(
      manager = manager,
      channel = channel,
      desiredSsid = desiredSsid,
      desiredPass = desiredPass,
      prefer5Ghz = supports5Ghz,
      allowAutoBandRetry = supports5Ghz,
      removeBusyFirst = true,
      onReady = onReady,
    )
  }

  @SuppressLint("MissingPermission")
  private fun startP2pCreateGroupDirect(
    manager: WifiP2pManager,
    channel: WifiP2pManager.Channel,
    desiredSsid: String,
    desiredPass: String,
    prefer5Ghz: Boolean,
    allowAutoBandRetry: Boolean,
    removeBusyFirst: Boolean,
    onReady: (LocalHotspotInfo) -> Unit,
  ) {
    val bandLabel =
      if (prefer5Ghz) "Wi-Fi Direct 5GHz Turbo" else "Wi-Fi Direct Auto-Band"

    val listener =
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          p2pGroupCreated = true
          _wifiDirectStatus.value =
            if (prefer5Ghz) "5GHz Turbo Hotspot Active" else "Hotspot Group Active"
          pollP2pGroupCredentials(
            manager = manager,
            channel = channel,
            attemptsLeft = 8,
            fallbackSsid = desiredSsid,
            fallbackPass = desiredPass,
            modeLabel = bandLabel,
            onReady = onReady,
          )
        }

        override fun onFailure(reason: Int) {
          if (reason == WifiP2pManager.BUSY && removeBusyFirst) {
            try {
              manager.removeGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                  override fun onSuccess() {
                    startP2pCreateGroupDirect(
                      manager = manager,
                      channel = channel,
                      desiredSsid = desiredSsid,
                      desiredPass = desiredPass,
                      prefer5Ghz = prefer5Ghz,
                      allowAutoBandRetry = allowAutoBandRetry,
                      removeBusyFirst = false,
                      onReady = onReady,
                    )
                  }

                  override fun onFailure(removeReason: Int) {
                    if (prefer5Ghz && allowAutoBandRetry) {
                      startP2pCreateGroupDirect(
                        manager = manager,
                        channel = channel,
                        desiredSsid = desiredSsid,
                        desiredPass = desiredPass,
                        prefer5Ghz = false,
                        allowAutoBandRetry = false,
                        removeBusyFirst = false,
                        onReady = onReady,
                      )
                    } else {
                      startLocalOnlyHotspotFallback(onReady)
                    }
                  }
                },
              )
            } catch (_: Throwable) {
              startLocalOnlyHotspotFallback(onReady)
            }
          } else if (prefer5Ghz && allowAutoBandRetry) {
            startP2pCreateGroupDirect(
              manager = manager,
              channel = channel,
              desiredSsid = desiredSsid,
              desiredPass = desiredPass,
              prefer5Ghz = false,
              allowAutoBandRetry = false,
              removeBusyFirst = false,
              onReady = onReady,
            )
          } else {
            startLocalOnlyHotspotFallback(onReady)
          }
        }
      }

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val band =
          if (prefer5Ghz) WifiP2pConfig.GROUP_OWNER_BAND_5GHZ
          else WifiP2pConfig.GROUP_OWNER_BAND_AUTO
        val config =
          WifiP2pConfig.Builder()
            .setNetworkName(desiredSsid)
            .setPassphrase(desiredPass)
            .enablePersistentMode(false)
            .setGroupOperatingBand(band)
            .build()
        manager.createGroup(channel, config, listener)
      } else {
        manager.createGroup(channel, listener)
      }
    } catch (_: Throwable) {
      if (prefer5Ghz && allowAutoBandRetry) {
        startP2pCreateGroupDirect(
          manager = manager,
          channel = channel,
          desiredSsid = desiredSsid,
          desiredPass = desiredPass,
          prefer5Ghz = false,
          allowAutoBandRetry = false,
          removeBusyFirst = false,
          onReady = onReady,
        )
      } else {
        startLocalOnlyHotspotFallback(onReady)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun pollP2pGroupCredentials(
    manager: WifiP2pManager,
    channel: WifiP2pManager.Channel,
    attemptsLeft: Int,
    fallbackSsid: String,
    fallbackPass: String,
    modeLabel: String,
    onReady: (LocalHotspotInfo) -> Unit,
  ) {
    try {
      manager.requestGroupInfo(channel) { group ->
        val netName = group?.networkName
        if (group != null && !netName.isNullOrBlank()) {
          val pwd = group.passphrase.orEmpty().ifBlank { fallbackPass }
          manager.requestConnectionInfo(channel) { connInfo ->
            val goIp =
              connInfo?.groupOwnerAddress?.hostAddress
                ?.takeIf { it.isNotBlank() }
                ?: getLocalWifiIpAddress(preferHotspot = true).takeIf { it != "127.0.0.1" }
                ?: DEFAULT_P2P_GO_IP
            val info =
              LocalHotspotInfo(
                active = true,
                ssid = netName,
                passphrase = pwd,
                hostIp = goIp,
                modeLabel = modeLabel,
              )
            _hotspotInfo.value = info
            onReady(info)
          }
        } else if (attemptsLeft > 0) {
          mainHandler.postDelayed(
            {
              pollP2pGroupCredentials(
                manager = manager,
                channel = channel,
                attemptsLeft = attemptsLeft - 1,
                fallbackSsid = fallbackSsid,
                fallbackPass = fallbackPass,
                modeLabel = modeLabel,
                onReady = onReady,
              )
            },
            250L,
          )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val goIp =
            getLocalWifiIpAddress(preferHotspot = true).takeIf { it != "127.0.0.1" }
              ?: DEFAULT_P2P_GO_IP
          val info =
            LocalHotspotInfo(
              active = true,
              ssid = fallbackSsid,
              passphrase = fallbackPass,
              hostIp = goIp,
              modeLabel = modeLabel,
            )
          _hotspotInfo.value = info
          onReady(info)
        } else {
          startLocalOnlyHotspotFallback(onReady)
        }
      }
    } catch (_: Throwable) {
      startLocalOnlyHotspotFallback(onReady)
    }
  }

  @SuppressLint("MissingPermission")
  private fun startLocalOnlyHotspotFallback(onReady: (LocalHotspotInfo) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      val info =
        LocalHotspotInfo(
          error = "Turn ON Wi-Fi & Location Services or open phone Hotspot manually."
        )
      _hotspotInfo.value = info
      onReady(info)
      return
    }

    val wm = wifiManager ?: run {
      val info = LocalHotspotInfo(error = "Wi-Fi service unavailable on this device.")
      _hotspotInfo.value = info
      onReady(info)
      return
    }

    try {
      wm.startLocalOnlyHotspot(
        object : WifiManager.LocalOnlyHotspotCallback() {
          override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
            hotspotReservation = reservation
            var ssid = "FastShare_Hotspot"
            var pwd = ""
            if (reservation != null) {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val softAp = reservation.softApConfiguration
                ssid = softAp.ssid ?: ssid
                pwd = softAp.passphrase ?: ""
              } else {
                @Suppress("DEPRECATION")
                val wc = reservation.wifiConfiguration
                if (wc != null) {
                  ssid = wc.SSID ?: ssid
                  pwd = wc.preSharedKey ?: ""
                }
              }
            }
            val resolvedIp = getLocalWifiIpAddress(preferHotspot = true)
            val info =
              LocalHotspotInfo(
                active = true,
                ssid = ssid,
                passphrase = pwd,
                hostIp = if (resolvedIp == "127.0.0.1") "" else resolvedIp,
                modeLabel = "Direct SoftAP Hotspot",
              )
            _hotspotInfo.value = info
            onReady(info)
          }

          override fun onStopped() {
            hotspotReservation = null
            if (!p2pGroupCreated) {
              _hotspotInfo.value = LocalHotspotInfo()
            }
          }

          override fun onFailed(reason: Int) {
            val msg =
              when (reason) {
                ERROR_INCOMPATIBLE_MODE ->
                  "Turn off system Tethering Hotspot first, then retry Fast Share Hotspot."
                ERROR_TETHERING_DISALLOWED -> "Hotspot disabled by device policy."
                ERROR_NO_CHANNEL -> "Busy Wi-Fi channel — turn Wi-Fi OFF/ON and retry."
                else -> "Could not start Hotspot (Code $reason). Turn ON Location & Wi-Fi."
              }
            val info = LocalHotspotInfo(error = msg)
            _hotspotInfo.value = info
            onReady(info)
          }
        },
        mainHandler,
      )
    } catch (e: SecurityException) {
      val info =
        LocalHotspotInfo(error = "Permission required: ${e.localizedMessage ?: "Nearby Wi-Fi"}")
      _hotspotInfo.value = info
      onReady(info)
    } catch (e: Throwable) {
      val info = LocalHotspotInfo(error = e.localizedMessage ?: "Hotspot unavailable")
      _hotspotInfo.value = info
      onReady(info)
    }
  }

  fun stopLocalHotspot() {
    if (p2pGroupCreated) {
      p2pGroupCreated = false
      val ch = p2pChannel
      val mgr = wifiP2pManager
      if (ch != null && mgr != null) {
        try {
          mgr.removeGroup(ch, null)
        } catch (_: Throwable) {}
      }
    }
    try {
      hotspotReservation?.close()
    } catch (_: Throwable) {}
    hotspotReservation = null
    _hotspotInfo.value = LocalHotspotInfo()
  }

  fun connectToHotspot(
    ssid: String,
    passphrase: String,
    onConnected: (Boolean, String) -> Unit,
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      onConnected(false, "Join Wi-Fi '$ssid' (Pass: $passphrase) in Settings, then tap Send.")
      return
    }
    val cm = connectivityManager ?: run {
      onConnected(false, "Connectivity service unavailable")
      return
    }
    releaseBoundHotspotNetwork()

    val delivered = AtomicBoolean(false)
    try {
      val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
      if (passphrase.isNotBlank()) {
        builder.setWpa2Passphrase(passphrase)
      }
      val request =
        NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          .setNetworkSpecifier(builder.build())
          .build()

      val cb =
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            try {
              cm.bindProcessToNetwork(network)
            } catch (_: Throwable) {}
            if (delivered.compareAndSet(false, true)) {
              onConnected(true, "Connected to $ssid")
            }
          }

          override fun onUnavailable() {
            if (delivered.compareAndSet(false, true)) {
              onConnected(false, "Could not join $ssid — ensure Receiver Hotspot is active")
            }
          }

          override fun onLost(network: Network) {
            try {
              cm.bindProcessToNetwork(null)
            } catch (_: Throwable) {}
          }
        }
      activeHotspotCallback = cb
      cm.requestNetwork(request, cb, 20_000)
    } catch (e: Throwable) {
      if (delivered.compareAndSet(false, true)) {
        onConnected(false, e.localizedMessage ?: "Failed to join hotspot network")
      }
    }
  }

  fun releaseBoundHotspotNetwork() {
    val cm = connectivityManager ?: return
    try {
      cm.bindProcessToNetwork(null)
    } catch (_: Throwable) {}
    activeHotspotCallback?.let {
      try {
        cm.unregisterNetworkCallback(it)
      } catch (_: Throwable) {}
    }
    activeHotspotCallback = null
  }

  fun registerReceiverService(deviceName: String, port: Int, pin: String) {
    unregisterReceiverService()
    val cleanName = deviceName.replace("|", "").replace(":", "").take(18).ifBlank { "Phone" }
    val serviceName = "$SERVICE_PREFIX|$cleanName|$pin"

    val serviceInfo =
      NsdServiceInfo().apply {
        this.serviceName = serviceName
        this.serviceType = SERVICE_TYPE
        this.port = port
      }

    val listener =
      object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
          registeredServiceName = nsdServiceInfo.serviceName
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
          registeredServiceName = null
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
      }

    registrationListener = listener
    try {
      nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    } catch (_: Throwable) {}
  }

  fun unregisterReceiverService() {
    val listener = registrationListener
    registrationListener = null
    if (listener != null) {
      try {
        nsdManager?.unregisterService(listener)
      } catch (_: Throwable) {}
    }
  }

  fun startNsdDiscovery() {
    stopNsdDiscovery()
    checkReadiness()
    _discoveredDevices.value = emptyList()
    _isScanning.value = true

    val listener =
      object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
          _isScanning.value = true
        }

        override fun onServiceFound(service: NsdServiceInfo) {
          if (!service.serviceType.contains("_ammarshare")) return
          if (service.serviceName == registeredServiceName) return
          resolveService(service)
        }

        override fun onServiceLost(service: NsdServiceInfo) {
          val name = service.serviceName
          _discoveredDevices.value =
            _discoveredDevices.value.filterNot { it.id == "nsd_$name" }
        }

        override fun onDiscoveryStopped(serviceType: String) {
          _isScanning.value = false
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          _isScanning.value = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
          _isScanning.value = false
        }
      }

    discoveryListener = listener
    try {
      nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    } catch (_: Throwable) {
      _isScanning.value = false
    }
  }

  @Suppress("DEPRECATION")
  private fun resolveService(service: NsdServiceInfo) {
    try {
      nsdManager?.resolveService(
        service,
        object : NsdManager.ResolveListener {
          override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

          override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val hostAddress = serviceInfo.host?.hostAddress ?: return
            val parts = serviceInfo.serviceName.split("|")
            val displayName = parts.getOrNull(1)?.ifBlank { "Nearby Phone" } ?: serviceInfo.serviceName
            val pin = parts.getOrNull(2) ?: "0000"
            val device =
              DiscoveredDevice(
                id = "nsd_${serviceInfo.serviceName}",
                name = displayName,
                host = hostAddress,
                port = serviceInfo.port,
                pin = pin,
                source = DiscoverySource.NSD_WIFI,
              )
            upsertDevice(device)
          }
        },
      )
    } catch (_: Throwable) {}
  }

  fun stopNsdDiscovery() {
    val listener = discoveryListener
    discoveryListener = null
    if (listener != null) {
      try {
        nsdManager?.stopServiceDiscovery(listener)
      } catch (_: Throwable) {}
    }
    _isScanning.value = false
  }

  @SuppressLint("MissingPermission")
  fun startWifiDirectDiscovery() {
    val state = checkReadiness()
    if (!state.wifiEnabled) {
      _wifiDirectStatus.value = "Turn ON Wi-Fi first"
      return
    }
    if (!state.locationEnabled) {
      _wifiDirectStatus.value = "Turn ON Location for Wi-Fi Direct"
      return
    }
    val (manager, channel) =
      ensureP2pChannel() ?: run {
        _wifiDirectStatus.value = "Wi-Fi Direct unavailable on this device"
        return
      }

    try {
      manager.discoverPeers(
        channel,
        object : WifiP2pManager.ActionListener {
          override fun onSuccess() {
            _wifiDirectStatus.value = "Scanning Wi-Fi Direct peers..."
          }

          override fun onFailure(reason: Int) {
            _wifiDirectStatus.value =
              when (reason) {
                WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct unsupported"
                WifiP2pManager.BUSY -> "Wi-Fi Direct busy — retry"
                else -> "Ensure Wi-Fi & Location are ON (Code $reason)"
              }
          }
        },
      )
    } catch (_: SecurityException) {
      _wifiDirectStatus.value = "Nearby Wi-Fi / Location permission required"
    } catch (_: Throwable) {
      _wifiDirectStatus.value = "Wi-Fi Direct unavailable"
    }
  }

  @SuppressLint("MissingPermission")
  private fun registerP2pReceiver(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
    if (p2pReceiver != null) return
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
          when (intent.action) {
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
              try {
                manager.requestPeers(channel) { peerList ->
                  val peers = peerList.deviceList
                  if (peers.isNotEmpty()) {
                    _wifiDirectStatus.value = "Found ${peers.size} Wi-Fi Direct peer(s)"
                    for (peer in peers) {
                      upsertDevice(
                        DiscoveredDevice(
                          id = "p2p_${peer.deviceAddress}",
                          name = peer.deviceName.ifBlank { "Wi-Fi Direct Device" },
                          host = peer.deviceAddress,
                          port = TransferEngine.DEFAULT_PORT,
                          pin = "",
                          source = DiscoverySource.WIFI_DIRECT,
                        )
                      )
                    }
                  }
                }
              } catch (_: Throwable) {}
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
              try {
                manager.requestConnectionInfo(channel) { info ->
                  val ownerHost = info.groupOwnerAddress?.hostAddress
                  if (info.groupFormed && !info.isGroupOwner && !ownerHost.isNullOrBlank()) {
                    _wifiDirectStatus.value = "Wi-Fi Direct Connected ($ownerHost)"
                    _connectedP2pHost.value = ownerHost
                    upsertDevice(
                      DiscoveredDevice(
                        id = "p2p_go_$ownerHost",
                        name = "Wi-Fi Direct Receiver",
                        host = ownerHost,
                        port = TransferEngine.DEFAULT_PORT,
                        pin = "",
                        source = DiscoverySource.WIFI_DIRECT,
                      )
                    )
                  } else if (info.groupFormed && info.isGroupOwner && !ownerHost.isNullOrBlank()) {
                    val currentHotspot = _hotspotInfo.value
                    if (currentHotspot.active && currentHotspot.hostIp.isBlank()) {
                      _hotspotInfo.value = currentHotspot.copy(hostIp = ownerHost)
                    }
                  } else if (!info.groupFormed) {
                    _connectedP2pHost.value = null
                  }
                }
              } catch (_: Throwable) {}
            }
          }
        }
      }
    p2pReceiver = receiver
    val filter =
      IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
      }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
      }
    } catch (_: Throwable) {}
  }

  @SuppressLint("MissingPermission")
  fun connectWifiDirectPeer(deviceAddress: String, onResult: (Boolean, String) -> Unit) {
    val pair = ensureP2pChannel()
    if (pair == null) {
      onResult(false, "Wi-Fi Direct not supported")
      return
    }
    val (manager, channel) = pair
    val config = WifiP2pConfig().apply { this.deviceAddress = deviceAddress }
    try {
      manager.connect(
        channel,
        config,
        object : WifiP2pManager.ActionListener {
          override fun onSuccess() {
            onResult(true, "Connecting via Wi-Fi Direct... Tap receiver once connected")
          }

          override fun onFailure(reason: Int) {
            onResult(false, "Wi-Fi Direct connect failed ($reason)")
          }
        },
      )
    } catch (_: SecurityException) {
      onResult(false, "Nearby Wi-Fi permission required")
    } catch (t: Throwable) {
      onResult(false, t.localizedMessage ?: "Wi-Fi Direct error")
    }
  }

  fun stopWifiDirect() {
    p2pReceiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (_: Throwable) {}
    }
    p2pReceiver = null
    _connectedP2pHost.value = null
  }

  private fun upsertDevice(device: DiscoveredDevice) {
    val current = _discoveredDevices.value.toMutableList()
    val idx =
      current.indexOfFirst {
        it.id == device.id || (it.host == device.host && it.port == device.port)
      }
    if (idx >= 0) {
      current[idx] = device
    } else {
      current.add(device)
    }
    _discoveredDevices.value = current
  }

  fun getLocalWifiIpAddress(preferHotspot: Boolean = false): String {
    return try {
      val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
      val candidates = mutableListOf<Pair<Int, String>>()
      for (ni in interfaces) {
        if (!ni.isUp || ni.isLoopback) continue
        val priority =
          if (preferHotspot) {
            when {
              ni.name.startsWith("p2p") -> 1
              ni.name.startsWith("ap") || ni.name.startsWith("swlan") -> 1
              ni.name.startsWith("wlan") -> 2
              else -> 4
            }
          } else {
            when {
              ni.name.startsWith("wlan") -> 1
              ni.name.startsWith("p2p") -> 2
              ni.name.startsWith("ap") || ni.name.startsWith("swlan") -> 2
              else -> 4
            }
          }
        for (addr in ni.inetAddresses.toList()) {
          if (addr is Inet4Address && !addr.isLoopbackAddress) {
            val host = addr.hostAddress ?: continue
            if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
              candidates.add(priority to host)
            }
          }
        }
      }
      candidates.minByOrNull { it.first }?.second ?: "127.0.0.1"
    } catch (_: Throwable) {
      "127.0.0.1"
    }
  }
}
