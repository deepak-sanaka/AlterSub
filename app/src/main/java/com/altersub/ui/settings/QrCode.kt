package com.altersub.ui.settings

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR codes for the TV screen. The bitmap holds one pixel per module (a few KB) and is scaled up by the
 * ImageView without filtering, so it stays sharp without a large bitmap in memory on 1GB boxes.
 */
object QrCode {

    /** Size 0 asks ZXing for its natural size: one pixel per module, plus [QUIET_ZONE_MODULES] of margin. */
    fun matrix(content: String): BitMatrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES
        )
    )

    fun drawable(resources: Resources, content: String): BitmapDrawable {
        val matrix = matrix(content)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { i ->
            if (matrix[i % width, i / width]) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        // Nearest-neighbour scaling keeps module edges crisp; bilinear filtering would blur them
        return BitmapDrawable(resources, bitmap).apply { isFilterBitmap = false }
    }

    /** Phone-remote link. The PIN rides in the fragment, which browsers never send to the server. */
    fun remoteLink(host: String, port: Int, pin: String?): String =
        "http://$host:$port/" + if (pin != null) "#pin=$pin" else ""

    private const val QUIET_ZONE_MODULES = 2
}
