package com.example.capstone_2

import android.os.Bundle
import com.google.ar.core.Config
import com.google.ar.core.Session
import android.opengl.Matrix
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "arcore_channel"
    private var arSession: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ARCore 세션 초기화
        try {
            arSession = Session(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "adjustStereoFocus" -> {
                    arSession?.let { session ->
                        adjustStereoRendering(session, 1080, 1920)
                        setFocusMode(session)
                        result.success("Adjusted")
                    } ?: result.error("SESSION_ERROR", "AR Session not initialized", null)
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

    private fun setStereoProjection(leftEyeMatrix: FloatArray, rightEyeMatrix: FloatArray) {
        val projectionMatrix = FloatArray(16)
        Matrix.frustumM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 100f)

        // left
        System.arraycopy(projectionMatrix, 0, leftEyeMatrix, 0, 16)
        Matrix.translateM(leftEyeMatrix, 0, -0.03f, 0f, 0f)

        // right
        System.arraycopy(projectionMatrix, 0, rightEyeMatrix, 0, 16)
        Matrix.translateM(rightEyeMatrix, 0, 0.03f, 0f, 0f)
    }
}