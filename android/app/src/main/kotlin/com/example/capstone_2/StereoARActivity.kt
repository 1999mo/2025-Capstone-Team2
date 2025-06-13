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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode

import android.graphics.Bitmap
import android.media.Image
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.util.concurrent.Executors
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
import java.io.ByteArrayOutputStream
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import androidx.camera.core.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import android.os.SystemClock
import androidx.activity.ComponentActivity

class StereoARActivity : ComponentActivity(), SpeechController, HandLandmarkerHelper.LandmarkerListener {
    private lateinit var glSurfaceView: GLSurfaceView
    private var arSession: Session? = null
    private lateinit var renderer: StereoARRenderer
    private lateinit var handCheck: HandCheck
    private val PICK_VIDEO_REQUEST = 1001

    private lateinit var eogManager: EOGManager
    private val DEVICE_NAME = "EOG_DEVICE"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_CODE_PERMISSIONS = 10

    lateinit var sttMessage: STTMessage
    private var listening: Boolean = false
    private lateinit var handLandmarker: HandLandmarker
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var cameraSelector: CameraSelector
    //private lateinit var yuvConverter: YuvToRgbConverter
    private lateinit var cameraProvider: ProcessCameraProvider

    private var lastAnalyzedTimestamp = 0L
    private val analysisIntervalMs = 5000L  // 100ms 간격 (약 10fps)
    private var bitmapBuffer: Bitmap? = null

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

        /*
        Log.d("handCheck", "1")
        handCheck = HandCheck()

        Log.d("handCheck", "1")
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task") // 반드시 assets 폴더에 있어야 함
            .build()
        Log.d("handCheck", "1")
        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
        optionsBuilder
            .setResultListener { result, _ ->
                if (result.landmarks().isNotEmpty()) {
                    val landmarks = result.landmarks()[0]
                    handCheck.onHandLandmarksReceived(landmarks)
                }
            }
            .setErrorListener { error ->
                Log.e("HandLandmarker", "MediaPipe Error: $error")
            }
        Log.d("handCheck", "1")
        val handLandmarkerOptions = optionsBuilder.build()
        Log.d("handCheck", "1")
        //handLandmarker = HandLandmarker.createFromOptions(this@StereoARActivity, handLandmarkerOptions)
        Log.d("handCheck", "1")
        Log.d("handCheck", "1")
        */
        handCheck = HandCheck()

        handLandmarkerHelper = HandLandmarkerHelper(
            context = this@StereoARActivity,
            runningMode = RunningMode.IMAGE,
            currentDelegate = HandLandmarkerHelper.DELEGATE_GPU,
            handLandmarkerHelperListener = this
        )

        //yuvConverter = YuvToRgbConverter(this)

        //cameraExecutor = Executors.newSingleThreadExecutor()
        //startCamera()

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = StereoARRenderer(arSession!!, this@StereoARActivity, this@StereoARActivity, this@StereoARActivity)
            renderer.loadVideoUri(this@StereoARActivity, 10)
            renderer.setHandCheck(handCheck)
            renderer.setHandLandmarkerHelper(handLandmarkerHelper)
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


        /*
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
*/
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
        cameraExecutor.shutdown()
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

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        // 손 랜드마크 결과가 여기로 들어옵니다.
        // ARCore Anchor 위치 업데이트 등에 활용 가능
        Log.d("landmarks", "we got results")
        val landmarks = resultBundle.results[0].landmarks()
        Log.d("landmarks", "landmarks: $landmarks")
        runOnUiThread {
            // UI 업데이트가 필요하면 여기서 처리
            updateAnchorWithLandmarks(landmarks[0])
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e("StereoARActivity", "HandLandmarker error: $error")
    }

    private fun updateAnchorWithLandmarks(landmarks: List<NormalizedLandmark>) {
        // 여기서 landmarks를 기반으로 ARCore Anchor 위치 변경 로직 구현
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
        )

        permissions.forEach { permission ->
            val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(permission), REQUEST_CODE_PERMISSIONS)
            }
            Log.d("권한요청: ", "$permission: ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider // 필드로 저장해두는 것이 좋음

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480)) // 원하는 해상도
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val currentTimestamp = SystemClock.uptimeMillis()
                if (currentTimestamp - lastAnalyzedTimestamp >= analysisIntervalMs) {
                    lastAnalyzedTimestamp = currentTimestamp
                    detectLiveStream(imageProxy, isFrontCamera = false)
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, // Activity가 LifecycleOwner여야 함
                    cameraSelector,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("StereoARActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val startTime = SystemClock.uptimeMillis()
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = isFrontCamera
        )
        val duration = SystemClock.uptimeMillis() - startTime
        Log.d("Perf", "Detection took $duration ms")
    }
}