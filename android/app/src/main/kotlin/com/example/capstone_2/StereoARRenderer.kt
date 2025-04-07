package com.example.capstone_2

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class StereoARRenderer(private val session: Session) : GLSurfaceView.Renderer {

    private var screenWidth = 0
    private var screenHeight = 0

    private val leftEyeMatrix = FloatArray(16)
    private val rightEyeMatrix = FloatArray(16)

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
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val halfWidth = screenWidth / 2

        // 왼쪽 눈 뷰포트
        GLES20.glViewport(0, 0, halfWidth, screenHeight)
        drawScene(leftEyeMatrix)

        // 오른쪽 눈 뷰포트
        GLES20.glViewport(halfWidth, 0, halfWidth, screenHeight)
        drawScene(rightEyeMatrix)
    }

    private fun drawScene(projectionMatrix: FloatArray) {
        // 실제 오브젝트 그리기 전에, ARCore Frame을 가져오고 처리하는 로직 필요
        // 예시로는 생략합니다. 여기에 drawVirtualObject(projectionMatrix) 같은 메서드 작성 가능.
    }
}
