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

class StereoARRenderer(
    private val session: Session,
    private val context: Context
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
    //private var cameraTextureId: Int = -1
    private var oesTextureId: Int = -1

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
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d("Render", "onSurfaceCreated")
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

        mediaPlayer = MediaPlayer()
        val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.my_video}")
        mediaPlayer.setDataSource(context, videoUri)
        mediaPlayer.setSurface(Surface(videoTexture))
        mediaPlayer.isLooping = true
        mediaPlayer.prepare()
        mediaPlayer.start()

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
            varying vec2 v_TexCoord;
            
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        planeShaderProgram = ShaderUtil.createProgram(planeVertexShaderCode, planeFragmentShaderCode)
        planePositionHandle = GLES20.glGetAttribLocation(planeShaderProgram, "a_Position")
        planeMvpMatrixHandle = GLES20.glGetUniformLocation(planeShaderProgram, "uMVPMatrix")
        planeColorHandle = GLES20.glGetUniformLocation(planeShaderProgram, "u_Color")
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
        val camera = frame.camera
        //al depthImage = frame.acquireDepthImage()
        surfaceTexture.updateTexImage()
        videoTexture.updateTexImage()


        Log.d("Render", "Trying Anchor creation")
        Log.d("Render", "Tracking: ${camera.trackingState}, Anchor: $anchor")
        if (camera.trackingState == TrackingState.TRACKING && anchor == null) {
            val cameraPose = camera.displayOrientedPose
            val forward = floatArrayOf(0f, 0f, -2f)
            val up = floatArrayOf(0f, 1f, 0f)
            //val out = FloatArray(3)

            val rotatedForward = FloatArray(3)
            cameraPose.rotateVector(forward, 0, rotatedForward, 0)

            val rotationQuat = OtherUtil.makeQuaternionLookRotation(rotatedForward, up)

            val position = floatArrayOf(
                cameraPose.tx() + rotatedForward[0],
                cameraPose.ty() + rotatedForward[1],
                cameraPose.tz() + rotatedForward[2]
            )
            //val rotationPose = Pose.makeRotationFromVectors(forward = out, up = up)
            val anchorPose = Pose(position, rotationQuat)

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

        GLES20.glViewport(eyeGap, 0, eyeWidth, screenHeight)
        drawScene(leftEyeMatrix)
        if (camera.trackingState == TrackingState.TRACKING && anchor != null) {
            drawPlane(anchorMatrix, viewMatrix, projectionMatrix)
        }

        GLES20.glViewport(screenWidth - eyeWidth - eyeGap, 0, eyeWidth, screenHeight)
        drawScene(rightEyeMatrix)
        if (camera.trackingState == TrackingState.TRACKING && anchor != null) {
            drawPlane(anchorMatrix, viewMatrix, projectionMatrix)
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

    private fun drawPlane(
        anchorMatrix: FloatArray,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        val quadCoords = floatArrayOf(
            -0.5f, 0f, -0.5f,
            0.5f, 0f, -0.5f,
            -0.5f, 0f,  0.5f,
            0.5f, 0f,  0.5f
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

        val modelViewMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, anchorMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

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

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("ARCore", "OpenGL error: $error")
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }
}