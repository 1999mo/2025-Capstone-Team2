package com.example.capstone_2

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentUris
import android.graphics.Bitmap
import android.opengl.GLUtils
import android.media.MediaMetadataRetriever
import kotlin.math.max
import android.util.Log
import android.opengl.GLES11Ext

class VideoRenderer(private val context: Context) {
    private val planeVertices: FloatBuffer
    private var program = 0
    private var thumbnailProgram = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureHandle = 0
    private var thumbnailPositionHandle = 0
    private var thumbnailTexCoordHandle = 0
    private var thumbnailMvpMatrixHandle = 0
    private var thumbnailTextureHandle = 0
    private var textureId = -1
    private var leftId = -1
    private var rightId = -1

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
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        val thumbnailFragmentShaderCode = """
            precision mediump float;
            uniform sampler2D u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        val thumbnailFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, thumbnailFragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        thumbnailProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, thumbnailFragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Get attribute/uniform locations
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")

        thumbnailPositionHandle = GLES20.glGetAttribLocation(thumbnailProgram, "a_Position")
        thumbnailTexCoordHandle = GLES20.glGetAttribLocation(thumbnailProgram, "a_TexCoord")
        thumbnailMvpMatrixHandle = GLES20.glGetUniformLocation(thumbnailProgram, "u_MVPMatrix")
        thumbnailTextureHandle = GLES20.glGetUniformLocation(thumbnailProgram, "u_Texture")
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

        val distance = 1.5f //화면 간 거리
        val zOffset = -0.3f //z축 밀기

        val translationLeft = FloatArray(16)
        Matrix.setIdentityM(translationLeft, 0)
        Matrix.translateM(translationLeft, 0, -distance, 0f, zOffset)

        val rotationLeft = FloatArray(16)
        Matrix.setRotateM(rotationLeft, 0, 120f, 0f, 0f, 1f)

        val modelLeft = FloatArray(16)
        Matrix.multiplyMM(modelLeft, 0, anchorMatrix, 0, translationLeft, 0)
        Matrix.multiplyMM(modelLeft, 0, modelLeft, 0, rotationLeft, 0)

        val mvpLeft = FloatArray(16)
        Matrix.multiplyMM(mvpLeft, 0, viewMatrix, 0, modelLeft, 0)
        Matrix.multiplyMM(mvpLeft, 0, projectionMatrix, 0, mvpLeft, 0)

        val translationRight = FloatArray(16)
        Matrix.setIdentityM(translationRight, 0)
        Matrix.translateM(translationRight, 0, distance, 0f, zOffset)

        val rotationRight = FloatArray(16)
        Matrix.setRotateM(rotationRight, 0, -120f, 0f, 0f, 1f)

        val modelRight = FloatArray(16)
        Matrix.multiplyMM(modelRight, 0, anchorMatrix, 0, translationRight, 0)
        Matrix.multiplyMM(modelRight, 0, modelRight, 0, rotationRight, 0)

        val mvpRight = FloatArray(16)
        Matrix.multiplyMM(mvpRight, 0, viewMatrix, 0, modelRight, 0)
        Matrix.multiplyMM(mvpRight, 0, projectionMatrix, 0, mvpRight, 0)

        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, anchorMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        //왼쪽 오른쪽 썸네일
        GLES20.glUseProgram(thumbnailProgram)

        planeVertices.position(0)
        GLES20.glVertexAttribPointer(thumbnailPositionHandle, 3, GLES20.GL_FLOAT, false, 20, planeVertices)
        GLES20.glEnableVertexAttribArray(thumbnailPositionHandle)

        planeVertices.position(3)
        GLES20.glVertexAttribPointer(thumbnailTexCoordHandle, 2, GLES20.GL_FLOAT, false, 20, planeVertices)
        GLES20.glEnableVertexAttribArray(thumbnailTexCoordHandle)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, leftId)
        GLES20.glUniformMatrix4fv(thumbnailMvpMatrixHandle, 1, false, mvpLeft, 0)
        GLES20.glUniform1i(thumbnailTextureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rightId)
        GLES20.glUniformMatrix4fv(thumbnailMvpMatrixHandle, 1, false, mvpRight, 0)
        GLES20.glUniform1i(thumbnailTextureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)


        GLES20.glUseProgram(program)

        planeVertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 20, planeVertices)
        GLES20.glEnableVertexAttribArray(positionHandle)

        planeVertices.position(3)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 20, planeVertices)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(thumbnailPositionHandle)
        GLES20.glDisableVertexAttribArray(thumbnailTexCoordHandle)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    fun loadTextureThumbnail(videoListUri: List<Uri>, currentVideoUri: Uri){
        val currentIndex = videoListUri.indexOf(currentVideoUri)
        if (currentIndex == -1) {
            deleteTexture(leftId)
            deleteTexture(rightId)
            leftId = -1
            rightId = -1
            return
        }

        val prevUri = if (currentIndex > 0) {
            videoListUri[currentIndex - 1]
        } else {
            videoListUri.last()
        }

        val nextUri = if (currentIndex < videoListUri.lastIndex) {
            videoListUri[currentIndex + 1]
        } else {
            videoListUri.first()
        }

        prevUri?.let {
            val bitmap = getVideoThumbnail(context, it)
            bitmap?.let { bmp: Bitmap ->
                deleteTexture(leftId)
                leftId = loadTextureFromBitmap(bmp)
                bmp.recycle()
            }
        } ?: run {
            deleteTexture(leftId)
            leftId = -1
        }

        nextUri?.let {
            val bitmap = getVideoThumbnail(context, it)
            bitmap?.let { bmp: Bitmap ->
                deleteTexture(rightId)
                rightId = loadTextureFromBitmap(bmp)
                bmp.recycle()
            }
        } ?: run {
            deleteTexture(rightId)
            rightId = -1
        }
    }

    private fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        if (textureIds[0] == 0) throw RuntimeException("Failed to generate texture ID")

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textureIds[0]
    }

    private fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.frameAtTime ?: null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun deleteTexture(textureId: Int) {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
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

    companion object {
        fun loadGalleryMP4(context: Context, maxCount: Int? = null): List<Uri> {
            val videoList = mutableListOf<Uri>()
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("DCIM/Camera%")

            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            Log.d("loadGalleryMP4", "Cursor count: ${cursor?.count ?: "null"}")

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                var count = 0

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val videoUri = ContentUris.withAppendedId(uri, id)
                    videoList.add(videoUri)

                    count++
                    if (maxCount != null && count >= maxCount) {
                        break
                    }
                }
            }

            Log.d("loadGalleryMP4", "Loaded ${videoList.size} videos")
            return videoList
        }
    }
}