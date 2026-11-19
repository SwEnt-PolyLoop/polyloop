// Made with Claude.

package com.swent.polyloop.ui.handover

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's QR code scanner over the app and gives the text it read to [onCode]. The scanner
 * is part of Google Play services and shows its own camera screen, so the app needs no camera
 * permission. A scanner that cannot open (e.g. still being downloaded, or Google Play services
 * missing or out of date) calls [onFailure]; closing it calls neither.
 */
fun scanQrCode(context: Context, onCode: (String) -> Unit, onFailure: () -> Unit) {
  val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
  GmsBarcodeScanning.getClient(context, options)
      .startScan()
      // A QR code holding no text cannot be a handover code; "" is refused as a wrong code.
      .addOnSuccessListener { barcode -> onCode(barcode.rawValue.orEmpty()) }
      // A cancel by the user is not a failure: it calls neither listener.
      .addOnFailureListener { onFailure() }
}
