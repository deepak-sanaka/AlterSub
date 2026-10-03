package com.altersub.ui.settings

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {

    private fun decode(content: String): String {
        val matrix = QrCode.matrix(content)
        // Scale each module up a few pixels, as a camera would see it, then read it back
        val scale = 4
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height) { i ->
            if (matrix[(i % width) / scale, (i / width) / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun testPairingLinkRoundTrips() {
        val link = QrCode.remoteLink("192.168.1.42", 8081, "048213")

        assertEquals("http://192.168.1.42:8081/#pin=048213", link)
        assertEquals(link, decode(link))
    }

    @Test
    fun testLinkWithoutPinHasNoFragment() {
        assertEquals("http://10.0.0.5:8080/", QrCode.remoteLink("10.0.0.5", 8080, null))
    }

    @Test
    fun testCodeStaysSmall() {
        // One pixel per module: a few KB of bitmap, however large the ImageView draws it
        val matrix = QrCode.matrix(QrCode.remoteLink("192.168.100.200", 8089, "999999"))
        assertTrue("Unexpectedly large QR: ${matrix.width} modules", matrix.width <= 41)
    }
}
