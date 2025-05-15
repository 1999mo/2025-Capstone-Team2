package com.example.capstone_2

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VideoRenderer(context: Context) {
    private val planeVertices: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureHandle = 0
    private var textureId = -1

    init {
        val coords = floatArrayOf(
            -0.5f, 0f, -0.5f, 0f, 1f,
            0.5f, 0f, -0.5f, 1f, 1f,
            -0.5f, 0f,  0.5f, 0f, 0f,
            0.5f, 0f,  0.5f, 1f, 0f,
        )
        planeVertices = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        planeVertices.put(coords)
        planeVertices.position(0)

        val vertexShaderCode = """
            attribute vec3 a_Position;
            attribute vec2 a_TexCoord;
            uniform mat4 u_MVPMatrix;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
                v_TexCoord = a_TexCoord;
            }
        """

        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Get attribute/uniform locations
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
    }

    fun setVideoTexture(textureId: Int) {
        this.textureId = textureId
    }

    fun draw(
        anchorMatrix: FloatArray,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (textureId == -1) return

        GLES20.glUseProgram(program)

        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, anchorMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        planeVertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 20, planeVertices)
        GLES20.glEnableVertexAttribArray(positionHandle)

        planeVertices.position(3)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 20, planeVertices)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $error")
        }

        return shader
    }
}