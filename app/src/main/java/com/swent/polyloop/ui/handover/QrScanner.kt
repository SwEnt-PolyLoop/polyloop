// Made with Claude.

package com.swent.polyloop.ui.handover

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's QR code scanner over the app and gives the text it read to [onCode]. The scanner
 * is part of Google Play services and shows its own camera screen, so the app needs no camera
 * permission. Closing it, or a scanner that cannot open, gives nothing.
 */
fun scanQrCode(context: Context, onCode: (String) -> Unit) {
  val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
  GmsBarcodeScanning.getClient(context, options).startScan().addOnSuccessListener { barcode ->
    // A QR code holding no text cannot be a handover code; "" is refused as a wrong code.
    onCode(barcode.rawValue.orEmpty())
  }
}
