package com.example.capstone_2

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import com.google.ar.core.Session
import com.google.ar.core.Config

class StereoARActivity : Activity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: StereoARRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = StereoARRenderer()
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContentView(glSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        ARCoreBridge.session?.resume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        ARCoreBridge.session?.pause()
    }

    override fun onDestroy() {
        ARCoreBridge.session?.close()
        super.onDestroy()
    }
}