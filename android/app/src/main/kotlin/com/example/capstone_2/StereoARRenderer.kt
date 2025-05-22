package com.example.capstone_2

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Session
import com.google.ar.core.Frame
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.google.ar.core.Pose
import android.content.Context
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.util.Log
import android.opengl.GLES11Ext
import android.graphics.SurfaceTexture
import android.view.WindowManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import com.example.capstone_2.OtherUtil.crossProduct
import com.example.capstone_2.OtherUtil.normalize
import com.google.ar.core.Plane
import com.google.ar.core.exceptions.NotYetAvailableException
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import com.example.eogmodule.EOGManager
import kotlin.math.*

class StereoARRenderer(
    private val session: Session,
    private val context: Context,
) : GLSurfaceView.Renderer {

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
    //private var cameraTextureId: Int = -1
    private var oesTextureId: Int = -1
    private lateinit var videoRenderer: VideoRenderer
    private var selection: Int = 0

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
    private var videoUri: Uri? = null
    lateinit var iconTextureIds: IntArray
    lateinit var textRenderer: TextRenderer
    var currentText: String = "Null"

    val menuSelected = false

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

        videoUri?.let { uri ->
            setVideoUri(context, uri)
        }

        /*
        mediaPlayer = MediaPlayer()
        val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.my_video}")
        mediaPlayer.setDataSource(context, videoUri)
        mediaPlayer.setSurface(Surface(videoTexture))
        mediaPlayer.isLooping = true
        mediaPlayer.prepare()
        mediaPlayer.start()
        */

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

        GLES20.glUseProgram(shaderProgram)

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

        iconTextureIds = IntArray(8)
        iconTextureIds[0] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[1] = loadTexture(context, R.drawable.icon2)
        iconTextureIds[2] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[3] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[4] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[5] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[6] = loadTexture(context, R.drawable.icon1)
        iconTextureIds[7] = loadTexture(context, R.drawable.icon1)

        textRenderer = TextRenderer()
        textRenderer.updateText("Hello World")

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


        /*
        Log.d("Render", "${camera.trackingState}, this the tracking state")

        Log.d("Render", "${updatedPlanes}, these are the planes")
        for (plane in updatedPlanes) {
            Log.d("Render", "Trying Anchor creation")
            if (plane.trackingState == TrackingState.TRACKING &&
                plane.type == Plane.Type.VERTICAL &&
                plane.isPoseInPolygon(frame.camera.pose)
            ) {
                if (anchor == null) {
                    val hitPose = plane.centerPose
                    anchor = session.createAnchor(hitPose)
                    Log.d("AR", "✅ 벽(수직 평면)에 앵커 생성됨!")
                }
            }
        }
        */

        Log.d("Render", "Trying Anchor creation")
        Log.d("Render", "Tracking: ${camera.trackingState}, Anchor: $anchor")
        if (camera.trackingState == TrackingState.TRACKING && anchor == null) {
            val cameraPose = camera.displayOrientedPose
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
            Log.d("Render", "Anchor created successfully")
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
        if (menuSelected) {
            drawCircleIcons(uiMvpMatrix, iconTextureIds)
        }
        if (anchor != null) {
            if (selection == 0) {
                textRenderer.drawTextLabel(anchorMatrix, viewMatrix, projectionMatrix)
            } else if (selection == 1) {
                videoRenderer.draw(anchorMatrix, viewMatrix, projectionMatrix)
            }
        }

        GLES20.glViewport(screenWidth - eyeWidth - eyeGap, 0, eyeWidth, screenHeight)
        drawScene(rightEyeMatrix)
        if (menuSelected) {
            drawCircleIcons(uiMvpMatrix, iconTextureIds)
        }
        if (anchor != null) {
            if (selection == 0) {
                textRenderer.drawTextLabel(anchorMatrix, viewMatrix, projectionMatrix)
            } else if (selection == 1) {
                videoRenderer.draw(anchorMatrix, viewMatrix, projectionMatrix)
            }
        }
    }

    private fun drawScene(projectionMatrix: FloatArray) {
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

    /*
    private fun drawPlane(
        anchorMatrix: FloatArray,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        val quadCoords = floatArrayOf(
            -1.0f, 0f, -0.5f,
            1.0f, 0f, -0.5f,
            -1.0f, 0f,  0.5f,
            1.0f, 0f,  0.5f
        )

        val texCoords = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
        val vertexBuffer = GlUtil.createFloatBuffer(quadCoords)
        val texBuffer = GlUtil.createFloatBuffer(texCoords)
        //val color = floatArrayOf(1f, 1f, 1f, 1f)

        val positionHandle = GLES20.glGetAttribLocation(planeShaderProgram, "a_Position")
        val texHandle = GLES20.glGetAttribLocation(planeShaderProgram, "a_TexCoord")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(planeShaderProgram, "uMVPMatrix")
        val texSamplerHandle = GLES20.glGetUniformLocation(planeShaderProgram, "u_texture")

        val depthTexHandle = GLES20.glGetUniformLocation(planeShaderProgram, "u_DepthTexture")
        val modelViewMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, anchorMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        //GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        GLES20.glUseProgram(planeShaderProgram)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        //GLES20.glUniform4fv(colorHandle, 1, color, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glUniform1i(texSamplerHandle,  0)

        /*
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTextureId[0])
        GLES20.glUniform1i(depthTexHandle, 1)
        */

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("ARCore", "OpenGL error: $error")
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }*/

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

        val columns = 4
        val rows = 2
        val spacingX = 0.4f
        val spacingY = 0.4f

        val startX = -((columns - 1) * spacingX) / 2f
        val startY = ((rows - 1) * spacingY) / 2f

        val totalIcons = minOf(iconTextureIds.size, columns * rows)

        var selectedX = 0f
        var selectedY = 0f
        var selected = false

        for (i in 0 until totalIcons) {
            val col = i % columns
            val row = i / columns

            val x = startX + col * spacingX
            val y = startY - row * spacingY

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

            if (i == selection) {
                selectedX = x
                selectedY = y
                selected = true
            }
        }

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)

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
        }
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

    fun setVideoUri(context: Context, uri: Uri) {
        Log.d("setvideoUri", "starting uri")
        videoUri = uri

        if(!::mediaPlayer.isInitialized) {
            mediaPlayer = MediaPlayer()
        } else {
            mediaPlayer?.reset()
        }

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            setSurface(Surface(videoTexture))
            isLooping = true
            prepare()
            start()
        }
        Log.d("setvideoUri", "ending uri")
    }

    fun moveAnchor(dx: Float, dy: Float, dz: Float) {
        val currentPose = anchor?.pose ?: return
        val newPose = currentPose.compose(Pose.makeTranslation(dx, dy, dz))

        anchor?.detach()

        anchor = session.createAnchor(newPose)
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
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        return textureIds[0]
    }

    fun setSelection(direction: String) {
        selection = when (direction) {
            "LEFT" -> if (selection == 0) 7 else selection - 1
            "RIGHT" -> if (selection == 7) 0 else selection + 1
            else -> selection
        }
    }

    fun getSTT(text: String) {
        currentText = text
        textRenderer.updateText(currentText)
    }

    fun selectMenu() {
        menuSelected = !menuSlected
    }
}