// Made with Claude.

package com.swent.polyloop.ui.handover

import android.content.Context
import com.google.android.gms.tasks.OnFailureListener
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

/**
 * [scanQrCode], with Google's scanner mocked: what it reads goes to the screen, and a scanner that
 * cannot open is reported.
 */
class QrScannerTest {

  private val context = mockk<Context>()
  private val scanner = mockk<GmsBarcodeScanner>()
  private val scan = mockk<Task<Barcode>>()
  private val onRead = slot<OnSuccessListener<Barcode>>()
  private val onFailed = slot<OnFailureListener>()
  private val codes = mutableListOf<String>()
  private var failures = 0

  @Before
  fun setUp() {
    mockkStatic(GmsBarcodeScanning::class)
    every { GmsBarcodeScanning.getClient(context, any()) } returns scanner
    every { scanner.startScan() } returns scan
    every { scan.addOnSuccessListener(capture(onRead)) } returns scan
    every { scan.addOnFailureListener(capture(onFailed)) } returns scan
  }

  @After
  fun tearDown() {
    unmockkStatic(GmsBarcodeScanning::class)
  }

  private fun scan() = scanQrCode(context, onCode = { codes += it }, onFailure = { failures++ })

  private fun read(text: String?) =
      onRead.captured.onSuccess(mockk { every { rawValue } returns text })

  @Test
  fun theTextReadGoesToTheScreen() {
    scan()
    verify { scanner.startScan() }

    read("secret-code")
    assertEquals(listOf("secret-code"), codes)
    assertEquals(0, failures)
  }

  @Test
  fun aQrCodeWithoutTextGivesAnEmptyCode() {
    scan()
    read(null)
    assertEquals(listOf(""), codes)
  }

  @Test
  fun aScannerThatCannotOpenIsReportedWithoutACode() {
    scan()
    onFailed.captured.onFailure(
        IllegalStateException("Waiting for the Barcode UI module to be downloaded.")
    )

    assertEquals(1, failures)
    assertTrue(codes.isEmpty())
  }
}
