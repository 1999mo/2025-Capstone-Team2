package com.example.capstone_2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.FloatBuffer
import android.content.Context
import android.net.Uri
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import com.google.gson.*

class TextRenderer {
    private var shaderProgram = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureHandle = 0

    private var textTextureId = -1

    private val vertexBuffer: FloatBuffer
    private val texBuffer: FloatBuffer

    private val squareCoords = floatArrayOf(
        -0.5f,  0.5f, 0f,
        -0.5f, -0.5f, 0f,
        0.5f,  0.5f, 0f,
        0.5f, -0.5f, 0f
    )

    private val texCoords = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )

    private val gson = Gson()
    private lateinit var client: WebSocketClient
    private var text = "Null"

    init {
        vertexBuffer = GlUtil.createFloatBuffer(squareCoords)
        texBuffer = GlUtil.createFloatBuffer(texCoords)

        val vertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = uMVPMatrix * a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            uniform sampler2D u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """.trimIndent()

        shaderProgram = createProgram(vertexShaderCode, fragmentShaderCode)
    }

    fun updateText() {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 48f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
        }

        val maxWidth = 512f
        val textWidth = paint.measureText(text).toInt()
        val lines = mutableListOf<String>()
        var currentLine = ""
        val maxCharsPerLine = 20

        for (word in text.split(" ")) {
            if (paint.measureText("$currentLine $word") < maxWidth) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        lines.add(currentLine)

        val textHeight = lines.size * paint.textSize.toInt()
        val bitmap = Bitmap.createBitmap(maxWidth.toInt(), textHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.argb(255, 255, 255, 255))
        //canvas.drawText(text, padding.toFloat(), -paint.fontMetrics.ascent + padding, paint)

        var yOffset = 0f
        for (line in lines) {
            canvas.drawText(line, 20f, yOffset + paint.textSize, paint)
            yOffset += paint.textSize
        }

        if (textTextureId == -1) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textTextureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId)
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    fun drawTextLabel(anchorMatrix: FloatArray, viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        val modelViewMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, anchorMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        GLES20.glUseProgram(shaderProgram)

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_Texture")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId)
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

    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $error")
        }

        return program
    }

    fun connect() {
        val serverUri = ""

        val uri = URI(serverUri)

        client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                println("WebSocket connected")
            }

            override fun onMessage(message: String?) {
                println("Received: $message")
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                println("WebSocket closed: $reason")
            }

            override fun onError(ex: Exception?) {
                println("WebSocket error: ${ex?.message}")
            }
        }

        client.connect()
    }

    fun sendMessage(toId: String, message: String) {
        if (::client.isInitialized && client.isOpen) {
            //val msg = ChatMessage(fromId = myId, toId = toId, message = message)
            //val json = gson.toJson(msg)
            //client.send(json)
        } else {
            println("WebSocket not connected")
        }
    }

    fun close() {
        if (::client.isInitialized) {
            client.close()
        }
    }
}
