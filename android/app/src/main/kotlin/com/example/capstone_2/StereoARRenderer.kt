package com.example.capstone_2

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.Image
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.example.eogmodule.EOGManager
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Activity

class StereoARRenderer(
    private val session: Session,
    private val context: Context,
    private val activity: Activity,
    private val speechController: SpeechController,
) : GLSurfaceView.Renderer, WebSocketMessageListener{

    private var screenWidth = 0
    private var screenHeight = 0
    private var shaderProgram: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var positionHandle: Int = 0
    private var planeShaderProgram: Int = 0
    private var planeMvpMatrixHandle: Int = 0
    private var planePositionHandle: Int = 0
    private var planeColorHandle: Int = 0
    private var menuShaderProgram: Int = 0
    private var circleHighlight: Int = 0
    private var circleProgram: Int = 0
    //private var cameraTextureId: Int = -1
    private var oesTextureId: Int = -1
    private var menuIconProgram: Int = 0
    private var menuIconTexture: Int = -1
    private lateinit var videoRenderer: VideoRenderer
    private var selection: Int = 1

    private val leftEyeMatrix = FloatArray(16)
    private val rightEyeMatrix = FloatArray(16)

    private lateinit var surfaceTexture: SurfaceTexture
    private val quadCoords = floatArrayOf(
        -1f, -1f, 0f,
        1f, -1f, 0f,
        -1f,  1f, 0f,
        1f,  1f, 0f
    )
    private val texCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private var anchor: Anchor? = null

    lateinit var mediaPlayer: MediaPlayer
    lateinit var videoTexture: SurfaceTexture
    var videoTextureId: Int = 0
    val depthTextureId = IntArray(1)
    private var videoListUri: List<Uri>? =  null
    private var videoUri: Uri? = null
    lateinit var iconTextureIds: IntArray
    lateinit var textRenderer: TextRenderer
    lateinit var imageRenderer: ImageRenderer

    private var messageIn = false

    var menuSelected = true

    private var messageHandler = Handler(Looper.getMainLooper())
    private var directedUserId = "PC"

    private var pendingSelectionJob: Job? = null
    private var currentPendingDirection: EOGManager.Direction? = null
    private var pendingProgress: Float = 0f

    private var serverUrl: String = "ws://175.198.71.84:8080"

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d("Render", "onSurfaceCreated")
        val eogManager = EOGManager(context)
        GLES20.glClearColor(0f, 0f, 0f, 0f)

        oesTextureId = GlUtil.createOESTexture()
        //cameraTextureId = createCameraTexture()
        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture.setOnFrameAvailableListener {
        }
        session.setCameraTextureName(oesTextureId)

        videoTextureId = GlUtil.createOESTexture()
        videoTexture = SurfaceTexture(videoTextureId)
        videoTexture.setOnFrameAvailableListener {

        }
        videoRenderer = VideoRenderer(context)
        videoRenderer.setVideoTexture(videoTextureId)

        val projectionMatrix = FloatArray(16)
        Matrix.frustumM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 100f)

        //Left
        System.arraycopy(projectionMatrix, 0, leftEyeMatrix, 0, 16)
        Matrix.translateM(leftEyeMatrix, 0, -0.03f, 0f, 0f)

        //Right
        System.arraycopy(projectionMatrix, 0, rightEyeMatrix, 0, 16)
        Matrix.translateM(rightEyeMatrix, 0, 0.03f, 0f, 0f)

        val vertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        val fragmentShaderCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            
            vec2 barrelDistortion(vec2 uv, float k) {
                vec2 center = vec2(0.5, 0.5);
                vec2 delta = uv - center;
                float r2 = dot(delta, delta);
                return center + delta * (1.0 + k * r2);
            }

            void main() {
                vec2 distortedUV = barrelDistortion(v_TexCoord, -0.3);
                if (distortedUV.x < 0.0 || distortedUV.x > 1.0 || distortedUV.y < 0.0 || distortedUV.y > 1.0) {
                discard;
            }
            gl_FragColor = texture2D(u_Texture, distortedUV);
        }
        """

        shaderProgram = ShaderUtil.createProgram(vertexShaderCode, fragmentShaderCode)

        //GLES20.glUseProgram(shaderProgram)

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")

        /*val planeVertexShaderCode = """
            attribute vec4 a_Position;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * a_Position;
            }
        """*/

        val planeVertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 v_TexCoord;
            
            void main() {
                gl_Position = uMVPMatrix * a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        /*val planeFragmentShaderCode = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """*/

        val planeFragmentShaderCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            
            uniform samplerExternalOES u_Texture;
            uniform sampler2D u_DepthTexture;
            varying vec2 v_TexCoord;
            
            void main() {
                float depth = texture2D(u_DepthTexture, v_TexCoord).r;
                
                if (depth < 0.5) {
                    //discard;
                }
                
                gl_FragColor = texture2D(u_Texture, vec2(v_TexCoord.x, 1.0 - v_TexCoord.y));
            }
        """

        planeShaderProgram = ShaderUtil.createProgram(planeVertexShaderCode, planeFragmentShaderCode)
        planePositionHandle = GLES20.glGetAttribLocation(planeShaderProgram, "a_Position")
        planeMvpMatrixHandle = GLES20.glGetUniformLocation(planeShaderProgram, "uMVPMatrix")
        planeColorHandle = GLES20.glGetUniformLocation(planeShaderProgram, "u_Color")

        val menuVertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 v_TexCoord;
            
            void main() {
                gl_Position = uMVPMatrix * a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        val menuFragmentShaderCode = """
            precision mediump float;
            uniform sampler2D u_Texture;
            varying vec2 v_TexCoord;

            void main() {
                vec2 center = vec2(0.5, 0.5);
                float dist = distance(v_TexCoord, center);
                
                if (dist > 0.5) {
                    discard;
                }

                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        menuShaderProgram = ShaderUtil.createProgram(menuVertexShaderCode, menuFragmentShaderCode)

        iconTextureIds = IntArray(4)
        iconTextureIds[0] = loadTexture(context, R.drawable.icon3)
        iconTextureIds[1] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[2] = loadTexture(context, R.drawable.icon4)
        iconTextureIds[3] = loadTexture(context, R.drawable.icon2)

        textRenderer = TextRenderer(context)
        textRenderer.connect(serverUrl)
        textRenderer.setWebSocketMessageListener(this)

        val highlightVertexShaderCode = """
            attribute vec4 a_Position;
            uniform mat4 uMVPMatrix;
            
            void main() {
                gl_Position = uMVPMatrix * a_Position;
            }
        """

        val highlightFragmentShaderCode = """
            precision mediump float;
            uniform vec4 u_Color;
            
            void main() {
                gl_FragColor = u_Color;
            }
        """

        circleHighlight = ShaderUtil.createProgram(highlightVertexShaderCode, highlightFragmentShaderCode)

        val circleVertexShaderCode = """
            attribute vec4 a_Position;
            uniform mat4 uMVPMatrix;
            
            void main() {
                gl_Position = uMVPMatrix * a_Position;
            }
        """

        val circleFragmentShaderCode = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """

        circleProgram = ShaderUtil.createProgram(circleVertexShaderCode, circleFragmentShaderCode)

        imageRenderer = ImageRenderer()
        imageRenderer.init(context)
        Log.d("Render", "OnsurfaceCreated finished")
        setVideoUri(context, videoUri!!)
        videoRenderer.loadTextureThumbnail(videoListUri!!, videoUri!!)

        menuIconProgram = ShaderUtil.createProgram(menuVertexShaderCode, menuFragmentShaderCode)
        menuIconTexture = loadTexture(context, R.drawable.menuicon)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height

        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        session.setDisplayGeometry(rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.d("Render", "onDrawFrame")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val eyeWidth = (screenWidth * 0.46).toInt()
        val eyeGap = (screenWidth * 0.04).toInt()
        val anchorMatrix = FloatArray(16)

        val frame = session.update()
        val updatedPlanes = session.getAllTrackables(Plane::class.java)
        val camera = frame.camera
        try {
            val depthImage = frame.acquireDepthImage()
            //val width = depthImage.width
            //val height = depthImage.height

            GLES20.glGenTextures(1, depthTextureId, 0)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTextureId[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val depthData = depthImage.planes[0].buffer
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT,
                depthImage.width, depthImage.height, 0,
                GLES20.GL_DEPTH_COMPONENT, GLES20.GL_FLOAT, depthData
            )

            depthImage.close()
        } catch (e: NotYetAvailableException)  {

        }
        surfaceTexture.updateTexImage()
        videoTexture.updateTexImage()

        Log.d("Render", "Trying Anchor creation")
        Log.d("Render", "Tracking: ${camera.trackingState}, Anchor: $anchor")
        if (camera.trackingState == TrackingState.TRACKING && anchor == null) {
            /*val cameraPose = camera.displayOrientedPose
            val forward = floatArrayOf(0f, 0f, -2f)
            val up = floatArrayOf(0f, 1f, 0f)
            //val out = FloatArray(3)
            val rotatedForward = FloatArray(3)
            cameraPose.rotateVector(forward, 0, rotatedForward, 0)
            //val rotationQuat = OtherUtil.makeQuaternionLookRotation(rotatedForward, up)

            val position = floatArrayOf(
                cameraPose.tx() + rotatedForward[0],
                cameraPose.ty() + rotatedForward[1],
                cameraPose.tz() + rotatedForward[2]
            )

            val lookRotation = cameraPose.rotationQuaternion
            val flippedRotation = floatArrayOf(
                -lookRotation[0], // x
                -lookRotation[1], // y
                -lookRotation[2], // z
                lookRotation[3]  // w (회전 축은 반전하지 않음)
            )
            //val rotationPose = Pose.makeRotationFromVectors(forward = out, up = up)

            val anchorPose = Pose(position, flippedRotation)

            anchor = session.createAnchor(anchorPose)
            Log.d("Render", "Anchor created successfully")*/
            val cameraPose = camera.displayOrientedPose

            // 카메라 앞쪽 방향으로 일정 거리 떨어진 위치
            val forward = floatArrayOf(0f, 0f, -1.5f) // -Z 방향 (카메라 기준)
            val rotatedForward = FloatArray(3)
            cameraPose.rotateVector(forward, 0, rotatedForward, 0)

            // 최종 Anchor 위치
            val position = floatArrayOf(
                cameraPose.tx() + rotatedForward[0],
                cameraPose.ty() + rotatedForward[1],
                cameraPose.tz() + rotatedForward[2]
            )

            // 카메라를 기준으로 평면이 카메라를 향하게 회전 (쿼터니언 유지)
            val rotation = cameraPose.rotationQuaternion

            // Anchor Pose 생성
            val anchorPose = Pose(position, rotation)
            anchor = session.createAnchor(anchorPose)
        }

        if(anchor != null) {
            anchor!!.pose.toMatrix(anchorMatrix, 0)
        }

        //val halfWidth = screenWidth / 2
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

        //ui matrix
        val uiProjectionMatrix = FloatArray(16)
        val uiModelMatrix = FloatArray(16)
        val uiMvpMatrix = FloatArray(16)

        Matrix.orthoM(uiProjectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
        Matrix.setIdentityM(uiModelMatrix, 0)
        Matrix.multiplyMM(uiMvpMatrix, 0, uiProjectionMatrix, 0, uiModelMatrix, 0)

        GLES20.glViewport(eyeGap, 0, eyeWidth, screenHeight)
        drawScene(leftEyeMatrix)
        //::mediaPlayer.isInitialized
        if(messageIn) {
            //textRenderer.updateDrawInMessage()
            textRenderer.drawInMessage()
        }
        if (!menuSelected) {
            drawCircleIcons(uiMvpMatrix, iconTextureIds)
        } else if (anchor != null) {
            drawMenuIcon()
            if (selection == 0) {
                if(messageIn) {
                    messageIn = false
                }
                textRenderer.drawBigText()
            } else if (selection == 1) {
                //닫기
                menuSelected != menuSelected
            } else if (selection == 2) {
                imageRenderer.draw()
            } else if (selection == 3) {
                videoRenderer.draw(anchorMatrix, viewMatrix, projectionMatrix)
            }
        }

        if(currentPendingDirection != null) {
            val (x, y) = getCoordFromDirection()
            drawCircularProgress(x, y)
        }

        GLES20.glViewport(screenWidth - eyeWidth - eyeGap, 0, eyeWidth, screenHeight)
        drawScene(rightEyeMatrix)
        if(messageIn) {
            textRenderer.updateDrawInMessage()
            textRenderer.drawInMessage()
        }
        if (!menuSelected) {
            drawCircleIcons(uiMvpMatrix, iconTextureIds)
        } else if (anchor != null) {
            drawMenuIcon()
            if (selection == 0) {
                textRenderer.drawBigText()
            } else if (selection == 1) {
                menuSelected != menuSelected
                //닫기
            } else if (selection == 2) {
                imageRenderer.draw()
            } else if (selection == 3) {
                videoRenderer.draw(anchorMatrix, viewMatrix, projectionMatrix)
            }
        }
        if(currentPendingDirection != null) {
            val (x, y) = getCoordFromDirection()
            drawCircularProgress(x, y)
        }
    }

    private fun drawScene(projectionMatrix: FloatArray) {
        val texCoords = floatArrayOf(
            1f, 0f,
            0f, 0f,
            1f, 1f,
            0f, 1f
        )
        val vertexBuffer = GlUtil.createFloatBuffer(quadCoords)
        val texBuffer = GlUtil.createFloatBuffer(texCoords)

        GLES20.glUseProgram(shaderProgram)

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoord")
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_Texture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawCircleIcons(
        mvpMatrix: FloatArray,
        iconTextureIds: IntArray
    ) {
        GLES20.glUseProgram(menuShaderProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val squareCoords = floatArrayOf(
            -0.5f,  0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            0.5f,  0.5f, 0f
        )

        val texCoords = floatArrayOf(
            0f, 0f,
            0f, 1f,
            1f, 1f,
            1f, 0f
        )

        val vertexBuffer = GlUtil.createFloatBuffer(squareCoords)
        val texBuffer = GlUtil.createFloatBuffer(texCoords)

        val posHandle = GLES20.glGetAttribLocation(menuShaderProgram, "a_Position")
        val texHandle = GLES20.glGetAttribLocation(menuShaderProgram, "a_TexCoord")
        val mvpHandle = GLES20.glGetUniformLocation(menuShaderProgram, "uMVPMatrix")
        val texSamplerHandle = GLES20.glGetUniformLocation(menuShaderProgram, "u_Texture")

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        // 중심에서의 오프셋 거리
        val offset = 0.5f

        // 십자 모양으로 배치할 4개의 위치 정의 (상, 하, 좌, 우)
        val iconPositions = listOf(
            Pair(0f, offset),    // 상
            Pair(0f, -offset),   // 하
            Pair(-offset, 0f),   // 좌
            Pair(offset, 0f)     // 우
        )

        val totalIcons = minOf(iconTextureIds.size, iconPositions.size)

        var selectedX = 0f
        var selectedY = 0f
        var selected = false

        for (i in 0 until totalIcons) {
            val (x, y) = iconPositions[i]

            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, x, y, 0f)
            Matrix.scaleM(modelMatrix, 0, 0.2f, 0.2f, 1f)

            val finalMatrix = FloatArray(16)
            Matrix.multiplyMM(finalMatrix, 0, mvpMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, finalMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconTextureIds[i])
            GLES20.glUniform1i(texSamplerHandle, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
            Log.d("TextureDebug", "Drawing icon $i at ($x, $y) with texture ID: ${iconTextureIds[i]}")

        }

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)

        for (i in 0 until totalIcons) {
            val (x, y) = iconPositions[i]
            drawCircleBackground(x, y)
        }
        /*
        if (selected) {
            Log.d("selected icons", "Selection: $selection, X: $selectedX, Y: $selectedY")
            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, selectedX, selectedY, 0.01f)
            Matrix.scaleM(modelMatrix, 0, 0.26f, 0.26f, 1f)

            val finalMatrix = FloatArray(16)
            Matrix.multiplyMM(finalMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

            val circleCoords = FloatArray(362 * 3)
            for (i in 0..361) {
                val angle = Math.toRadians(i.toDouble())
                circleCoords[i * 3] = cos(angle).toFloat()
                circleCoords[i * 3 + 1] = sin(angle).toFloat()
                circleCoords[i * 3 + 2] = 0f
            }

            val circleBuffer = GlUtil.createFloatBuffer(circleCoords)

            GLES20.glUseProgram(circleHighlight)

            val posHandle = GLES20.glGetAttribLocation(circleHighlight, "a_Position")
            val mvpHandle = GLES20.glGetUniformLocation(circleHighlight, "uMVPMatrix")
            val colorHandle = GLES20.glGetUniformLocation(circleHighlight, "u_Color")

            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, circleBuffer)
            GLES20.glEnableVertexAttribArray(posHandle)

            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, finalMatrix, 0)
            GLES20.glUniform4f(colorHandle, 1f, 1f, 0f, 1f)

            GLES20.glLineWidth(3f)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 362)

            GLES20.glDisableVertexAttribArray(posHandle)
        }*/
    }

    private fun drawCircleBackground(centerX: Float, centerY: Float) {
        val radius = 0.1f
        val segments = 60

        val coords = FloatArray((segments + 2) * 3)  // (x, y, z) * (segments + center + last point)
        coords[0] = centerX
        coords[1] = centerY
        coords[2] = 0f

        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i.toDouble() / segments
            val x = (Math.cos(angle) * radius).toFloat()
            val y = (Math.sin(angle) * radius).toFloat()

            coords[(i + 1) * 3] = centerX + x
            coords[(i + 1) * 3 + 1] = centerY + y
            coords[(i + 1) * 3 + 2] = 0f
        }

        val buffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(coords).position(0)

        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)

        val posHandle = GLES20.glGetAttribLocation(circleHighlight, "a_Position")
        val mvpHandle = GLES20.glGetUniformLocation(circleHighlight, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(circleHighlight, "u_Color")

        GLES20.glUseProgram(circleHighlight)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 0.1f)  // 흰색 반투명 (alpha = 0.5)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, segments + 2)

        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawMenuIcon() {
        GLES20.glUseProgram(menuIconProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val squareCoords = floatArrayOf(
            -0.5f,  0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            0.5f,  0.5f, 0f
        )
        val texCoords = floatArrayOf(
            0f, 0f,
            0f, 1f,
            1f, 1f,
            1f, 0f
        )
        val vertexBuffer = GlUtil.createFloatBuffer(squareCoords)
        val texBuffer = GlUtil.createFloatBuffer(texCoords)

        val posHandle = GLES20.glGetAttribLocation(menuIconProgram, "a_Position")
        val texHandle = GLES20.glGetAttribLocation(menuIconProgram, "a_TexCoord")
        val mvpHandle = GLES20.glGetUniformLocation(menuIconProgram, "uMVPMatrix")
        val texSamplerHandle = GLES20.glGetUniformLocation(menuIconProgram, "u_Texture")

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        val projectionMatrix = FloatArray(16)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0.8f, 0.8f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.2f, 0.2f, 1f)

        val finalMatrix = FloatArray(16)
        Matrix.multiplyMM(finalMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, finalMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, menuIconTexture)
        GLES20.glUniform1i(texSamplerHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun drawCircularProgress(xOffset: Float, yOffset: Float) {
        val sweepAngle = 360f * pendingProgress.coerceIn(0f, 1f)
        val radius = 0.1f
        val segments = sweepAngle.toInt().coerceAtLeast(1)
        val vertexCount = segments + 2 // center + sweep

        val vertices = FloatArray(vertexCount * 3)

        // 중심점
        vertices[0] = 0f
        vertices[1] = 0f
        vertices[2] = 0f

        for (i in 0..segments) {
            val angle = Math.toRadians(i.toDouble())
            val x = (cos(angle) * radius).toFloat()
            val y = (sin(angle) * radius).toFloat()
            vertices[(i + 1) * 3 + 0] = x
            vertices[(i + 1) * 3 + 1] = y
            vertices[(i + 1) * 3 + 2] = 0f
        }

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(vertices).position(0)

        // 행렬 생성
        val modelMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, xOffset, yOffset, 0f)

        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(circleProgram)

        val positionHandle = GLES20.glGetAttribLocation(circleProgram, "a_Position")
        val colorHandle = GLES20.glGetUniformLocation(circleProgram, "u_Color")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(circleProgram, "uMVPMatrix")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 1f) // 하얀색
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
    }


    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.start()
        }
    }

    fun skipForward() {
        val newPosition = mediaPlayer.currentPosition + 10000
        mediaPlayer.seekTo(newPosition)
    }

    fun skipBackward() {
        val newPosition = mediaPlayer.currentPosition - 10000
        mediaPlayer.seekTo(newPosition.coerceAtLeast(0))
    }

        fun loadVideoUri(context: Context, maxCount: Int? = null) {
            videoListUri = VideoRenderer.loadGalleryMP4(context, maxCount)
            videoUri = videoListUri!![0]
        }

        fun setVideoUri(context: Context, uri: Uri) {
            Log.d("setvideoUri", "starting uri")

            if (!::mediaPlayer.isInitialized) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer.apply {
                setSurface(Surface(videoTexture))
                setDataSource(context, uri)
                isLooping = true
                prepare()
            }

            Log.d("setvideoUri", "ending uri")
        }

        fun loadTexture(context: Context, resourceId: Int): Int {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)

            if (textureIds[0] == 0) {
                throw RuntimeException("Error generating texture ID")
            }

            val options = BitmapFactory.Options()
            options.inScaled = false // no pre-scaling

            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
                ?: throw RuntimeException("Error loading bitmap")

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()

            return textureIds[0]
        }

        enum class Direction {
            LEFT_UP, UP, RIGHT_UP, LEFT, RIGHT, LEFT_DOWN, DOWN, RIGHT_DOWN, BLINK
        }

        fun sendDirection(direction: EOGManager.Direction) {
            if (direction == EOGManager.Direction.LEFT_UP && messageIn == true) {
                startPending(direction) {
                    messageIn = false
                    menuSelected = true
                    selection = 0
                }
                // 문자 메시지 알림이 잇을 시에 바로 키기
            }
            if (!menuSelected) {
                if (direction != EOGManager.Direction.LEFT &&
                    direction != EOGManager.Direction.RIGHT &&
                    direction != EOGManager.Direction.UP &&
                    direction != EOGManager.Direction.DOWN) {
                    if(pendingSelectionJob?.isActive == true){
                        pendingSelectionJob?.cancel()
                        currentPendingDirection = null
                        pendingProgress = 0f
                    }
                    return
                }
                startPending(direction) {
                    when (direction) {
                        EOGManager.Direction.LEFT -> {
                            selection = 2
                            menuSelected = true
                            //갤러리
                        }

                        EOGManager.Direction.RIGHT -> {
                            selection = 3
                            menuSelected = true
                            //영상
                        }

                        EOGManager.Direction.UP -> {
                            selection = 0
                            menuSelected = true
                            //문자
                        }

                        EOGManager.Direction.DOWN -> {
                            selection = 1
                            menuSelected = true
                            //menuSelected = !menuSelected
                        }

                        else -> {

                        }
                    }
                }
            } else {
                if (pendingSelectionJob?.isActive == true) {
                    pendingSelectionJob?.cancel()
                    currentPendingDirection = null
                    pendingProgress = 0f
                    return
                }
                when (selection) {
                    0 -> {
                        when (direction) {
                            EOGManager.Direction.LEFT -> {
                                activity.runOnUiThread {
                                    speechController.startListening()
                                }
                                //STT
                            }
                            EOGManager.Direction.DOWN -> {
                                textRenderer.backwardMessage()
                                //문자 뒤로
                            }
                            EOGManager.Direction.RIGHT -> {
                                sendMessage()
                                //문자 보내기
                            }
                            EOGManager.Direction.UP -> {
                                textRenderer.forwardMessage()
                                //문자 앞으로
                            }
                            EOGManager.Direction.RIGHT_UP -> {
                                startPending(direction) {
                                    selection = 1
                                    menuSelected = false
                                }
                            }
                            else -> {

                            }
                        }
                        //문자
                    }
                    1 -> {
                        startPending(EOGManager.Direction.RIGHT_UP) {
                            menuSelected = false
                            return@startPending
                        }
                    }
                    2 -> {
                        when (direction) {
                            EOGManager.Direction.LEFT -> {
                                imageRenderer.previousImage()
                                //사진 뒤로
                            }
                            EOGManager.Direction.RIGHT -> {
                                imageRenderer.nextImage()
                                //사진 앞으로
                            }
                            EOGManager.Direction.RIGHT_UP -> {
                                startPending(direction) {
                                    selection = 1
                                    menuSelected = false
                                }
                            }
                            else -> {

                            }
                        }
                        //갤러리
                    }
                    3 -> {
                        when (direction) {
                            EOGManager.Direction.LEFT -> {
                                changeVideoUri(EOGManager.Direction.LEFT)
                                //영상 뒤로
                            }
                            EOGManager.Direction.RIGHT -> {
                                changeVideoUri(EOGManager.Direction.RIGHT)
                                //영상 앞으로
                            }
                            EOGManager.Direction.DOWN -> {
                                togglePlayPause()
                            }
                            EOGManager.Direction.UP -> {
                            }
                            EOGManager.Direction.RIGHT_UP -> {
                                startPending(direction) {
                                    selection = 1
                                    menuSelected = false
                                }
                            }
                            else -> {

                            }
                        }
                        //영상
                    }
                    else -> {

                    }
                }
            }
        }

    private fun startPending(direction: EOGManager.Direction, onConfirm: () -> Unit) {
        //Log.d("startPending", "Direction: $currentPendingDirection, Active: ${pendingSelectionJob?.isActive}")
        if (pendingSelectionJob?.isActive == true) {
            pendingSelectionJob?.cancel()
            currentPendingDirection = null
            pendingProgress = 0f
            return
        }

        pendingSelectionJob?.cancel()
        currentPendingDirection = direction
        pendingProgress = 0f

        pendingSelectionJob = CoroutineScope(Dispatchers.Main).launch {
            val duration = 3000L
            val interval = 50L
            var elapsed = 0L

            while (elapsed < duration) {
                delay(interval)
                elapsed += interval
                pendingProgress = elapsed.toFloat() / duration
            }

            onConfirm()
            pendingProgress = 0f
            currentPendingDirection = null
        }
    }

    private fun getCoordFromDirection(): Pair<Float, Float> {
        return when (currentPendingDirection) {
            EOGManager.Direction.UP -> Pair(0f, 0.5f)
            EOGManager.Direction.DOWN -> Pair(0f, -0.5f)
            EOGManager.Direction.LEFT -> Pair(-0.5f, 0f)
            EOGManager.Direction.RIGHT -> Pair(0.5f, 0f)
            EOGManager.Direction.RIGHT_UP -> Pair(0.8f, 0.8f)
            EOGManager.Direction.LEFT_UP -> Pair(-0.8f, 0.8f)
            else -> Pair(0f, 0f)
        }
    }

        fun setSelection(direction: String) {
            selection = when (direction) {
                "LEFT" -> if (selection == 0) 3 else selection - 1
                "RIGHT" -> if (selection == 3) 0 else selection + 1
                else -> selection
            }
        }

        fun getSTT(text: String) {
            textRenderer.setSTT(text)
        }

        fun selectMenu() {
            menuSelected = !menuSelected
        }

        fun sendMessage() {
            textRenderer.sendMessage(directedUserId)
        }

    override fun onWebSocketMessage() {
        playNotificationSound()

        if(selection != 0)
            messageIn = true
        if(!menuSelected)
            messageIn = true
    }

    private fun playNotificationSound() {
        val mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener {
            it.release()
        }
    }

    fun changeVideoUri(direction: EOGManager.Direction) {
        val currentIndex = videoListUri?.indexOf(videoUri)
        if (direction == EOGManager.Direction.LEFT) {//이전 영상으로
            if (currentIndex != null) {
                videoUri = if (currentIndex > 0) {
                    videoListUri?.get(currentIndex - 1)
                } else {
                    videoListUri!!.last()
                }
            }
            setVideoUri(context, videoUri!!)
            togglePlayPause()
            togglePlayPause()
            videoRenderer.loadTextureThumbnail(videoListUri!!, videoUri!!)
            return
        }

        if (direction == EOGManager.Direction.RIGHT) { //다음 영상으로
            if (currentIndex != null) {
                videoUri = if (currentIndex < videoListUri!!.lastIndex) {
                    videoListUri?.get(currentIndex + 1)
                } else {
                    videoListUri!!.first()
                }
            }
            setVideoUri(context, videoUri!!)
            togglePlayPause()
            togglePlayPause()
            videoRenderer.loadTextureThumbnail(videoListUri!!, videoUri!!)
            return
        }
    }

    fun setServerUrl(url: String) {
        serverUrl = url
    }

    fun textSetListening(stt: Boolean) {
        textRenderer.setMic(stt)
    }
}