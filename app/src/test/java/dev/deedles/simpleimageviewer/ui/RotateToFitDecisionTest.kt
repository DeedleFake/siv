package dev.deedles.simpleimageviewer.ui

import android.view.Surface
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotateToFitDecisionTest {
    private val all = Orientation.entries

    @Test
    fun rotatesOnlyWhenEnabledAndOrientationsDiffer() {
        for (image in all) for (window in all) {
            assertEquals(image != window, shouldRotateToFit(image, window, enabled = true))
            assertFalse(shouldRotateToFit(image, window, enabled = false))
        }
    }

    @Test
    fun doesNotRotateWithoutAnImageSize() {
        for (window in all) assertFalse(shouldRotateToFit(null, window, enabled = true))
    }

    @Test
    fun landscapeOnlyWhenWiderThanTall() {
        assertEquals(Orientation.Landscape, Orientation.of(1600, 900))
        assertEquals(Orientation.Portrait, Orientation.of(900, 1600))
        assertEquals(Orientation.Portrait, Orientation.of(1000, 1000))
        assertEquals(Orientation.Landscape, Orientation.of(Size(1600f, 900f)))
        assertEquals(Orientation.Portrait, Orientation.of(Size(1000f, 1000f)))
    }

    @Test
    fun portraitImageInLandscapeWindowRotates() {
        assertTrue(shouldRotateToFit(Orientation.Portrait, Orientation.Landscape, enabled = true))
    }

    private fun turn(image: Orientation?, window: Orientation, rotation: DisplayRotation, enabled: Boolean = true) =
        rotateToFitTurn(image, window, enabled, rotation)

    @Test
    fun noTurnWhenNoneIsNeeded() {
        for (rotation in DisplayRotation.entries) {
            for (orientation in all) assertNull(turn(orientation, orientation, rotation))
            for (window in all) assertNull(turn(null, window, rotation))
            assertNull(turn(Orientation.Portrait, Orientation.Landscape, rotation, enabled = false))
            assertNull(turn(Orientation.Landscape, Orientation.Portrait, rotation, enabled = false))
        }
    }

    @Test
    fun turnsTowardTheNaturalTopEdgeForEveryCombination() {
        val expected = mapOf(
            DisplayRotation.Rotation0 to Turn.Clockwise,
            DisplayRotation.Rotation90 to Turn.Clockwise,
            DisplayRotation.Rotation180 to Turn.Clockwise,
            DisplayRotation.Rotation270 to Turn.CounterClockwise,
        )
        for ((rotation, want) in expected) {
            assertEquals(rotation.name, want, turn(Orientation.Portrait, Orientation.Landscape, rotation))
            assertEquals(rotation.name, want, turn(Orientation.Landscape, Orientation.Portrait, rotation))
        }
    }

    @Test
    fun ties() {
        assertEquals(Turn.Clockwise, turn(Orientation.Landscape, Orientation.Portrait, DisplayRotation.Rotation0))
        assertEquals(Turn.Clockwise, turn(Orientation.Landscape, Orientation.Portrait, DisplayRotation.Rotation180))
    }

    @Test
    fun displayRotationFromSurfaceConstants() {
        assertEquals(DisplayRotation.Rotation0, DisplayRotation.ofSurface(Surface.ROTATION_0))
        assertEquals(DisplayRotation.Rotation90, DisplayRotation.ofSurface(Surface.ROTATION_90))
        assertEquals(DisplayRotation.Rotation180, DisplayRotation.ofSurface(Surface.ROTATION_180))
        assertEquals(DisplayRotation.Rotation270, DisplayRotation.ofSurface(Surface.ROTATION_270))
    }
}
