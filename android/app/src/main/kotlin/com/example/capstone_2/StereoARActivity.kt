package com.example.capstone_2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import com.example.eogmodule.EOGManager
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.util.UUID
import android.graphics.Color


class StereoARActivity : Activity(), SpeechController{
    private lateinit var glSurfaceView: GLSurfaceView
    private var arSession: Session? = null
    private lateinit var renderer: StereoARRenderer
    private val PICK_VIDEO_REQUEST = 1001

    private lateinit var eogManager: EOGManager
    private val DEVICE_NAME = "EOG_DEVICE"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_CODE_PERMISSIONS = 10

    lateinit var sttMessage: STTMessage
    private var listening: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Activity", "onCreate")
        checkPermissions()
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
            renderer = StereoARRenderer(arSession!!, this@StereoARActivity, this@StereoARActivity, this@StereoARActivity)
            renderer.loadVideoUri(this@StereoARActivity, 10)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val serverUrl = intent.getStringExtra("SERVER_URL") ?: "ws://default.url:8080"
        renderer.setServerUrl(serverUrl)

        eogManager = EOGManager(this)

        eogManager.setEOGEventListener(object : EOGManager.EOGEventListener {
            override fun onEyeMovement(direction: EOGManager.Direction) {
                //Toast.makeText(this@StereoARActivity, "EOG: $direction", Toast.LENGTH_SHORT).show()
                glSurfaceView.queueEvent {
                    renderer.sendDirection(direction)
                }
            }

            override fun onRawData(rawData: String) {

            }
        })


        sttMessage = STTMessage(this)
        setSTT()

        eogManager.connect(DEVICE_NAME, MY_UUID)

        val leftButton = Button(this).apply {
            text = "좌"
            textSize = 16f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.LTGRAY)
        }
        val rightButton = Button(this).apply {
            text = "우"
            textSize = 16f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.LTGRAY)
        }
        val upButton = Button(this).apply {
            text = "상"
            textSize = 16f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.LTGRAY)
        }
        val downButton = Button(this).apply {
            text = "하"
            textSize = 16f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.LTGRAY)
        }
        val rightUpButton = Button(this).apply {
            text = "우상"
            textSize = 16f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.LTGRAY)
        }

        leftButton.setOnClickListener {
            glSurfaceView.queueEvent {
                renderer.sendDirection(EOGManager.Direction.LEFT)
            }
        }

        rightButton.setOnClickListener {
            glSurfaceView.queueEvent {
                renderer.sendDirection(EOGManager.Direction.RIGHT)
            }
        }

        upButton.setOnClickListener {
            glSurfaceView.queueEvent {
                renderer.sendDirection(EOGManager.Direction.UP)
            }
        }

        downButton.setOnClickListener {
            glSurfaceView.queueEvent {
                renderer.sendDirection(EOGManager.Direction.DOWN)
            }
        }

        rightUpButton.setOnClickListener {
            glSurfaceView.queueEvent {
                renderer.sendDirection(EOGManager.Direction.RIGHT_UP)
            }
        }

        val layout = FrameLayout(this)
        layout.addView(glSurfaceView)

        var layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = 100
        }
        layout.addView(leftButton, layoutParams)

        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = 100
        }
        layout.addView(rightButton, layoutParams)

        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 100
        }
        layout.addView(upButton, layoutParams)

        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 100
        }
        layout.addView(downButton, layoutParams)

        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = 100
            topMargin = 100
        }
        layout.addView(rightUpButton, layoutParams)

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
        sttMessage.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            val selectedVideoUri: Uri? = data?.data
            Log.d("onActivityResult", "Uri: ${selectedVideoUri}")
            if (selectedVideoUri != null) {
                glSurfaceView.queueEvent {
                    renderer.setVideoUri(this@StereoARActivity, selectedVideoUri)
                }
            }
        }
    }

    override fun startListening() {
        if(!listening) {
            sttMessage.startListening()
            listening = true
            runOnUiThread {
                glSurfaceView.queueEvent {
                    renderer.textSetListening(true)
                }
            }
            //textRenderer의 마이크 빨갛게
        }
    }

    override fun setSTT() {
        sttMessage.onResult = { result ->
            runOnUiThread {
                glSurfaceView.queueEvent {
                    renderer.getSTT(result)
                }
            }
        }
        sttMessage.onEnd = {
            listening = false
            runOnUiThread {
                glSurfaceView.queueEvent {
                    renderer.textSetListening(false)
                }
            }
            //textRenderer의 마이크 원래대로
        }
    }

    private fun checkPermissions() {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        permissions.forEach { permission ->
            val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(permission), REQUEST_CODE_PERMISSIONS)
            }
            Log.d("권한요청: ", "$permission: ${if (granted) "GRANTED" else "DENIED"}")
        }
    }
}