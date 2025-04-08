package com.example.capstone_2

import android.os.Bundle
import com.google.ar.core.Config
import com.google.ar.core.Session
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry
import android.util.Log

class MainActivity : FlutterActivity() {
    private val CHANNEL = "arcore_channel"
    private var arSession: Session? = null
    private var surfaceTextureEntry: TextureRegistry.SurfaceTextureEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            arSession = Session(this).apply {
                val config = Config(this)
                config.focusMode = Config.FocusMode.AUTO
                configure(config)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getARTexture" -> {
                    Log.d("Main", "0")
                    if (surfaceTextureEntry == null) {
                        Log.d("Main", "1")
                        surfaceTextureEntry = flutterEngine.renderer.createSurfaceTexture()
                        surfaceTextureEntry?.surfaceTexture()?.setDefaultBufferSize(1080, 1920)

                        val textureId = surfaceTextureEntry!!.id().toInt()
                        Log.d("Main", "test: $textureId")
                        arSession?.setCameraTextureName(textureId)
                        Log.d("Main", "test: $textureId")

                        ARCoreBridge.surfaceTexture = surfaceTextureEntry!!.surfaceTexture()
                        ARCoreBridge.session = arSession
                        ARCoreBridge.textureId = surfaceTextureEntry!!.id().toInt()
                        Log.d("Main", "test: $textureId")
                    }
                    Log.d("Main", "2")

                    result.success(surfaceTextureEntry?.id())
                }
                else -> result.notImplemented()
            }
        }
    }
}
