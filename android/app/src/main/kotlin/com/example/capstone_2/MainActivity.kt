package com.example.capstone_2

import android.os.Bundle
import com.google.ar.core.Config
import com.google.ar.core.Session
//import android.opengl.Matrix
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.util.Log
import android.content.Intent
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanContract
import androidx.activity.result.ActivityResultLauncher
import android.widget.EditText
import android.app.AlertDialog

class MainActivity : FlutterActivity() {
    private val CHANNEL = "arcore_channel"
    private var arSession: Session? = null
    private val QR_SCAN_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            arSession = Session(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        Log.d("Main", "configureFlutter")
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "adjustStereoFocus" -> {
                    arSession?.let { session ->
                        Log.d("Main", "channel call")
                        val editText = EditText(this)
                        editText.hint = "ws://192.168.0.x:8080"

                        AlertDialog.Builder(this)
                            .setTitle("서버 주소 입력")
                            .setView(editText)
                            .setPositiveButton("연결") { _, _ ->
                                val serverUrl = editText.text.toString().ifBlank {
                                    "ws://default.url:8080"
                                }

                                val intent = Intent(this, StereoARActivity::class.java).apply {
                                    putExtra("SERVER_URL", serverUrl)
                                }

                                startActivity(intent)
                            }
                            .setNegativeButton("취소", null)
                            .show()
                        result.success("Adjusted")
                    } ?: result.error("SESSION_ERROR", "AR Session not initialized", null)
                }
                "connectToPC" -> {
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun adjustStereoRendering(session: Session, width: Int, height: Int) {
        session.setDisplayGeometry(0, width / 2, height)
    }

    private fun setFocusMode(session: Session) {
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        session.configure(config)
    }
}