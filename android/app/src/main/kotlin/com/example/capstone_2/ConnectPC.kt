package com.example.capstone_2

import android.util.Log
import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanContract

object ConnectPC {
    private var webSocket: WebSocket? = null
    //private lateinit var qrLauncher: ActivityResultLauncher<Intent>
    fun connect(
        activity: Activity,
        scanLauncher: ActivityResultLauncher<ScanOptions>
    ): String {
        val options = ScanOptions()
        options.setPrompt("QR 코드 스캔")
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        options.setBarcodeImageEnabled(true)
        scanLauncher.launch(options)
        return "연결 시도 ConnectPC.kt"
    }

    fun connectWebSocket(url: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                ws.send("Capstone connection from android")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("WebSocket", "메시지: $text")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, resposne: okhttp3.Response?) {
                Log.e("WebSocket", "오류: ${t.message}")
            }
        }

        val ws = client.newWebSocket(request, listener)
    }
}