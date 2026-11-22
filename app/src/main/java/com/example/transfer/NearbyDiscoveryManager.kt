package com.example.transfer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import com.example.model.DiscoveredDevice
import com.example.model.DiscoverySource
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyDiscoveryManager(private val context: Context) {

  companion object {
    const val SERVICE_TYPE = "_ammarshare._tcp."
    private const val SERVICE_PREFIX = "AmmarShare"
  }

  private val nsdManager: NsdManager? =
    context.getSystemService(Context.NSD_SERVICE) as? NsdManager

  private val wifiP2pManager: WifiP2pManager? =
    context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _wifiDirectStatus = MutableStateFlow("Ready")
  val wifiDirectStatus: StateFlow<String> = _wifiDirectStatus.asStateFlow()

  private var registrationListener: NsdManager.RegistrationListener? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null
  private var p2pChannel: WifiP2pManager.Channel? = null
  private var p2pReceiver: BroadcastReceiver? = null
  private var registeredServiceName: String? = null

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
    } catch (_: Exception) {}
  }

  fun unregisterReceiverService() {
    val listener = registrationListener
    registrationListener = null
    if (listener != null) {
      try {
        nsdManager?.unregisterService(listener)
      } catch (_: Exception) {}
    }
  }

  fun startNsdDiscovery() {
    stopNsdDiscovery()
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
    } catch (_: Exception) {
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
    } catch (_: Exception) {}
  }

  fun stopNsdDiscovery() {
    val listener = discoveryListener
    discoveryListener = null
    if (listener != null) {
      try {
        nsdManager?.stopServiceDiscovery(listener)
      } catch (_: Exception) {}
    }
    _isScanning.value = false
  }

  @SuppressLint("MissingPermission")
  fun startWifiDirectDiscovery() {
    val manager = wifiP2pManager ?: run {
      _wifiDirectStatus.value = "Wi-Fi Direct unavailable on this device"
      return
    }
    if (p2pChannel == null) {
      p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
    }
    val channel = p2pChannel ?: return

    registerP2pReceiver(manager, channel)
    try {
      manager.discoverPeers(
        channel,
        object : WifiP2pManager.ActionListener {
          override fun onSuccess() {
            _wifiDirectStatus.value = "Scanning Wi-Fi Direct peers..."
          }

          override fun onFailure(reason: Int) {
            _wifiDirectStatus.value = "Wi-Fi Direct ready (Code $reason)"
          }
        },
      )
    } catch (e: SecurityException) {
      _wifiDirectStatus.value = "Nearby Wi-Fi permission required for Wi-Fi Direct"
    } catch (_: Exception) {
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
              } catch (_: SecurityException) {}
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
              manager.requestConnectionInfo(channel) { info ->
                val ownerHost = info.groupOwnerAddress?.hostAddress
                if (info.groupFormed && !info.isGroupOwner && !ownerHost.isNullOrBlank()) {
                  upsertDevice(
                    DiscoveredDevice(
                      id = "p2p_go_$ownerHost",
                      name = "Wi-Fi Direct Group Owner",
                      host = ownerHost,
                      port = TransferEngine.DEFAULT_PORT,
                      pin = "",
                      source = DiscoverySource.WIFI_DIRECT,
                    )
                  )
                }
              }
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("UnspecifiedRegisterReceiverFlag")
      context.registerReceiver(receiver, filter)
    }
  }

  @SuppressLint("MissingPermission")
  fun connectWifiDirectPeer(deviceAddress: String, onResult: (Boolean, String) -> Unit) {
    val manager = wifiP2pManager ?: return onResult(false, "Wi-Fi Direct not supported")
    val channel = p2pChannel ?: return onResult(false, "Wi-Fi Direct not initialized")
    val config = WifiP2pConfig().apply { this.deviceAddress = deviceAddress }
    try {
      manager.connect(
        channel,
        config,
        object : WifiP2pManager.ActionListener {
          override fun onSuccess() {
            onResult(true, "Wi-Fi Direct invitation sent")
          }

          override fun onFailure(reason: Int) {
            onResult(false, "Wi-Fi Direct connect failed ($reason)")
          }
        },
      )
    } catch (_: SecurityException) {
      onResult(false, "Nearby Wi-Fi permission required")
    }
  }

  fun stopWifiDirect() {
    p2pReceiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (_: Exception) {}
    }
    p2pReceiver = null
  }

  private fun upsertDevice(device: DiscoveredDevice) {
    val current = _discoveredDevices.value.toMutableList()
    val idx = current.indexOfFirst { it.id == device.id || (it.host == device.host && it.port == device.port) }
    if (idx >= 0) {
      current[idx] = device
    } else {
      current.add(device)
    }
    _discoveredDevices.value = current
  }

  fun getLocalWifiIpAddress(): String {
    return try {
      val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
      val candidates = mutableListOf<Pair<Int, String>>()
      for (ni in interfaces) {
        if (!ni.isUp || ni.isLoopback) continue
        val priority =
          when {
            ni.name.startsWith("wlan") -> 1
            ni.name.startsWith("ap") || ni.name.startsWith("swlan") -> 2
            ni.name.startsWith("p2p") -> 3
            else -> 4
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
    } catch (_: Exception) {
      "127.0.0.1"
    }
  }
}
