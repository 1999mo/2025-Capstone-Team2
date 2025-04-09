package com.example.capstone_2

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Session
import com.google.ar.core.Frame
import android.content.Context
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.util.Log
import android.opengl.GLES11Ext
import android.graphics.SurfaceTexture
import android.view.WindowManager
import com.example.capstone_2.GlUtil.createFloatBuffer

class StereoARRenderer(
    private val session: Session,
    private val context: Context
) : GLSurfaceView.Renderer {

    private var screenWidth = 0
    private var screenHeight = 0
    private var shaderProgram: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var positionHandle: Int = 0
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
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d("Render", "onSurfaceCreated")
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        oesTextureId = GlUtil.createOESTexture()
        //cameraTextureId = createCameraTexture()
        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture.setOnFrameAvailableListener {
            // 프레임 새로 도착했을 때 콜백
        }
        session.setCameraTextureName(oesTextureId)

        // 기본 projection 설정
        val projectionMatrix = FloatArray(16)
        Matrix.frustumM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 100f)

        // 왼쪽 눈 matrix
        System.arraycopy(projectionMatrix, 0, leftEyeMatrix, 0, 16)
        Matrix.translateM(leftEyeMatrix, 0, -0.03f, 0f, 0f)

        // 오른쪽 눈 matrix
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
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        shaderProgram = ShaderUtil.createProgram(vertexShaderCode, fragmentShaderCode)

        GLES20.glUseProgram(shaderProgram)

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
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

        // ARCore 카메라 프레임 업데이트
        session.update()
        surfaceTexture.updateTexImage()
        val halfWidth = screenWidth / 2

        // 왼쪽 눈 뷰포트
        GLES20.glViewport(0, 0, halfWidth, screenHeight)
        drawScene(leftEyeMatrix)

        // 오른쪽 눈 뷰포트
        GLES20.glViewport(halfWidth, 0, halfWidth, screenHeight)
        drawScene(rightEyeMatrix)
    }

    private fun drawScene(projectionMatrix: FloatArray) {
        val vertexBuffer = GlUtil.createFloatBuffer(quadCoords)
        val texBuffer = GlUtil.createFloatBuffer(texCoords)

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
}