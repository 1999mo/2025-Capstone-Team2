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
import com.example.eogmodule.EOGManager
import java.util.UUID
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast

class StereoARActivity : Activity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private var arSession: Session? = null
    private lateinit var renderer: StereoARRenderer
    private val PICK_VIDEO_REQUEST = 1001

    private lateinit var eogManager: EOGManager
    private val DEVICE_NAME = "EOG_DEVICE"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_CODE_PERMISSIONS = 10

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

        checkBluetoothPermissions()
        eogManager = EOGManager(this)

        eogManager.setEOGEventListener(object : EOGManager.EOGEventListener {
            override fun onRawData(rawData: String?) {
                Log.d("EOG", "Raw data: $rawData")
            }
        })

        eogManager.setHorizontalListener { direction ->
            val message = "Horizontal move: $direction"
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }

            glSurfaceView.queueEvent {
                renderer.setSelection(direction)
            }
        }

        val connectButton = Button(this).apply {
            text = "EOG 연결"
            setOnClickListener {
                checkBluetoothPermissions()
                eogManager.connect(DEVICE_NAME, MY_UUID)
            }
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

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = 32
            topMargin = 32
        }

        layout.addView(connectButton, layoutParams)

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
        eogManager.disconnect()
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

    private fun checkBluetoothPermissions() {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        permissions.forEach {
            if (checkSelfPermission(it) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(it), REQUEST_CODE_PERMISSIONS)
            }
        }
    }
}