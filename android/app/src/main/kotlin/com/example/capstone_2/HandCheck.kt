package com.example.capstone_2

import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Frame
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.google.ar.core.Plane

class HandCheck() {
    private var latestLandmarks: NormalizedLandmarkList? = null


    fun onHandLandmarksReceived(landmarks: NormalizedLandmarkList) {
        latestLandmarks = landmarks
    }

    fun getCurrentLandmarks(): NormalizedLandmarkList? {
        return latestLandmarks
    }

    fun updateAnchorWithHand(session: Session, frame: Frame, xPx: Float, yPx: Float, currentAnchor: Anchor?): Anchor? {
        val hits = frame.hitTest(xPx, yPx)
        for (hit in hits) {
            if (hit.trackable is Plane && (hit.trackable as Plane).isPoseInPolygon(hit.hitPose)) {
                currentAnchor?.detach()
                return session.createAnchor(hit.hitPose)
            }
        }
        return currentAnchor
    }
}