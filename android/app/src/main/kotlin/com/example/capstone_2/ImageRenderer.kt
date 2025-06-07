package com.example.capstone_2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.util.Log
import android.content.ContentUris

class ImageRenderer {
    private var currentImageIndex = 0
    private val textureIds = mutableListOf<Int>()
    private lateinit var planeVertices: FloatBuffer

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureHandle = 0

    fun init(context: Context) {
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

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")

        val bitmaps = loadGalleryImages(context, 10)
        bitmaps.forEach {
            textureIds.add(loadTextureFromBitmap(it))
        }
    }

    fun loadGalleryImages(context: Context, maxImage: Int? = null): List<Bitmap> {
        val imageList = mutableListOf<Bitmap>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%DCIM/Camera%")


        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val contentResolver = context.contentResolver
            var count = 0

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val imageUri = ContentUris.withAppendedId(uri, id)

                try {
                    val bitmap = contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }

                    if (bitmap != null) {
                        imageList.add(bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                count++
                if (maxImage != null && count >= maxImage) {
                    break
                }
            }
        }

        Log.d("loadGalleryImages", "Loaded ${imageList.size} images")
        return imageList
    }

    fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textures[0]
    }

    fun nextImage() {
        currentImageIndex = (currentImageIndex + 1) % textureIds.size
    }

    fun previousImage() {
        currentImageIndex = (currentImageIndex - 1 + textureIds.size) % textureIds.size
    }

    fun draw() {
        if (textureIds.isEmpty()) return

        GLES20.glUseProgram(program)

        val previewScale = 0.2f
        val centerScale = 1f

        val identityMatrix = FloatArray(16)
        Matrix.setIdentityM(identityMatrix, 0)

        fun drawImage(index: Int, xPos: Float, scale: Float) {
            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, xPos, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, scale, scale, 1f)

            val mvpMatrix = FloatArray(16)
            Matrix.multiplyMM(mvpMatrix, 0, identityMatrix, 0, modelMatrix, 0)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            val planeCoords = floatArrayOf(
                -0.5f,  0.5f, 0f,   0f, 0f,
                -0.5f, -0.5f, 0f,   0f, 1f,
                0.5f,  0.5f, 0f,   1f, 0f,
                0.5f, -0.5f, 0f,   1f, 1f
            )

            val bb = ByteBuffer.allocateDirect(planeCoords.size * 4).order(ByteOrder.nativeOrder())
            val vertexBuffer = bb.asFloatBuffer()
            vertexBuffer.put(planeCoords)
            vertexBuffer.position(0)

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer)
            GLES20.glEnableVertexAttribArray(positionHandle)

            vertexBuffer.position(3)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[index])
            GLES20.glUniform1i(textureHandle, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        val prevIndex = (currentImageIndex - 1 + textureIds.size) % textureIds.size
        val nextIndex = (currentImageIndex + 1) % textureIds.size

        drawImage(prevIndex, -0.9f, previewScale)
        drawImage(currentImageIndex, 0f, centerScale)
        drawImage(nextIndex, 0.9f, previewScale)
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