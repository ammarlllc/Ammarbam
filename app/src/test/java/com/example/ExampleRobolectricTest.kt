package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShareRepository
import com.example.model.InstalledAppInfo
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.transfer.TransferEngine
import com.example.ui.PairingUriParser
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `main activity launches splash with credits and navigates all screens without crashing`() {
    composeRule.onNodeWithTag("splash_screen").assertExists()
    composeRule.mainClock.advanceTimeBy(700L)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("credits_title_text", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("credits_dev_name_text", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("splash_credit_text", useUnmergedTree = true).assertExists()

    composeRule.onNodeWithTag("splash_screen").performClick()
    composeRule.mainClock.advanceTimeBy(600L)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("home_send_button").assertExists()
    composeRule.onNodeWithTag("home_receive_button").assertExists()

    // Open and close Credits animation overlay
    composeRule.onNodeWithTag("open_credits_button").performClick()
    composeRule.mainClock.advanceTimeBy(800L)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("credits_overlay").assertExists()
    composeRule.onNodeWithTag("close_credits_button").performClick()
    composeRule.waitForIdle()

    // Visit Select tabs (Apps, Videos/MovieBox, Photos, Folders, Files)
    composeRule.onNodeWithTag("tab_select").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("tab_videos").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("tab_files").performClick()
    composeRule.waitForIdle()

    // Visit History and Receive Mode
    composeRule.onNodeWithTag("tab_history").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("tab_transfer").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("home_receive_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("toggle_hotspot_button").assertExists()
    composeRule.onNodeWithTag("receive_radar_back").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("home_send_button").assertExists()
  }

  @Test
  fun `app name matches Fast Share and credits match AMMAR H4CK34`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Fast Share", context.getString(R.string.app_name))
    assertEquals("CREDITS", context.getString(R.string.credits_title))
    assertEquals("AMMAR H4CK34", context.getString(R.string.credits_dev_name))
    assertEquals("MADE WITH AMMAR APP SOFTWARE", context.getString(R.string.splash_credit))
  }

  @Test
  fun `qr code generation and public Ammarshare folder work`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ShareRepository(context)
    val bmp = repo.generateQrBitmap("fastshare://192.168.49.1:48255?pin=4825", 256)
    assertNotNull(bmp)
    assertEquals(256, bmp!!.width)
    assertEquals("Ammarshare", repo.publicAmmarshareDir().name)
  }

  @Test
  fun `split apk bundle and history apk install detection work`() {
    val app =
      InstalledAppInfo(
        packageName = "com.example.game",
        appName = "Turbo Game",
        versionName = "2.1",
        apkPath = "/data/app/base.apk",
        sizeBytes = 10_000L,
        splitPaths = listOf("/data/app/split_config.arm64_v8a.apk"),
        extraDataPaths = listOf("/storage/emulated/0/Android/obb/com.example.game/main.obb"),
        totalBundleBytes = 50_000L,
      )
    assertTrue(app.hasSplitsOrData)
    assertEquals("Turbo_Game_v2.1.apk", app.exportFileName)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ShareRepository(context)
    val apkFile = java.io.File(context.cacheDir, "sample_test.apk").apply { writeText("stub") }
    repo.appendHistoryRecords(
      listOf(
        TransferRecord(
          id = "apk_rec_1",
          fileName = "sample_test.apk",
          relativePath = "Apps/sample_test.apk",
          sizeBytes = apkFile.length(),
          direction = TransferDirection.RECEIVE,
          category = ShareCategory.APPS,
          timestampMs = System.currentTimeMillis(),
          speedMBps = 125.0,
          peerName = "Pixel",
          savedFilePath = apkFile.absolutePath,
          mimeType = "application/vnd.android.package-archive",
        )
      )
    )
    val loaded = repo.loadTransferHistory()
    assertTrue(loaded.any { it.id == "apk_rec_1" })
  }

  @Test
  fun `pairing uri parser handles fastshare and ammarshare links and rejects invalid links`() {
    val fast =
      PairingUriParser.parse(
        "fastshare://192.168.49.1:48255?pin=9123&name=Pixel%209&ssid=DIRECT-FS-FastShare&pwd=fast1234",
        48255,
        "",
      )
    assertNotNull(fast)
    assertEquals("192.168.49.1", fast!!.host)
    assertEquals(48255, fast.port)
    assertEquals("9123", fast.pin)
    assertEquals("Pixel 9", fast.peerName)
    assertEquals("DIRECT-FS-FastShare", fast.hotspotSsid)
    assertEquals("fast1234", fast.hotspotPassword)

    val legacy =
      PairingUriParser.parse("ammarshare://192.168.43.1:48255?pin=1234", 48255, "")
    assertNotNull(legacy)
    assertEquals("192.168.43.1", legacy!!.host)

    assertNull(PairingUriParser.parse("https://example.com/not-fastshare", 48255, "1234"))
    assertNull(PairingUriParser.parse("random text with spaces", 48255, "1234"))
  }

  @Test
  fun `turbo tcp loopback benchmark transfers payload`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ShareRepository(context)
    val engine = TransferEngine(context, repo)
    val result = engine.runTurboLoopbackBenchmark(payloadMb = 16, streams = 8)
    assertTrue(result.throughputMBps > 0.0)
    assertEquals(16, result.payloadMegabytes)
    assertEquals(8, result.streamsUsed)
  }

  @Test
  fun `single large file stripes across parallel tcp streams with exact byte integrity`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val repo = ShareRepository(context)
      val receiverEngine = TransferEngine(context, repo)
      val senderEngine = TransferEngine(context, repo)

      // 10 MB single file -> 3 stripes (4 MB + 4 MB + 2 MB) transferred in parallel over localhost
      val sourceBytes = ByteArray(10 * 1024 * 1024) { i -> ((i * 31 + (i ushr 8)) and 0xFF).toByte() }
      val srcFile = File(context.cacheDir, "striped_movie_test.mp4").apply { writeBytes(sourceBytes) }

      val boundPort = CompletableDeferred<Int>()
      val historyUpdated = CompletableDeferred<Unit>()
      receiverEngine.startReceiver(
        expectedPin = "4825",
        onBound = { p -> boundPort.complete(p) },
        onHistoryUpdated = { historyUpdated.complete(Unit) },
      )

      val port = withTimeout(5000L) { boundPort.await() }
      val item =
        ShareableItem(
          id = "test_striped_1",
          displayName = srcFile.name,
          relativePath = "Movies/${srcFile.name}",
          sizeBytes = srcFile.length(),
          mimeType = "video/mp4",
          category = ShareCategory.VIDEOS,
          localFilePath = srcFile.absolutePath,
        )

      val senderDone = CompletableDeferred<Unit>()
      senderEngine.sendFiles(
        items = listOf(item),
        host = "127.0.0.1",
        port = port,
        pin = "4825",
        senderDeviceName = "SenderPhone",
        peerDeviceName = "ReceiverPhone",
        turboMode = true,
        onHistoryUpdated = { senderDone.complete(Unit) },
      )

      val finalSender =
        withTimeout(10_000L) {
          senderEngine.progress.first {
            it.status == TransferStatus.COMPLETED ||
              it.status == TransferStatus.FAILED ||
              it.status == TransferStatus.CANCELLED
          }
        }
      assertEquals(
        "Sender failed: ${finalSender.errorMessage}",
        TransferStatus.COMPLETED,
        finalSender.status,
      )

      val finalReceiver =
        withTimeout(10_000L) {
          receiverEngine.progress.first {
            it.status == TransferStatus.COMPLETED ||
              it.status == TransferStatus.FAILED ||
              it.status == TransferStatus.CANCELLED
          }
        }
      assertEquals(
        "Receiver failed: ${finalReceiver.errorMessage}",
        TransferStatus.COMPLETED,
        finalReceiver.status,
      )
      withTimeout(5_000L) {
        senderDone.await()
        historyUpdated.await()
      }

      val rec =
        repo.loadTransferHistory().firstOrNull {
          it.fileName == srcFile.name && it.direction == TransferDirection.RECEIVE
        }
      assertNotNull(rec)
      assertEquals(sourceBytes.size.toLong(), rec!!.sizeBytes)
      val savedFile = File(rec.savedFilePath!!)
      assertTrue(savedFile.exists())
      assertEquals(sourceBytes.size.toLong(), savedFile.length())

      val digest = MessageDigest.getInstance("SHA-256")
      val expectedSha = digest.digest(sourceBytes)
      digest.reset()
      val actualSha = digest.digest(savedFile.readBytes())
      assertArrayEquals(expectedSha, actualSha)

      receiverEngine.stopReceiver()
      srcFile.delete()
      savedFile.delete()
      Unit
    }
}
