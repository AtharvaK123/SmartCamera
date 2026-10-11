package com.hack.smartcam.food

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Food barcodes (EAN-13/8, UPC-A/E) with ML Kit — on-device, no network. */
object BarcodeReader {
    val client: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }

    /** First valid product code in the image, or null. */
    suspend fun read(image: InputImage): String? = suspendCancellableCoroutine { cont ->
        client.process(image)
            .addOnSuccessListener { list -> cont.resume(list.mapNotNull { it.rawValue }.firstOrNull { valid(it) }) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun read(bitmap: Bitmap): String? = read(InputImage.fromBitmap(bitmap, 0))

    /** Check digit test for EAN/UPC so a misread never reaches the lookup. */
    fun valid(code: String): Boolean {
        if (!code.all(Char::isDigit) || code.length !in setOf(8, 12, 13)) return false
        val digits = code.map { it - '0' }
        val body = digits.dropLast(1).reversed()
        val sum = body.mapIndexed { i, d -> if (i % 2 == 0) d * 3 else d }.sum()
        return (10 - sum % 10) % 10 == digits.last()
    }
}
