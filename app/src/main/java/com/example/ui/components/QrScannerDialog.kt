package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurfaceHigh
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerDialog(
  onQrScanned: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  BackHandler(onBack = onDismiss)

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val handled = remember { AtomicBoolean(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      try {
        boundProvider?.unbindAll()
      } catch (_: Throwable) {}
      try {
        analysisExecutor.shutdownNow()
      } catch (_: Throwable) {}
    }
  }

  val infinite = rememberInfiniteTransition(label = "qrLaser")
  val laserFraction by
    infinite.animateFloat(
      initialValue = 0.08f,
      targetValue = 0.92f,
      animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
      label = "laserY",
    )

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ObsidianBg)
        .safeDrawingPadding()
        .testTag("qr_scanner_overlay"),
  ) {
    AndroidView(
      factory = { ctx ->
        val previewView = PreviewView(ctx).apply {
          implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        try {
          val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
          cameraProviderFuture.addListener(
            {
              try {
                val cameraProvider = cameraProviderFuture.get()
                boundProvider = cameraProvider
                val preview =
                  Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                  }
                val analyzer =
                  ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                      analysis.setAnalyzer(
                        analysisExecutor,
                        ZxingQrAnalyzer { decoded ->
                          if (handled.compareAndSet(false, true)) {
                            ContextCompat.getMainExecutor(ctx).execute {
                              try {
                                cameraProvider.unbindAll()
                              } catch (_: Throwable) {}
                              onQrScanned(decoded)
                            }
                          }
                        },
                      )
                    }

                val selector =
                  if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                  } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                  }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analyzer)
              } catch (t: Throwable) {
                cameraError = t.localizedMessage ?: "Unable to start camera"
              }
            },
            ContextCompat.getMainExecutor(ctx),
          )
        } catch (t: Throwable) {
          cameraError = t.localizedMessage ?: "Camera initialization failed"
        }
        previewView
      },
      modifier = Modifier.fillMaxSize(),
    )

    // Targeting Reticle Canvas
    Canvas(modifier = Modifier.fillMaxSize()) {
      val boxSize = minOf(size.width, size.height) * 0.64f
      val left = (size.width - boxSize) / 2f
      val top = (size.height - boxSize) / 2f

      drawRoundRect(
        brush = Brush.linearGradient(listOf(CyberCyan, ElectricViolet)),
        topLeft = Offset(left, top),
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
        style = Stroke(width = 3.dp.toPx()),
      )

      val laserY = top + boxSize * laserFraction
      drawLine(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, CyberCyan, Color.Transparent)),
        start = Offset(left + 14.dp.toPx(), laserY),
        end = Offset(left + boxSize - 14.dp.toPx(), laserY),
        strokeWidth = 2.5.dp.toPx(),
      )
    }

    // Top Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(50),
        color = ObsidianSurfaceHigh.copy(alpha = 0.88f),
      ) {
        Text(
          text = "Scan Receiver QR Code",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }

      IconButton(
        onClick = onDismiss,
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(ObsidianSurfaceHigh.copy(alpha = 0.88f))
            .testTag("close_qr_scanner_button"),
      ) {
        Icon(Icons.Rounded.Close, contentDescription = "Close scanner", tint = Color.White)
      }
    }

    // Bottom Hint / Error
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.BottomCenter),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurfaceHigh.copy(alpha = 0.92f),
        modifier =
          Modifier.border(1.dp, ElectricViolet.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text =
              cameraError
                ?: "Point camera at the QR code on the receiver's Fast Share screen",
            style = MaterialTheme.typography.bodyMedium,
            color = if (cameraError != null) Color(0xFFFF8A80) else Color.White,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Auto-joins Fast Share Hotspot & starts transfer",
            style = MaterialTheme.typography.labelSmall,
            color = CyberCyan,
          )
        }
      }
    }
  }
}

private class ZxingQrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {

  private val reader =
    MultiFormatReader().apply {
      setHints(
        mapOf(
          DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
          DecodeHintType.TRY_HARDER to true,
        )
      )
    }

  override fun analyze(imageProxy: ImageProxy) {
    try {
      val plane = imageProxy.planes.firstOrNull() ?: return
      val width = imageProxy.width
      val height = imageProxy.height
      if (width <= 0 || height <= 0) return

      val buffer = plane.buffer.duplicate()
      buffer.rewind()
      val rowStride = plane.rowStride
      val pixelStride = plane.pixelStride.coerceAtLeast(1)

      val yData = ByteArray(width * height)
      if (pixelStride == 1 && rowStride == width && buffer.remaining() >= width * height) {
        buffer.get(yData, 0, width * height)
      } else {
        val totalRemaining = buffer.remaining()
        val raw = ByteArray(totalRemaining)
        buffer.get(raw)
        for (y in 0 until height) {
          val rowOffset = y * rowStride
          if (rowOffset >= raw.size) break
          if (pixelStride == 1) {
            val copyLen = minOf(width, raw.size - rowOffset)
            System.arraycopy(raw, rowOffset, yData, y * width, copyLen)
          } else {
            for (x in 0 until width) {
              val idx = rowOffset + x * pixelStride
              if (idx >= raw.size) break
              yData[y * width + x] = raw[idx]
            }
          }
        }
      }

      val source = PlanarYUVLuminanceSource(yData, width, height, 0, 0, width, height, false)
      val bitmap = BinaryBitmap(HybridBinarizer(source))
      val result = reader.decodeWithState(bitmap)
      val text = result?.text
      if (!text.isNullOrBlank()) {
        onDecoded(text)
      }
    } catch (_: Throwable) {
      // Ignore frame decode errors / NotFoundException safely
    } finally {
      try {
        reader.reset()
      } catch (_: Throwable) {}
      try {
        imageProxy.close()
      } catch (_: Throwable) {}
    }
  }
}
