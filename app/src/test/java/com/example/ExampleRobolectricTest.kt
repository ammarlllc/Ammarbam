package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShareRepository
import com.example.transfer.TransferEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Ammar Share`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    val splashCredit = context.getString(R.string.splash_credit)
    assertEquals("Ammar Share", appName)
    assertEquals("MADE WITH AMMAR APP SOFTWARE", splashCredit)
  }

  @Test
  fun `qr code generation succeeds`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ShareRepository(context)
    val bmp = repo.generateQrBitmap("ammarshare://192.168.1.10:48255?pin=4825", 256)
    assertNotNull(bmp)
    assertEquals(256, bmp!!.width)
  }

  @Test
  fun `turbo tcp loopback benchmark transfers payload`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ShareRepository(context)
    val engine = TransferEngine(context, repo)
    val result = engine.runTurboLoopbackBenchmark(payloadMb = 12, streams = 6)
    assertTrue(result.throughputMBps > 0.0)
    assertEquals(12, result.payloadMegabytes)
    assertEquals(6, result.streamsUsed)
  }
}
