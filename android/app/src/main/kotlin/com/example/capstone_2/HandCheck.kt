package com.example.capstone_2

import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import android.util.Log

class HandCheck() {
    private var latestLandmarks: List<NormalizedLandmark>? = null


    fun onHandLandmarksReceived(landmarks: List<NormalizedLandmark>) {
        latestLandmarks = landmarks
    }

    fun getCurrentLandmarks(): List<NormalizedLandmark>? {
        return latestLandmarks
    }

    fun updateAnchorWithHand(session: Session, frame: Frame, xPx: Float, yPx: Float, currentAnchor: Anchor?): Anchor? {
        val hits = frame.hitTest(xPx, yPx)
        Log.d("updateAnchorWithHand", "Hits: $hits")
        for (hit in hits) {
            if (hit.trackable is Plane && (hit.trackable as Plane).isPoseInPolygon(hit.hitPose)) {
                currentAnchor?.detach()
                return session.createAnchor(hit.hitPose)
            }
        }
        return currentAnchor
    }
}