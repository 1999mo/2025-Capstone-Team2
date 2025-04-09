package com.example.capstone_2

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import com.google.ar.core.Session
import com.google.ar.core.Config
import android.content.Context
import android.util.Log

class StereoARActivity : Activity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private var arSession: Session? = null
    private lateinit var renderer: StereoARRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Activity", "onCreate")
        super.onCreate(savedInstanceState)

        arSession = Session(this).apply {
            val config = Config(this)
            config.focusMode = Config.FocusMode.AUTO
            configure(config)
        }

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = StereoARRenderer(arSession!!, this@StereoARActivity)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContentView(glSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        arSession?.resume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        arSession?.close()
        super.onDestroy()
    }
}