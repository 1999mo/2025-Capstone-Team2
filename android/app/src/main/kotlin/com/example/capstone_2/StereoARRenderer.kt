package com.example.capstone_2

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Session
import com.google.ar.core.Frame
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class StereoARRenderer(private val session: Session) : GLSurfaceView.Renderer {

    private var screenWidth = 0
    private var screenHeight = 0
    private var shaderProgram: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var positionHandle: Int = 0

    private val leftEyeMatrix = FloatArray(16)
    private val rightEyeMatrix = FloatArray(16)
    private val vertices = floatArrayOf(
        0f, 0.5f,   // 상단
        -0.5f, -0.5f,  // 왼쪽 하단
        0.5f, -0.5f   // 오른쪽 하단
    )
    private val vertexBuffer = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertices)
        .position(0)
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

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
            attribute vec4 vPosition;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
            }
        """

        val fragmentShaderCode = """
            precision mediump float;
            void main() {
                gl_FragColor = vec4(1.0, 1.0, 0.0, 1.0);  // 노란색
            }
        """

        shaderProgram = ShaderUtil.createProgram(vertexShaderCode, fragmentShaderCode)

        GLES20.glUseProgram(shaderProgram)

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // ARCore 카메라 프레임 업데이트
        val camera = session.update()
        val halfWidth = screenWidth / 2

        // 왼쪽 눈 뷰포트
        GLES20.glViewport(0, 0, halfWidth, screenHeight)
        drawScene(leftEyeMatrix)

        // 오른쪽 눈 뷰포트
        GLES20.glViewport(halfWidth, 0, halfWidth, screenHeight)
        drawScene(rightEyeMatrix)
    }

    private fun drawScene(projectionMatrix: FloatArray) {
        // 셰이더의 MVP 행렬 업데이트
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, projectionMatrix, 0)

        // 정점 데이터를 셰이더로 전송
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        // 삼각형 그리기
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 3)
    }
}