package dev.deedles.simpleimageviewer

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OptimalOrientationRequestTest {
    @Test
    fun landscapeImagesRequestSensorLandscape() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, optimalOrientationRequest(1600, 900))
    }

    @Test
    fun portraitImagesRequestSensorPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, optimalOrientationRequest(900, 1600))
    }

    @Test
    fun squareImagesRequestSensorPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, optimalOrientationRequest(1000, 1000))
    }

    @Test
    fun noImageRequestsTheSystemDefault() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, optimalOrientationRequest(0, 0))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, optimalOrientationRequest(0, 500))
    }
}
