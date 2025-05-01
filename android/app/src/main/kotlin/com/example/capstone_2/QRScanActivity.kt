package com.example.capstone_2

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.*
import android.opengl.GLES20

class QRScanActivity : AppCompatActivity() {
    private lateinit var scanLauncher: androidx.activity.result.ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scanLauncher = registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                Log.d("QRScan", "스캔된 주소: ${result.contents}")
                connectWebSocket(result.contents)
            }
            finish()
        }

        val options = ScanOptions().apply {
            setPrompt("QR 코드를 스캔하세요")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }

        scanLauncher.launch(options)
    }

    private fun connectWebSocket(ip: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(ip).build()
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "서버 연결 성공")
                webSocket.send("Android에서 연결됨")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "메시지 수신: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "연결 실패: ${t.message}")
            }
        })
    }

    /*
    fun sendCurrentFrameOverSocket(webSocket: WebSocket) {
        val width = screenWidth
        val height = screenHeight
        val buffer = IntArray(width * height)
        val intBuffer = IntBuffer.wrap(buffer)
        intBuffer.position(0)

        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, intBuffer)

        // Convert to Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(buffer))

        // Optional: Flip vertically (OpenGL origin is bottom-left)
        val flipped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flipped)
        val matrix = Matrix()
        matrix.preScale(1f, -1f)
        canvas.drawBitmap(bitmap, matrix, null)

        // Compress to JPEG
        val byteStream = ByteArrayOutputStream()
        flipped.compress(Bitmap.CompressFormat.JPEG, 60, byteStream)
        val byteArray = byteStream.toByteArray()

        // Send over WebSocket
        webSocket.send(ByteString.of(*byteArray))
    }*/
}