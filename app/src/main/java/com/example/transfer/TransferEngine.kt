package com.example.transfer

import android.content.Context
import com.example.data.ShareRepository
import com.example.model.BenchmarkResult
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferProgressState
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferEngine(
  private val context: Context,
  private val repository: ShareRepository,
) {

  companion object {
    const val DEFAULT_PORT = 48255
    private const val MAGIC_CONTROL = 0x414D4D52 // "AMMR"
    private const val MAGIC_WORKER = 0x414D4D57  // "AMMW"
    private const val PROTOCOL_VERSION = 1
    private const val CHUNK_SIZE = 2 * 1024 * 1024 // 2 MB high-speed buffer
    private const val SOCKET_BUFFER_SIZE = 8 * 1024 * 1024 // 8 MB TCP window
    private const val TURBO_STREAMS = 6
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _progress = MutableStateFlow(TransferProgressState())
  val progress: StateFlow<TransferProgressState> = _progress.asStateFlow()

  private var serverSocket: ServerSocket? = null
  private var receiverJob: Job? = null
  private var senderJob: Job? = null
  private val cancelRequested = AtomicBoolean(false)

  private data class ReceiveSession(
    val sessionId: String,
    val peerName: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val startNs: Long,
    val bytesReceived: AtomicLong = AtomicLong(0L),
    val filesCompleted: AtomicInteger = AtomicInteger(0),
    val activeWorkers: AtomicInteger = AtomicInteger(0),
    val records: MutableList<TransferRecord> = java.util.Collections.synchronizedList(mutableListOf()),
  )

  private val sessions = ConcurrentHashMap<String, ReceiveSession>()

  fun startReceiver(
    expectedPin: String,
    onBound: (actualPort: Int) -> Unit,
    onHistoryUpdated: () -> Unit,
  ) {
    stopReceiver()
    cancelRequested.set(false)

    receiverJob =
      scope.launch {
        var boundPort = DEFAULT_PORT
        val srv =
          try {
            ServerSocket().apply {
              reuseAddress = true
              receiveBufferSize = SOCKET_BUFFER_SIZE
              bind(InetSocketAddress(DEFAULT_PORT))
            }
          } catch (_: Exception) {
            ServerSocket().apply {
              reuseAddress = true
              receiveBufferSize = SOCKET_BUFFER_SIZE
              bind(InetSocketAddress(0))
            }
          }
        boundPort = srv.localPort
        serverSocket = srv
        onBound(boundPort)

        TransferForegroundService.start(
          context,
          "Ammar Share Ready",
          "Listening on port $boundPort • PIN $expectedPin",
        )

        _progress.value =
          TransferProgressState(
            direction = TransferDirection.RECEIVE,
            status = TransferStatus.LISTENING,
          )

        try {
          while (isActive && !srv.isClosed) {
            val client = srv.accept()
            launch {
              handleIncomingSocket(client, expectedPin, onHistoryUpdated)
            }
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
        val input = DataInputStream(BufferedInputStream(socket.getInputStream(), CHUNK_SIZE))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 4096))

        val magic = input.readInt()
        if (magic == MAGIC_CONTROL) {
          val version = input.readInt()
          val pin = input.readUTF()
          val senderName = input.readUTF()
          val sessionId = input.readUTF()
          val totalFiles = input.readInt()
          val totalBytes = input.readLong()
          val streamCount = input.readInt().coerceIn(1, 8)

          if (version != PROTOCOL_VERSION || (expectedPin.isNotBlank() && pin.isNotBlank() && pin != expectedPin)) {
            output.writeBoolean(false)
            output.writeUTF("PIN mismatch or incompatible version")
            output.flush()
            socket.close()
            return@withContext
          }

          val session =
            ReceiveSession(
              sessionId = sessionId,
              peerName = senderName,
              totalFiles = totalFiles,
              totalBytes = totalBytes,
              startNs = System.nanoTime(),
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
              totalFiles = totalFiles,
              totalBytes = totalBytes,
              activeStreams = streamCount,
            )

          // Monitor live sliding-window speed while session runs
          val monitorJob =
            scope.launch {
              var lastBytes = 0L
              var lastNs = System.nanoTime()
              var peakSpeed = 0.0
              while (isActive && session.filesCompleted.get() < session.totalFiles && !cancelRequested.get()) {
                delay(200L)
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
                      bytesTransferred = curBytes,
                      currentFileIndex = session.filesCompleted.get(),
                      speedMBps = instantMBps,
                      peakSpeedMBps = peakSpeed,
                      elapsedMs = elapsedMs,
                      activeStreams = session.activeWorkers.get().coerceAtLeast(1),
                    )
                }
              }
            }

          // Control socket stays open to confirm completion
          val doneSignal = input.readBoolean()
          monitorJob.cancel()

          val totalElapsedMs = ((System.nanoTime() - session.startNs) / 1_000_000L).coerceAtLeast(1L)
          val avgSpeed =
            (session.bytesReceived.get() / (1024.0 * 1024.0)) / (totalElapsedMs / 1000.0)

          if (doneSignal && session.records.isNotEmpty()) {
            repository.appendHistoryRecords(session.records.toList())
            onHistoryUpdated()
          }

          _progress.value =
            _progress.value.copy(
              status = if (doneSignal) TransferStatus.COMPLETED else TransferStatus.FAILED,
              bytesTransferred = session.bytesReceived.get(),
              currentFileIndex = session.filesCompleted.get(),
              speedMBps = avgSpeed,
              peakSpeedMBps = maxOf(_progress.value.peakSpeedMBps, avgSpeed),
              elapsedMs = totalElapsedMs,
            )

          output.writeBoolean(true)
          output.flush()
          sessions.remove(sessionId)
        } else if (magic == MAGIC_WORKER) {
          val sessionId = input.readUTF()
          val session = sessions[sessionId]
          if (session == null) {
            socket.close()
            return@withContext
          }
          session.activeWorkers.incrementAndGet()
          try {
            receiveWorkerFiles(input, session)
          } finally {
            session.activeWorkers.decrementAndGet()
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

  private fun receiveWorkerFiles(input: DataInputStream, session: ReceiveSession) {
    val buffer = ByteArray(CHUNK_SIZE)
    while (!cancelRequested.get()) {
      val hasNext = input.readBoolean()
      if (!hasNext) break

      val relativePath = input.readUTF()
      val mimeType = input.readUTF()
      val fileSize = input.readLong()
      val fileName = relativePath.substringAfterLast('/')

      _progress.value = _progress.value.copy(currentFileName = fileName)

      val (outStream, savedLoc) = repository.createIncomingOutputStream(relativePath, mimeType)
      var remaining = fileSize
      val fileStartNs = System.nanoTime()

      outStream.use { out ->
        val bufferedOut = BufferedOutputStream(out, CHUNK_SIZE)
        while (remaining > 0L && !cancelRequested.get()) {
          val toRead = minOf(buffer.size.toLong(), remaining).toInt()
          val read = input.read(buffer, 0, toRead)
          if (read == -1) throw IllegalStateException("Unexpected end of stream for $fileName")
          bufferedOut.write(buffer, 0, read)
          remaining -= read
          session.bytesReceived.addAndGet(read.toLong())
        }
        bufferedOut.flush()
      }

      repository.finalizeSavedLocation(savedLoc)
      val fileSec = ((System.nanoTime() - fileStartNs) / 1_000_000_000.0).coerceAtLeast(0.001)
      val fileSpeed = (fileSize / (1024.0 * 1024.0)) / fileSec
      session.filesCompleted.incrementAndGet()

      session.records.add(
        TransferRecord(
          id = UUID.randomUUID().toString(),
          fileName = fileName,
          relativePath = relativePath,
          sizeBytes = fileSize,
          direction = TransferDirection.RECEIVE,
          category = ShareRepository.inferCategory(fileName, mimeType, relativePath),
          timestampMs = System.currentTimeMillis(),
          speedMBps = fileSpeed,
          peerName = session.peerName,
          savedUriString = savedLoc.uri?.toString(),
          savedFilePath = savedLoc.filePath,
          mimeType = mimeType,
        )
      )
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
    sessions.clear()
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

    val streamCount = if (turboMode) minOf(TURBO_STREAMS, items.size.coerceAtLeast(1)) else 1
    val totalBytes = items.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    TransferForegroundService.start(
      context,
      "Sending ${items.size} item(s)",
      "Target: $peerDeviceName ($host)",
    )

    senderJob =
      scope.launch {
        _progress.value =
          TransferProgressState(
            direction = TransferDirection.SEND,
            status = TransferStatus.CONNECTING,
            peerName = peerDeviceName,
            totalFiles = items.size,
            totalBytes = totalBytes,
            activeStreams = streamCount,
          )

        var controlSocket: Socket? = null
        try {
          controlSocket =
            Socket().apply {
              tcpNoDelay = true
              sendBufferSize = SOCKET_BUFFER_SIZE
              connect(InetSocketAddress(host, port), 8000)
            }

          val out = DataOutputStream(BufferedOutputStream(controlSocket.getOutputStream(), 4096))
          val inp = DataInputStream(BufferedInputStream(controlSocket.getInputStream(), 4096))

          val sessionId = UUID.randomUUID().toString()
          out.writeInt(MAGIC_CONTROL)
          out.writeInt(PROTOCOL_VERSION)
          out.writeUTF(pin)
          out.writeUTF(senderDeviceName)
          out.writeUTF(sessionId)
          out.writeInt(items.size)
          out.writeLong(totalBytes)
          out.writeInt(streamCount)
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

          _progress.value =
            _progress.value.copy(status = TransferStatus.TRANSFERRING, activeStreams = streamCount)

          val monitorJob =
            launch {
              var lastBytes = 0L
              var lastNs = System.nanoTime()
              var peakSpeed = 0.0
              while (isActive && !cancelRequested.get() && filesDone.get() < items.size) {
                delay(180L)
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
                    bytesTransferred = cur,
                    currentFileIndex = filesDone.get(),
                    speedMBps = instantMBps,
                    peakSpeedMBps = peakSpeed,
                    elapsedMs = elapsedMs,
                  )
              }
            }

          val channel = Channel<ShareableItem>(Channel.UNLIMITED)
          for (item in items) {
            channel.send(item)
          }
          channel.close()

          val workers =
            (0 until streamCount).map {
              async(Dispatchers.IO) {
                sendWorkerLoop(
                  host = host,
                  port = port,
                  sessionId = sessionId,
                  channel = channel,
                  bytesSent = bytesSent,
                  filesDone = filesDone,
                  sentRecords = sentRecords,
                  peerName = peerDeviceName,
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
          TransferForegroundService.stop(context)
        }
      }
  }

  private suspend fun sendWorkerLoop(
    host: String,
    port: Int,
    sessionId: String,
    channel: Channel<ShareableItem>,
    bytesSent: AtomicLong,
    filesDone: AtomicInteger,
    sentRecords: MutableList<TransferRecord>,
    peerName: String,
  ) {
    val workerSocket =
      Socket().apply {
        tcpNoDelay = true
        sendBufferSize = SOCKET_BUFFER_SIZE
        connect(InetSocketAddress(host, port), 8000)
      }

    workerSocket.use { sock ->
      val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), CHUNK_SIZE))
      out.writeInt(MAGIC_WORKER)
      out.writeUTF(sessionId)
      val buffer = ByteArray(CHUNK_SIZE)

      for (item in channel) {
        if (cancelRequested.get()) break
        _progress.value = _progress.value.copy(currentFileName = item.displayName)

        val streamAndSize = openSourceStream(item)
        val inputStream = streamAndSize.first
        val actualSize = streamAndSize.second

        out.writeBoolean(true)
        out.writeUTF(item.relativePath)
        out.writeUTF(item.mimeType)
        out.writeLong(actualSize)

        val fileStartNs = System.nanoTime()
        inputStream.use { rawIn ->
          val bufferedIn = BufferedInputStream(rawIn, CHUNK_SIZE)
          var written = 0L
          while (written < actualSize && !cancelRequested.get()) {
            val read = bufferedIn.read(buffer)
            if (read == -1) break
            out.write(buffer, 0, read)
            written += read
            bytesSent.addAndGet(read.toLong())
          }
        }
        out.flush()

        val sec = ((System.nanoTime() - fileStartNs) / 1_000_000_000.0).coerceAtLeast(0.001)
        val fileSpeed = (actualSize / (1024.0 * 1024.0)) / sec
        filesDone.incrementAndGet()

        sentRecords.add(
          TransferRecord(
            id = UUID.randomUUID().toString(),
            fileName = item.displayName,
            relativePath = item.relativePath,
            sizeBytes = actualSize,
            direction = TransferDirection.SEND,
            category = item.category,
            timestampMs = System.currentTimeMillis(),
            speedMBps = fileSpeed,
            peerName = peerName,
            mimeType = item.mimeType,
          )
        )
      }

      out.writeBoolean(false)
      out.flush()
    }
  }

  private fun openSourceStream(item: ShareableItem): Pair<InputStream, Long> {
    if (!item.localFilePath.isNullOrBlank()) {
      val file = java.io.File(item.localFilePath)
      return FileInputStream(file) to file.length()
    }
    val uri = item.uri ?: throw IllegalArgumentException("No URI or path for ${item.displayName}")
    var resolvedSize = item.sizeBytes
    if (resolvedSize <= 0L) {
      context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
        if (afd.length > 0L) resolvedSize = afd.length
      }
    }
    val stream =
      context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Cannot open ${item.displayName}")
    return stream to resolvedSize
  }

  fun cancelActiveTransfer() {
    cancelRequested.set(true)
    senderJob?.cancel()
    TransferForegroundService.stop(context)
    _progress.value = _progress.value.copy(status = TransferStatus.CANCELLED)
  }

  fun resetProgress() {
    _progress.value = TransferProgressState()
  }

  /**
   * Real 4-stream TCP Loopback throughput test so users can measure actual kernel socket speed
   * on their device hardware (typically 200-600+ MB/s on modern Android devices).
   */
  suspend fun runTurboLoopbackBenchmark(payloadMb: Int = 96, streams: Int = 6): BenchmarkResult =
    withContext(Dispatchers.IO) {
      val totalBytes = payloadMb.toLong() * 1024L * 1024L
      val bytesPerStream = totalBytes / streams
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
        (0 until streams).map {
          async(Dispatchers.IO) {
            Socket().use { s ->
              s.tcpNoDelay = true
              s.sendBufferSize = SOCKET_BUFFER_SIZE
              s.setPerformancePreferences(0, 1, 2)
              s.connect(InetSocketAddress("127.0.0.1", port), 3000)
              val out = s.getOutputStream()
              val chunk = ByteArray(CHUNK_SIZE) { 0x5A }
              var left = bytesPerStream
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
            delay(50L)
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
