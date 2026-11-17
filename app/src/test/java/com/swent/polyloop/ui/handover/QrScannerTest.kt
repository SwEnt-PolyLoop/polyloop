// Made with Claude.

package com.swent.polyloop.ui.handover

import android.content.Context
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [scanQrCode], with Google's scanner mocked: what it reads goes to the screen, nothing else. */
class QrScannerTest {

  private val context = mockk<Context>()
  private val scanner = mockk<GmsBarcodeScanner>()
  private val scan = mockk<Task<Barcode>>()
  private val onRead = slot<OnSuccessListener<Barcode>>()
  private val codes = mutableListOf<String>()

  @Before
  fun setUp() {
    mockkStatic(GmsBarcodeScanning::class)
    every { GmsBarcodeScanning.getClient(context, any()) } returns scanner
    every { scanner.startScan() } returns scan
    every { scan.addOnSuccessListener(capture(onRead)) } returns scan
  }

  @After
  fun tearDown() {
    unmockkStatic(GmsBarcodeScanning::class)
  }

  private fun read(text: String?) =
      onRead.captured.onSuccess(mockk { every { rawValue } returns text })

  @Test
  fun theTextReadGoesToTheScreen() {
    scanQrCode(context) { codes += it }
    verify { scanner.startScan() }

    read("secret-code")
    assertEquals(listOf("secret-code"), codes)
  }

  @Test
  fun aQrCodeWithoutTextGivesAnEmptyCode() {
    scanQrCode(context) { codes += it }
    read(null)
    assertEquals(listOf(""), codes)
  }

  @Test
  fun nothingIsGivenUntilACodeIsRead() {
    // Closing the scanner, or a scanner that fails, never calls the success listener.
    scanQrCode(context) { codes += it }
    assertTrue(codes.isEmpty())
  }
}
