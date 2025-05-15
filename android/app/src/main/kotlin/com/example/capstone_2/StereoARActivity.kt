package com.example.capstone_2

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import com.google.ar.core.Session
import com.google.ar.core.Config
import android.content.Context
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.view.Gravity
import android.content.Intent
import android.net.Uri

class StereoARActivity : Activity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private var arSession: Session? = null
    private lateinit var renderer: StereoARRenderer
    private val PICK_VIDEO_REQUEST = 1001


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Activity", "onCreate")
        super.onCreate(savedInstanceState)

        arSession = Session(this).apply {
            val config = Config(this)
            config.depthMode = Config.DepthMode.AUTOMATIC
            config.focusMode = Config.FocusMode.AUTO
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            configure(config)
        }

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = StereoARRenderer(arSession!!, this@StereoARActivity)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val pauseButton = Button(this).apply {
            text = "⏸"
            setOnClickListener {
                renderer.togglePlayPause()
            }
        }

        val forwardButton = Button(this).apply {
            text = ">"
            setOnClickListener {
                renderer.skipForward()
            }
        }

        val backwardButton = Button(this).apply {
            text = "<"
            setOnClickListener {
                renderer.skipBackward()
            }
        }

        val pickButton = Button(this).apply {
            text = "영상 선택"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "video/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, PICK_VIDEO_REQUEST)
            }
        }

        val layout = FrameLayout(this)
        layout.addView(glSurfaceView)

        setContentView(layout)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            val selectedVideoUri: Uri? = data?.data
            Log.d("onActivityResult", "Uri: ${selectedVideoUri}")
            if (selectedVideoUri != null) {
                glSurfaceView.queueEvent {
                    renderer.setVideoUri(this, selectedVideoUri)
                }
            }
        }
    }
}