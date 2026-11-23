package com.example.transfer

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import com.example.data.IncomingFileTarget
import com.example.data.ShareRepository
import com.example.model.BenchmarkResult
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferProgressState
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class TransferEngine(
  private val context: Context,
  private val repository: ShareRepository,
) {

  companion object {
    const val DEFAULT_PORT = 48255
    private const val MAGIC_CONTROL = 0x414D4D52 // "AMMR"
    private const val MAGIC_WORKER = 0x414D4D57  // "AMMW"
    private const val PROTOCOL_VERSION = 2
    private const val CHUNK_SIZE = 2 * 1024 * 1024 // 2 MB zero-GC worker block
    private const val STRIPE_SIZE = 4L * 1024L * 1024L // 4 MB parallel byte-range stripes
    private const val SOCKET_BUFFER_SIZE = 16 * 1024 * 1024 // 16 MB TCP window
    private const val TURBO_STREAMS = 8
    private const val IPTOS_HIGH_THROUGHPUT = 0x10
  }

  private val scopeExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
      if (!cancelRequested.get() && _progress.value.status == TransferStatus.TRANSFERRING) {
        _progress.value =
          _progress.value.copy(
            status = TransferStatus.FAILED,
            errorMessage = throwable.localizedMessage ?: "Transfer interrupted",
          )
      }
    }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + scopeExceptionHandler)

  private val _progress = MutableStateFlow(TransferProgressState())
  val progress: StateFlow<TransferProgressState> = _progress.asStateFlow()

  private var serverSocket: ServerSocket? = null
  private var receiverJob: Job? = null
  private var senderJob: Job? = null
  private val cancelRequested = AtomicBoolean(false)

  private val lockRefCount = AtomicInteger(0)
  private val lockGuard = Any()
  private var wifiLock: WifiManager.WifiLock? = null
  private var wakeLock: PowerManager.WakeLock? = null

  private class ReceiveFileSlot(
    val fileId: Int,
    val relativePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val bytesRemaining: AtomicLong = AtomicLong(fileSize),
    val completed: AtomicBoolean = AtomicBoolean(false),
    @Volatile var startNs: Long = 0L,
    private val openLock: Any = Any(),
    @Volatile var target: IncomingFileTarget? = null,
  ) {
    fun getOrOpen(repository: ShareRepository): IncomingFileTarget {
      target?.let { return it }
      synchronized(openLock) {
        target?.let { return it }
        startNs = System.nanoTime()
        val created =
          repository.createIncomingRandomAccessTarget(relativePath, mimeType, fileSize)
        target = created
        return created
      }
    }

    fun closeQuietly() {
      try {
        target?.close()
      } catch (_: Throwable) {}
    }
  }

  private class ReceiveSession(
    val sessionId: String,
    val peerName: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val expectedWorkers: Int,
    val startNs: Long,
    val files: Array<ReceiveFileSlot>,
    val bytesReceived: AtomicLong = AtomicLong(0L),
    val filesCompleted: AtomicInteger = AtomicInteger(0),
    val activeWorkers: AtomicInteger = AtomicInteger(0),
    val finishedWorkers: AtomicInteger = AtomicInteger(0),
    val allWorkersFinished: CompletableDeferred<Unit> = CompletableDeferred(),
    val workerError: AtomicReference<Throwable?> = AtomicReference(null),
    @Volatile var currentFileName: String = "",
    val records: MutableList<TransferRecord> =
      java.util.Collections.synchronizedList(mutableListOf()),
  ) {
    fun closeAllOpenTargets() {
      for (slot in files) {
        if (!slot.completed.get()) {
          slot.closeQuietly()
        }
      }
    }
  }

  private data class PreparedSource(
    val fileId: Int,
    val item: ShareableItem,
    val actualSize: Long,
    val startOffset: Long,
    val seekable: Boolean,
    val remainingBytes: AtomicLong = AtomicLong(actualSize),
    val completed: AtomicBoolean = AtomicBoolean(false),
    val startNs: AtomicLong = AtomicLong(0L),
  )

  private data class StripeTask(
    val fileId: Int,
    val offset: Long,
    val length: Long,
    val seekable: Boolean,
  )

  private val sessions = ConcurrentHashMap<String, ReceiveSession>()

  private fun acquireTransferLocks() {
    synchronized(lockGuard) {
      if (lockRefCount.getAndIncrement() > 0) return
      try {
        val wm =
          context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm != null) {
          val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
              @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
          wifiLock =
            try {
              wm.createWifiLock(mode, "FastShare:TurboWifiLock").apply {
                setReferenceCounted(false)
                acquire()
              }
            } catch (_: Throwable) {
              @Suppress("DEPRECATION")
              wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FastShare:TurboWifiLock")
                .apply {
                  setReferenceCounted(false)
                  acquire()
                }
            }
        }
      } catch (_: Throwable) {}

      try {
        val pm =
          context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null) {
          wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FastShare:TurboWakeLock").apply {
              setReferenceCounted(false)
              acquire(15 * 60 * 1000L)
            }
        }
      } catch (_: Throwable) {}
    }
  }

  private fun releaseTransferLocks() {
    synchronized(lockGuard) {
      val left = lockRefCount.decrementAndGet()
      if (left > 0) return
      lockRefCount.set(0)
      try {
        if (wifiLock?.isHeld == true) wifiLock?.release()
      } catch (_: Throwable) {}
      wifiLock = null
      try {
        if (wakeLock?.isHeld == true) wakeLock?.release()
      } catch (_: Throwable) {}
      wakeLock = null
    }
  }

  fun startReceiver(
    expectedPin: String,
    onBound: (actualPort: Int) -> Unit,
    onHistoryUpdated: () -> Unit,
  ) {
    stopReceiver()
    cancelRequested.set(false)
    acquireTransferLocks()

    receiverJob =
      scope.launch {
        val srv =
          try {
            ServerSocket().apply {
              reuseAddress = true
              receiveBufferSize = SOCKET_BUFFER_SIZE
              setPerformancePreferences(0, 1, 2)
              bind(InetSocketAddress(DEFAULT_PORT))
            }
          } catch (_: Exception) {
            ServerSocket().apply {
              reuseAddress = true
              receiveBufferSize = SOCKET_BUFFER_SIZE
              setPerformancePreferences(0, 1, 2)
              bind(InetSocketAddress(0))
            }
          }
        val boundPort = srv.localPort
        serverSocket = srv
        onBound(boundPort)

        TransferForegroundService.start(
          context,
          "Fast Share Ready",
          "8x Hyper-Turbo on port $boundPort • PIN $expectedPin",
        )

        _progress.value =
          TransferProgressState(
            direction = TransferDirection.RECEIVE,
            status = TransferStatus.LISTENING,
          )

        try {
          while (isActive && !srv.isClosed) {
            val client = srv.accept()
            launch { handleIncomingSocket(client, expectedPin, onHistoryUpdated) }
          }
        } catch (_: Exception) {
          // Server closed normally
        }
      }
  }

  private suspend fun handleIncomingSocket(
    socket: Socket,
    expectedPin: String,
    onHistoryUpdated: () -> Unit,
  ) {
    withContext(Dispatchers.IO) {
      try {
        socket.tcpNoDelay = true
        socket.receiveBufferSize = SOCKET_BUFFER_SIZE
        socket.sendBufferSize = SOCKET_BUFFER_SIZE
        try {
          socket.trafficClass = IPTOS_HIGH_THROUGHPUT
        } catch (_: Throwable) {}
        socket.setPerformancePreferences(0, 1, 2)

        val input = DataInputStream(BufferedInputStream(socket.getInputStream(), CHUNK_SIZE))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 8192))

        val magic = input.readInt()
        if (magic == MAGIC_CONTROL) {
          val version = input.readInt()
          val pin = input.readUTF()
          val senderName = input.readUTF()
          val sessionId = input.readUTF()
          val totalFiles = input.readInt()
          val totalBytes = input.readLong()
          val streamCount = input.readInt().coerceIn(1, TURBO_STREAMS)

          if (
            version != PROTOCOL_VERSION ||
              (expectedPin.isNotBlank() && pin.isNotBlank() && pin != expectedPin) ||
              totalFiles < 0
          ) {
            output.writeBoolean(false)
            output.writeUTF("PIN mismatch or incompatible protocol v$version")
            output.flush()
            socket.close()
            return@withContext
          }

          val slots =
            Array(totalFiles) { idx ->
              val relPath = input.readUTF()
              val mimeType = input.readUTF()
              val fileSize = input.readLong().coerceAtLeast(0L)
              ReceiveFileSlot(
                fileId = idx,
                relativePath = relPath,
                fileName = relPath.substringAfterLast('/'),
                mimeType = mimeType,
                fileSize = fileSize,
              )
            }

          val session =
            ReceiveSession(
              sessionId = sessionId,
              peerName = senderName,
              totalFiles = totalFiles,
              totalBytes = totalBytes,
              expectedWorkers = streamCount,
              startNs = System.nanoTime(),
              files = slots,
              currentFileName = slots.firstOrNull()?.fileName.orEmpty(),
            )
          sessions[sessionId] = session

          output.writeBoolean(true)
          output.writeUTF("OK")
          output.flush()

          _progress.value =
            TransferProgressState(
              direction = TransferDirection.RECEIVE,
              status = TransferStatus.TRANSFERRING,
              peerName = senderName,
              currentFileName = session.currentFileName,
              totalFiles = totalFiles,
              totalBytes = totalBytes,
              activeStreams = streamCount,
            )

          val monitorJob =
            scope.launch {
              var lastBytes = 0L
              var lastNs = System.nanoTime()
              var peakSpeed = 0.0
              while (
                isActive &&
                  session.filesCompleted.get() < session.totalFiles &&
                  !cancelRequested.get()
              ) {
                delay(120L)
                val nowNs = System.nanoTime()
                val curBytes = session.bytesReceived.get()
                val deltaBytes = (curBytes - lastBytes).coerceAtLeast(0L)
                val deltaSec = ((nowNs - lastNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                val instantMBps = (deltaBytes / (1024.0 * 1024.0)) / deltaSec
                if (instantMBps > peakSpeed) peakSpeed = instantMBps
                lastBytes = curBytes
                lastNs = nowNs

                val elapsedMs = (nowNs - session.startNs) / 1_000_000L
                val current = _progress.value
                if (current.status == TransferStatus.TRANSFERRING) {
                  _progress.value =
                    current.copy(
                      currentFileName = session.currentFileName,
                      bytesTransferred = curBytes,
                      currentFileIndex = session.filesCompleted.get(),
                      speedMBps = instantMBps,
                      peakSpeedMBps = peakSpeed,
                      elapsedMs = elapsedMs,
                      activeStreams = session.activeWorkers.get().coerceAtLeast(streamCount),
                    )
                }
              }
            }

          val doneSignal =
            try {
              val signal = input.readBoolean()
              if (signal) {
                withTimeoutOrNull(30_000L) { session.allWorkersFinished.await() }
              }
              signal && session.workerError.get() == null
            } catch (_: Exception) {
              false
            } finally {
              monitorJob.cancel()
              session.closeAllOpenTargets()
              sessions.remove(sessionId)
            }

          val totalElapsedMs = ((System.nanoTime() - session.startNs) / 1_000_000L).coerceAtLeast(1L)
          val avgSpeed =
            (session.bytesReceived.get() / (1024.0 * 1024.0)) / (totalElapsedMs / 1000.0)

          if (doneSignal && session.records.isNotEmpty()) {
            repository.appendHistoryRecords(session.records.toList())
            onHistoryUpdated()
          }

          val err = session.workerError.get()
          _progress.value =
            _progress.value.copy(
              status =
                if (doneSignal && !cancelRequested.get()) TransferStatus.COMPLETED
                else TransferStatus.FAILED,
              bytesTransferred = session.bytesReceived.get(),
              currentFileIndex = session.filesCompleted.get(),
              speedMBps = avgSpeed,
              peakSpeedMBps = maxOf(_progress.value.peakSpeedMBps, avgSpeed),
              elapsedMs = totalElapsedMs,
              errorMessage = err?.localizedMessage ?: _progress.value.errorMessage,
            )

          try {
            output.writeBoolean(doneSignal)
            output.flush()
          } catch (_: Exception) {}
        } else if (magic == MAGIC_WORKER) {
          val sessionId = input.readUTF()
          val session = sessions[sessionId]
          if (session == null) {
            socket.close()
            return@withContext
          }
          session.activeWorkers.incrementAndGet()
          try {
            receiveWorkerStripes(input, session)
          } catch (t: Throwable) {
            session.workerError.compareAndSet(null, t)
            throw t
          } finally {
            session.activeWorkers.decrementAndGet()
            if (session.finishedWorkers.incrementAndGet() >= session.expectedWorkers) {
              session.allWorkersFinished.complete(Unit)
            }
          }
        }
      } catch (e: Exception) {
        if (!cancelRequested.get() && _progress.value.status == TransferStatus.TRANSFERRING) {
          _progress.value =
            _progress.value.copy(
              status = TransferStatus.FAILED,
              errorMessage = e.localizedMessage ?: "Transfer interrupted",
            )
        }
      } finally {
        try {
          socket.close()
        } catch (_: Exception) {}
      }
    }
  }

  private fun receiveWorkerStripes(input: DataInputStream, session: ReceiveSession) {
    // Allocate a single 2 MB buffer per worker — zero per-chunk heap churn
    val reusableBuf = ByteArray(CHUNK_SIZE)
    val writeByteBuf = ByteBuffer.wrap(reusableBuf)

    while (!cancelRequested.get()) {
      val hasStripe = input.readBoolean()
      if (!hasStripe) break

      val fileId = input.readInt()
      val offset = input.readLong()
      val length = input.readLong()
      if (fileId !in session.files.indices) {
        throw IOException("Invalid stripe fileId $fileId")
      }
      val slot = session.files[fileId]
      session.currentFileName = slot.fileName

      val target = slot.getOrOpen(repository)

      var currentPos = offset
      var remaining = length
      while (remaining > 0L && !cancelRequested.get()) {
        val toRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
        input.readFully(reusableBuf, 0, toRead)
        writeByteBuf.clear()
        writeByteBuf.limit(toRead)
        target.writeAt(writeByteBuf, currentPos)
        currentPos += toRead
        remaining -= toRead
        session.bytesReceived.addAndGet(toRead.toLong())
      }

      if (!cancelRequested.get() && slot.bytesRemaining.addAndGet(-length) == 0L) {
        if (slot.completed.compareAndSet(false, true)) {
          target.close()
          repository.finalizeSavedLocation(target.savedLocation)
          val fileSec = ((System.nanoTime() - slot.startNs) / 1_000_000_000.0).coerceAtLeast(0.001)
          val fileSpeed = (slot.fileSize / (1024.0 * 1024.0)) / fileSec
          session.filesCompleted.incrementAndGet()

          session.records.add(
            TransferRecord(
              id = UUID.randomUUID().toString(),
              fileName = slot.fileName,
              relativePath = slot.relativePath,
              sizeBytes = slot.fileSize,
              direction = TransferDirection.RECEIVE,
              category = ShareRepository.inferCategory(slot.fileName, slot.mimeType, slot.relativePath),
              timestampMs = System.currentTimeMillis(),
              speedMBps = fileSpeed,
              peerName = session.peerName,
              savedUriString = target.savedLocation.uri?.toString(),
              savedFilePath = target.savedLocation.filePath,
              mimeType = slot.mimeType,
            )
          )
        }
      }
    }
  }

  fun stopReceiver() {
    cancelRequested.set(true)
    receiverJob?.cancel()
    receiverJob = null
    try {
      serverSocket?.close()
    } catch (_: Exception) {}
    serverSocket = null
    sessions.values.forEach { it.closeAllOpenTargets() }
    sessions.clear()
    releaseTransferLocks()
    TransferForegroundService.stop(context)
    if (_progress.value.status == TransferStatus.LISTENING) {
      _progress.value = TransferProgressState()
    }
  }

  fun sendFiles(
    items: List<ShareableItem>,
    host: String,
    port: Int,
    pin: String,
    senderDeviceName: String,
    peerDeviceName: String,
    turboMode: Boolean,
    onHistoryUpdated: () -> Unit,
  ) {
    if (items.isEmpty()) return
    senderJob?.cancel()
    cancelRequested.set(false)
    acquireTransferLocks()

    val estimatedTotalBytes = items.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    TransferForegroundService.start(
      context,
      "Sending ${items.size} item(s)",
      "8x Hyper-Turbo -> $peerDeviceName ($host)",
    )

    senderJob =
      scope.launch {
        _progress.value =
          TransferProgressState(
            direction = TransferDirection.SEND,
            status = TransferStatus.CONNECTING,
            peerName = peerDeviceName,
            totalFiles = items.size,
            totalBytes = estimatedTotalBytes,
            activeStreams = if (turboMode) TURBO_STREAMS else 1,
          )

        var controlSocket: Socket? = null
        try {
          val preparedSources = prepareSources(items)
          val tasks = buildStripeTasks(preparedSources, turboMode)
          val totalBytes = preparedSources.sumOf { it.actualSize }
          val streamCount =
            if (turboMode) minOf(TURBO_STREAMS, tasks.size.coerceAtLeast(1)) else 1

          controlSocket =
            Socket().apply {
              tcpNoDelay = true
              sendBufferSize = SOCKET_BUFFER_SIZE
              receiveBufferSize = SOCKET_BUFFER_SIZE
              try {
                trafficClass = IPTOS_HIGH_THROUGHPUT
              } catch (_: Throwable) {}
              setPerformancePreferences(0, 1, 2)
              connect(InetSocketAddress(host, port), 10_000)
            }

          val out = DataOutputStream(BufferedOutputStream(controlSocket.getOutputStream(), 65536))
          val inp = DataInputStream(BufferedInputStream(controlSocket.getInputStream(), 8192))

          val sessionId = UUID.randomUUID().toString()
          out.writeInt(MAGIC_CONTROL)
          out.writeInt(PROTOCOL_VERSION)
          out.writeUTF(pin)
          out.writeUTF(senderDeviceName)
          out.writeUTF(sessionId)
          out.writeInt(preparedSources.size)
          out.writeLong(totalBytes)
          out.writeInt(streamCount)

          // Transmit file manifest upfront so Receiver can pre-register slots for parallel pwrite
          for (src in preparedSources) {
            out.writeUTF(src.item.relativePath)
            out.writeUTF(src.item.mimeType)
            out.writeLong(src.actualSize)
          }
          out.flush()

          val accepted = inp.readBoolean()
          val msg = inp.readUTF()
          if (!accepted) {
            throw IllegalStateException("Rejected by receiver: $msg")
          }

          val startNs = System.nanoTime()
          val bytesSent = AtomicLong(0L)
          val filesDone = AtomicInteger(0)
          val sentRecords = java.util.Collections.synchronizedList(mutableListOf<TransferRecord>())
          var currentFileName = preparedSources.firstOrNull()?.item?.displayName.orEmpty()

          _progress.value =
            _progress.value.copy(
              status = TransferStatus.TRANSFERRING,
              totalBytes = totalBytes,
              currentFileName = currentFileName,
              activeStreams = streamCount,
            )

          val monitorJob =
            launch {
              var lastBytes = 0L
              var lastNs = System.nanoTime()
              var peakSpeed = 0.0
              while (isActive && !cancelRequested.get() && filesDone.get() < preparedSources.size) {
                delay(120L)
                val nowNs = System.nanoTime()
                val cur = bytesSent.get()
                val delta = (cur - lastBytes).coerceAtLeast(0L)
                val sec = ((nowNs - lastNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                val instantMBps = (delta / (1024.0 * 1024.0)) / sec
                if (instantMBps > peakSpeed) peakSpeed = instantMBps
                lastBytes = cur
                lastNs = nowNs

                val elapsedMs = (nowNs - startNs) / 1_000_000L
                _progress.value =
                  _progress.value.copy(
                    currentFileName = currentFileName,
                    bytesTransferred = cur,
                    currentFileIndex = filesDone.get(),
                    speedMBps = instantMBps,
                    peakSpeedMBps = peakSpeed,
                    elapsedMs = elapsedMs,
                  )
              }
            }

          val taskChannel = Channel<StripeTask>(Channel.UNLIMITED)
          for (task in tasks) {
            taskChannel.send(task)
          }
          taskChannel.close()

          val workers =
            (0 until streamCount).map {
              async(Dispatchers.IO) {
                sendWorkerLoop(
                  host = host,
                  port = port,
                  sessionId = sessionId,
                  sources = preparedSources,
                  channel = taskChannel,
                  bytesSent = bytesSent,
                  filesDone = filesDone,
                  sentRecords = sentRecords,
                  peerName = peerDeviceName,
                  onActiveFileChanged = { name -> currentFileName = name },
                )
              }
            }
          workers.awaitAll()
          monitorJob.cancel()

          val totalElapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
          val avgSpeed = (bytesSent.get() / (1024.0 * 1024.0)) / (totalElapsedMs / 1000.0)

          out.writeBoolean(!cancelRequested.get())
          out.flush()
          try {
            inp.readBoolean()
          } catch (_: Exception) {}

          if (sentRecords.isNotEmpty()) {
            repository.appendHistoryRecords(sentRecords.toList())
            onHistoryUpdated()
          }

          _progress.value =
            _progress.value.copy(
              status =
                if (cancelRequested.get()) TransferStatus.CANCELLED else TransferStatus.COMPLETED,
              bytesTransferred = bytesSent.get(),
              currentFileIndex = filesDone.get(),
              speedMBps = avgSpeed,
              peakSpeedMBps = maxOf(_progress.value.peakSpeedMBps, avgSpeed),
              elapsedMs = totalElapsedMs,
            )
        } catch (e: Exception) {
          _progress.value =
            _progress.value.copy(
              status = if (cancelRequested.get()) TransferStatus.CANCELLED else TransferStatus.FAILED,
              errorMessage = e.localizedMessage ?: "Connection failed",
            )
        } finally {
          try {
            controlSocket?.close()
          } catch (_: Exception) {}
          releaseTransferLocks()
          TransferForegroundService.stop(context)
        }
      }
  }

  private fun prepareSources(items: List<ShareableItem>): Array<PreparedSource> {
    return Array(items.size) { index ->
      val item = items[index]
      if (!item.localFilePath.isNullOrBlank()) {
        val file = File(item.localFilePath)
        if (!file.exists() || !file.canRead()) {
          throw IllegalStateException("Cannot read ${item.displayName}")
        }
        PreparedSource(
          fileId = index,
          item = item,
          actualSize = file.length(),
          startOffset = 0L,
          seekable = true,
        )
      } else {
        val uri =
          item.uri ?: throw IllegalArgumentException("No URI or path for ${item.displayName}")
        var resolvedSize = item.sizeBytes
        var startOffset = 0L
        var seekable = false

        try {
          context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            if (afd.length >= 0L) {
              resolvedSize = afd.length
              startOffset = afd.startOffset.coerceAtLeast(0L)
              val ch = FileInputStream(afd.fileDescriptor).channel
              // Verify underlying FD supports positional pread (not a FIFO/socket pipe)
              val probeBuf = ByteBuffer.allocate(1)
              try {
                if (resolvedSize == 0L || ch.read(probeBuf, startOffset) >= 0) {
                  seekable = true
                }
              } catch (_: Throwable) {
                seekable = false
              } finally {
                ch.close()
              }
            }
          }
        } catch (_: Throwable) {}

        if (resolvedSize <= 0L) {
          resolvedSize = probeStreamLength(uri)
        }

        PreparedSource(
          fileId = index,
          item = item,
          actualSize = resolvedSize.coerceAtLeast(0L),
          startOffset = startOffset,
          seekable = seekable,
        )
      }
    }
  }

  private fun probeStreamLength(uri: Uri): Long {
    var counted = 0L
    try {
      context.contentResolver.openInputStream(uri)?.use { probe ->
        val skipBuf = ByteArray(64 * 1024)
        while (true) {
          val n = probe.read(skipBuf)
          if (n <= 0) break
          counted += n
        }
      }
    } catch (_: Throwable) {}
    return counted
  }

  private fun buildStripeTasks(
    sources: Array<PreparedSource>,
    turboMode: Boolean,
  ): List<StripeTask> {
    val tasks = ArrayList<StripeTask>(sources.size * 4)
    for (src in sources) {
      val size = src.actualSize
      if (!turboMode || !src.seekable || size <= STRIPE_SIZE) {
        tasks.add(
          StripeTask(
            fileId = src.fileId,
            offset = 0L,
            length = size,
            seekable = src.seekable,
          )
        )
      } else {
        var offset = 0L
        while (offset < size) {
          val len = minOf(STRIPE_SIZE, size - offset)
          tasks.add(
            StripeTask(
              fileId = src.fileId,
              offset = offset,
              length = len,
              seekable = true,
            )
          )
          offset += len
        }
      }
    }
    return tasks
  }

  private suspend fun sendWorkerLoop(
    host: String,
    port: Int,
    sessionId: String,
    sources: Array<PreparedSource>,
    channel: Channel<StripeTask>,
    bytesSent: AtomicLong,
    filesDone: AtomicInteger,
    sentRecords: MutableList<TransferRecord>,
    peerName: String,
    onActiveFileChanged: (String) -> Unit,
  ) = coroutineScope {
    val workerSocket =
      Socket().apply {
        tcpNoDelay = true
        sendBufferSize = SOCKET_BUFFER_SIZE
        receiveBufferSize = SOCKET_BUFFER_SIZE
        try {
          trafficClass = IPTOS_HIGH_THROUGHPUT
        } catch (_: Throwable) {}
        setPerformancePreferences(0, 1, 2)
        connect(InetSocketAddress(host, port), 10_000)
      }

    // One reusable 2 MB heap buffer per worker — zero per-chunk GC pauses
    val reusableBuf = ByteArray(CHUNK_SIZE)
    val readByteBuf = ByteBuffer.wrap(reusableBuf)

    var cachedFileId = -1
    var cachedChannel: FileChannel? = null
    var cachedCloseables: List<Closeable> = emptyList()

    fun closeCachedSource() {
      for (c in cachedCloseables) {
        try {
          c.close()
        } catch (_: Throwable) {}
      }
      cachedCloseables = emptyList()
      cachedChannel = null
      cachedFileId = -1
    }

    try {
      workerSocket.use { sock ->
        val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), CHUNK_SIZE))
        out.writeInt(MAGIC_WORKER)
        out.writeUTF(sessionId)

        for (task in channel) {
          if (cancelRequested.get()) break
          val src = sources[task.fileId]
          src.startNs.compareAndSet(0L, System.nanoTime())
          onActiveFileChanged(src.item.displayName)

          out.writeBoolean(true)
          out.writeInt(task.fileId)
          out.writeLong(task.offset)
          out.writeLong(task.length)

          if (task.length > 0L) {
            if (task.seekable) {
              if (cachedFileId != task.fileId || cachedChannel == null) {
                closeCachedSource()
                val opened = openSeekableChannel(src.item)
                cachedFileId = task.fileId
                cachedChannel = opened.first
                cachedCloseables = opened.second
              }
              val fileChannel = cachedChannel!!
              var currentOffset = src.startOffset + task.offset
              var remaining = task.length
              while (remaining > 0L && !cancelRequested.get()) {
                val maxRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                readByteBuf.clear()
                readByteBuf.limit(maxRead)
                var totalRead = 0
                while (totalRead < maxRead) {
                  val n = fileChannel.read(readByteBuf, currentOffset + totalRead)
                  if (n <= 0) break
                  totalRead += n
                }
                if (totalRead <= 0) break
                out.write(reusableBuf, 0, totalRead)
                currentOffset += totalRead
                remaining -= totalRead
                bytesSent.addAndGet(totalRead.toLong())
              }
            } else {
              val uri =
                src.item.uri
                  ?: throw IllegalStateException("Missing URI for ${src.item.displayName}")
              val rawIn =
                context.contentResolver.openInputStream(uri)
                  ?: throw IllegalStateException("Cannot open ${src.item.displayName}")
              rawIn.use { stream ->
                var remaining = task.length
                while (remaining > 0L && !cancelRequested.get()) {
                  val maxRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                  val read = stream.read(reusableBuf, 0, maxRead)
                  if (read == -1) break
                  out.write(reusableBuf, 0, read)
                  remaining -= read
                  bytesSent.addAndGet(read.toLong())
                }
              }
            }
          }

          if (!cancelRequested.get() && src.remainingBytes.addAndGet(-task.length) == 0L) {
            if (src.completed.compareAndSet(false, true)) {
              val started = src.startNs.get().takeIf { it > 0L } ?: System.nanoTime()
              val sec = ((System.nanoTime() - started) / 1_000_000_000.0).coerceAtLeast(0.001)
              val fileSpeed = (src.actualSize / (1024.0 * 1024.0)) / sec
              filesDone.incrementAndGet()

              sentRecords.add(
                TransferRecord(
                  id = UUID.randomUUID().toString(),
                  fileName = src.item.displayName,
                  relativePath = src.item.relativePath,
                  sizeBytes = src.actualSize,
                  direction = TransferDirection.SEND,
                  category = src.item.category,
                  timestampMs = System.currentTimeMillis(),
                  speedMBps = fileSpeed,
                  peerName = peerName,
                  mimeType = src.item.mimeType,
                )
              )
            }
          }
        }

        out.writeBoolean(false)
        out.flush()
      }
    } finally {
      closeCachedSource()
    }
  }

  private fun openSeekableChannel(item: ShareableItem): Pair<FileChannel, List<Closeable>> {
    if (!item.localFilePath.isNullOrBlank()) {
      val file = File(item.localFilePath)
      val fis = FileInputStream(file)
      val ch = fis.channel
      return ch to listOf(ch, fis)
    }
    val uri = item.uri ?: throw IllegalArgumentException("No URI or path for ${item.displayName}")
    val afd: AssetFileDescriptor =
      context.contentResolver.openAssetFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Cannot open ${item.displayName}")
    val fis = FileInputStream(afd.fileDescriptor)
    val ch = fis.channel
    return ch to listOf(ch, fis, afd)
  }

  fun cancelActiveTransfer() {
    cancelRequested.set(true)
    senderJob?.cancel()
    releaseTransferLocks()
    TransferForegroundService.stop(context)
    _progress.value = _progress.value.copy(status = TransferStatus.CANCELLED)
  }

  fun resetProgress() {
    _progress.value = TransferProgressState()
  }

  suspend fun runTurboLoopbackBenchmark(
    payloadMb: Int = 128,
    streams: Int = TURBO_STREAMS,
  ): BenchmarkResult =
    withContext(Dispatchers.IO) {
      val totalBytes = payloadMb.toLong() * 1024L * 1024L
      val bytesPerStream = totalBytes / streams
      val remainderBytes = totalBytes - (bytesPerStream * streams)
      val srv =
        ServerSocket().apply {
          reuseAddress = true
          receiveBufferSize = SOCKET_BUFFER_SIZE
          setPerformancePreferences(0, 1, 2)
          bind(InetSocketAddress("127.0.0.1", 0))
        }
      val port = srv.localPort

      val startNs = System.nanoTime()
      val receivedTotal = AtomicLong(0L)
      var peak = 0.0

      val serverJobs =
        (0 until streams).map {
          async(Dispatchers.IO) {
            srv.accept().use { s ->
              s.tcpNoDelay = true
              s.receiveBufferSize = SOCKET_BUFFER_SIZE
              try {
                s.trafficClass = IPTOS_HIGH_THROUGHPUT
              } catch (_: Throwable) {}
              val inp = s.getInputStream()
              val buf = ByteArray(CHUNK_SIZE)
              while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                receivedTotal.addAndGet(n.toLong())
              }
            }
          }
        }

      val clientJobs =
        (0 until streams).map { idx ->
          async(Dispatchers.IO) {
            Socket().use { s ->
              s.tcpNoDelay = true
              s.sendBufferSize = SOCKET_BUFFER_SIZE
              try {
                s.trafficClass = IPTOS_HIGH_THROUGHPUT
              } catch (_: Throwable) {}
              s.setPerformancePreferences(0, 1, 2)
              s.connect(InetSocketAddress("127.0.0.1", port), 3000)
              val out = s.getOutputStream()
              val chunk = ByteArray(CHUNK_SIZE) { 0x5A }
              var left = bytesPerStream + if (idx == 0) remainderBytes else 0L
              while (left > 0L) {
                val toWrite = minOf(chunk.size.toLong(), left).toInt()
                out.write(chunk, 0, toWrite)
                left -= toWrite
              }
              out.flush()
            }
          }
        }

      val sampler =
        launch {
          var prev = 0L
          var prevNs = System.nanoTime()
          while (isActive && receivedTotal.get() < totalBytes) {
            delay(40L)
            val now = System.nanoTime()
            val cur = receivedTotal.get()
            val dt = ((now - prevNs) / 1_000_000_000.0).coerceAtLeast(0.01)
            val mbps = ((cur - prev) / (1024.0 * 1024.0)) / dt
            if (mbps > peak) peak = mbps
            prev = cur
            prevNs = now
          }
        }

      clientJobs.awaitAll()
      serverJobs.awaitAll()
      sampler.cancel()
      srv.close()

      val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
      val avgMBps = (receivedTotal.get() / (1024.0 * 1024.0)) / (durationMs / 1000.0)
      BenchmarkResult(
        throughputMBps = avgMBps,
        peakMBps = maxOf(peak, avgMBps),
        durationMs = durationMs,
        payloadMegabytes = payloadMb,
        streamsUsed = streams,
      )
    }
}
