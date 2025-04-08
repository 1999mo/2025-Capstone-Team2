package com.example.capstone_2

import android.graphics.SurfaceTexture
import com.google.ar.core.Session

object ARCoreBridge {
    var surfaceTexture: SurfaceTexture? = null
    var session: Session? = null
    var textureId: Int = -1
}
