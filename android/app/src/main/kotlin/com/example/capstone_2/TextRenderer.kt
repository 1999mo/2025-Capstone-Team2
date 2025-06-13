package com.example.capstone_2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.FloatBuffer
import android.net.Uri
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import com.google.gson.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.json.JSONException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context

class TextRenderer(
    private val context: Context
) {
    private var shaderProgram = 0
    private var hudShader = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureHandle = 0

    private var textTextureId = -1

    private var vertexBuffer: FloatBuffer
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
    private var sttText = ""
    private var textSizeFloat = 64f
    private var textureAspectRatio = 1.0f
    private var messageInText: String? = null
    private var messageInTextureId = -1
    private var messageInAspectRatio = 1.0f

    private var messageListener: WebSocketMessageListener? = null
    private var fromId = ""

    private lateinit var messageList: MutableList<ChatMessage>
    private var userId = "USER" //현재 휴대폰 id
    private var batchIndex = 0 //메시지 index
    private var batchSize = 5 //메세지 총 갯수

    private var iconTexture = IntArray(3)
    private var listening = false

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

        val hudVertexShaderCode = """
            attribute vec4 a_Position;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * a_Position;
            }
        """.trimIndent()

        val hudFragmentShaderCode = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """.trimIndent()

        shaderProgram = createProgram(vertexShaderCode, fragmentShaderCode)
        hudShader = createProgram(hudVertexShaderCode, hudFragmentShaderCode)

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_Texture")

        messageList = loadMessage(context).toMutableList()

        iconTexture[0] = loadTexture(context, R.drawable.mic)
        iconTexture[1] = loadTexture(context, R.drawable.icon3)
        iconTexture[2] = loadTexture(context, R.drawable.micn)
    }

    fun updateText(newText: String) {
        text = newText
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = textSizeFloat
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
        }

        val padding = 20
        val fixedHeight = 128
        val fm = paint.fontMetrics
        //val textHeight = (fm.descent - fm.ascent).toInt()

        val y = fixedHeight / 2f - (fm.ascent + fm.descent) / 2
        val textWidth = paint.measureText(text).toInt()

        val bitmapWidth = textWidth + padding * 2
        val bitmapHeight = fixedHeight

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)  // 배경 흰색
        canvas.drawText(text, padding.toFloat(), y, paint)

        textureAspectRatio = bitmapWidth.toFloat() / bitmapHeight

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

        Log.d("updateText", "Text updated: $text")
    }

    fun drawBigText() {
        drawBackground()

        val lineSpacing = 0.15f
        val startY = 0.45f
        val startX = 0f

        val maxMessagesToDraw = 5

        val messagesToDraw = getMessageBatch()
        //Log.d("drawBigText", "Index size: ${messagesToDraw.size}")

        for ((index, message) in messagesToDraw.withIndex()) {
            val displayText = "${message.fromId}: ${message.message}"
            //Log.d("drawBigText", "Lines: $displayText")

            updateText(displayText)
            val yPosition = startY - index * lineSpacing

            val alignRight = message.fromId == userId
            drawTextLabel(startX, yPosition, true, alignRight)
        }

        drawSTT()
        drawIcon()
    }

    private fun getMessageBatch(): List<ChatMessage> {
        val total = messageList.size
        val start = (total - (batchIndex + 1) * batchSize).coerceAtLeast(0)
        val end = (total - batchIndex * batchSize).coerceAtMost(total)
        return if (start < end) messageList.subList(start, end) else emptyList()
    }

    fun forwardMessage() {
        if ((batchIndex + 1) * batchSize < messageList.size) {
            batchIndex++
        }
    }

    fun backwardMessage() {
        if (batchIndex > 0) {
            batchIndex--
        }
    }

    fun drawTextLabel(x: Float, y: Float, alignment: Boolean = false, alignRight: Boolean = false) {
        val hudCoords = floatArrayOf(
            -0.5f,  0.5f, 0f,
            -0.5f, -0.5f, 0f,
            0.5f,  0.5f, 0f,
            0.5f, -0.5f, 0f
        )

        val projectionMatrix = FloatArray(16)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        val modelMatrix = FloatArray(16)
        val scale = 0.1f
        val scaleWidth = textureAspectRatio * scale
        val alignedX = if (alignment) {
            if (alignRight)
                0.8f - scaleWidth / 2
                else -0.8f + scaleWidth / 2
        }
        else 0f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, alignedX + x, y, 0f)
        Matrix.scaleM(modelMatrix, 0, textureAspectRatio * scale, 1f * scale, 1f)

        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(shaderProgram)

        val hudVertexBuffer = ByteBuffer.allocateDirect(hudCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        hudVertexBuffer.put(hudCoords).position(0)

        texBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, hudVertexBuffer)

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

    fun setSTT(newText: String) {
        sttText = newText
    }

    private fun drawSTT() {
        updateText(sttText)
        drawTextLabel(0f, -0.45f)
    }

    private fun drawBackground() {
        val coords = floatArrayOf(
            -0.8f,  0.5f, 0f,
            -0.8f, -0.5f, 0f,
            0.8f,  0.5f, 0f,
            0.8f, -0.5f, 0f
        )
        val buffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(coords).position(0)

        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        val posHandle = GLES20.glGetAttribLocation(hudShader, "a_Position")
        val mvpHandle = GLES20.glGetUniformLocation(hudShader, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(hudShader, "u_Color")

        GLES20.glUseProgram(hudShader)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 0.8f)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawMinibackGround(x: Float, y: Float) {
        val width = 0.2f
        val height = 0.2f

        val left = x - width / 2f
        val right = x + width / 2f
        val top = y + height / 2f
        val bottom = y - height / 2f

        val coords = floatArrayOf(
            left,  top,    0f,  // 좌상
            left,  bottom, 0f,  // 좌하
            right, bottom, 0f,  // 우하
            right, top,    0f   // 우상
        )
        val buffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(coords).position(0)

        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        val posHandle = GLES20.glGetAttribLocation(hudShader, "a_Position")
        val mvpHandle = GLES20.glGetUniformLocation(hudShader, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(hudShader, "u_Color")

        GLES20.glUseProgram(hudShader)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 0.8f)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    fun updateDrawInMessage() {
        messageInText = "$fromId 로부터 문자가 왔습니다"

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 36f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
        }

        val width = 512
        val height = 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.argb(0, 255, 255, 255)) // transparent
        messageInText?.let { canvas.drawText(it, 20f, 80f, paint) }

        messageInAspectRatio = width.toFloat() / height

        if (messageInTextureId == -1) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            messageInTextureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, messageInTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, messageInTextureId)
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        Log.d("updateDrawInMessage", "TextureID: $messageInTextureId")
    }

    fun setMic(stt: Boolean) {
        listening = stt
    }

    fun drawInMessage() {
        drawMinibackGround(-0.9f, 0.9f)

        val vertices = floatArrayOf(
            -1f,  1f, 0f,   // 왼쪽 위
            1f,  1f, 0f,   // 오른쪽 위
            -1f, -1f, 0f,   // 왼쪽 아래
            1f, -1f, 0f    // 오른쪽 아래
        )

        val textureCoords = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )

        val leftMatrix = FloatArray(16)
        Matrix.setIdentityM(leftMatrix, 0)
        Matrix.translateM(leftMatrix, 0, -0.9f, 0.9f, 0f)
        Matrix.scaleM(leftMatrix, 0, 0.1f, 0.1f, 1f)

        GLES20.glUseProgram(shaderProgram)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconTexture[1])
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, leftMatrix, 0)

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)

        val texBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuffer.put(textureCoords).position(0)

        // position attribute
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // texture coord attribute
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e("ShaderError", "Shader compile error:\n$error\nShader Code:\n$shaderCode")
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

    fun connect(serverUri: String) {
        //val ServerUri = ""

        val uri = URI(serverUri)
        Log.d("WebSocket", "Websocket connection starting at $serverUri")

            client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("WebSocket", "WebSocket connected")
            }

            override fun onMessage(message: String?) {
                Log.d("WebSocket", "Received: $message")
                message?.let {
                    try {
                        val jsonObject = JSONObject(it)
                        val receivedText = jsonObject.getString("message")
                        fromId = jsonObject.optString("fromId", "unknown")
                        //updateText(receivedText)
                        //updateDrawInMessage(fromId, receivedText)
                        messageListener?.onWebSocketMessage()
                        messageList.add(ChatMessage(fromId, userId, receivedText))
                        saveMessage(context, messageList)
                    } catch (e: JSONException) {
                        Log.e("WebSocket", "Invalid JSON: ${e.message}")
                    }
                    //메세지 수신
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d("WebSocket", "WebSocket closed: $reason")
            }

            override fun onError(ex: Exception?) {
                Log.d("WebSocket", "WebSocket error: ${ex?.message}")
            }
        }

        client.connect()
    }

    fun setWebSocketMessageListener(listener: WebSocketMessageListener) {
        this.messageListener = listener
    }

    data class ChatMessage(
        val fromId: String,
        val toId: String,
        val message: String
    )

    fun sendMessage(toId: String) {
        if (::client.isInitialized && client.isOpen && sttText != "") {
            val myId = "USER"
            val msg = ChatMessage(fromId = myId, toId = toId, message = sttText)
            val json = gson.toJson(msg)
            client.send(json)
            messageList.add(ChatMessage(myId, toId, sttText))
            saveMessage(context, messageList)
            setSTT("")
        } else {
            Log.d("WebSocket", "WebSocket not connected")
        }
    }

    fun close() {
        if (::client.isInitialized) {
            client.close()
        }
    }

    private fun saveMessage(context: Context, messages: List<ChatMessage>) {
        val json = Gson().toJson(messages)
        context.openFileOutput("messages.json", Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    private fun loadMessage(context: Context): List<ChatMessage> {
        return try {
            val json = context.openFileInput("messages.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
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

    private fun drawIcon() {
        val vertices = floatArrayOf(
            -1f,  1f, 0f,   // 왼쪽 위
            1f,  1f, 0f,   // 오른쪽 위
            -1f, -1f, 0f,   // 왼쪽 아래
            1f, -1f, 0f    // 오른쪽 아래
        )

        val textureCoords = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )

        val leftMatrix = FloatArray(16)
        Matrix.setIdentityM(leftMatrix, 0)
        Matrix.translateM(leftMatrix, 0, -0.9f, 0f, 0f)
        Matrix.scaleM(leftMatrix, 0, 0.1f, 0.1f, 1f)

        val rightMatrix = FloatArray(16)
        Matrix.setIdentityM(rightMatrix, 0)
        Matrix.translateM(rightMatrix, 0, 0.9f, 0f, 0f)
        Matrix.scaleM(rightMatrix, 0, 0.1f, 0.1f, 1f)

        GLES20.glUseProgram(shaderProgram)
        if (listening) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconTexture[2])
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconTexture[0])
        }
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, leftMatrix, 0)

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)

        val texBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuffer.put(textureCoords).position(0)

        // position attribute
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // texture coord attribute
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisableVertexAttribArray(positionHandle)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconTexture[1])
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, rightMatrix, 0)
        val vertexBufferR = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBufferR.put(vertices).position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBufferR)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        drawMinibackGround(-0.9f, 0f)
        drawMinibackGround(0.9f, 0f)
    }
}
