package com.example.capstone_2

import kotlin.math.sqrt

object OtherUtil {
    fun makeQuaternionLookRotation(forward: FloatArray, up: FloatArray): FloatArray {
        fun normalize(v: FloatArray): FloatArray {
            val len = sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])
            return floatArrayOf(v[0]/len, v[1]/len, v[2]/len)
        }

        val f = normalize(forward)
        val u = normalize(up)

        val r = floatArrayOf(
            u[1]*f[2] - u[2]*f[1],
            u[2]*f[0] - u[0]*f[2],
            u[0]*f[1] - u[1]*f[0]
        )

        val newUp = floatArrayOf(
            f[1]*r[2] - f[2]*r[1],
            f[2]*r[0] - f[0]*r[2],
            f[0]*r[1] - f[1]*r[0]
        )

        val rot = floatArrayOf(
            r[0], newUp[0], -f[0],
            r[1], newUp[1], -f[1],
            r[2], newUp[2], -f[2]
        )

        val qw = sqrt(1f + rot[0] + rot[4] + rot[8]) / 2f
        val qx = (rot[7] - rot[5]) / (4f * qw)
        val qy = (rot[2] - rot[6]) / (4f * qw)
        val qz = (rot[3] - rot[1]) / (4f * qw)

        return floatArrayOf(qx, qy, qz, qw)
    }
}