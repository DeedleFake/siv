package dev.deedles.simpleimageviewer.ui

import android.view.Surface
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // The window orientation the display has at a rotation, given its natural orientation: the natural one
    // at 0 and 180 degrees, the other one at 90 and 270.
    private fun windowAt(natural: Orientation, rotation: DisplayRotation) =
        if (rotation == DisplayRotation.Rotation0 || rotation == DisplayRotation.Rotation180) {
            natural
        } else {
            all.first { it != natural }
        }

    private fun assertTurns(natural: Orientation, expected: Map<DisplayRotation, Turn>) {
        for (rotation in DisplayRotation.entries) {
            val window = windowAt(natural, rotation)
            val image = all.first { it != window }
            val label = "natural $natural, $rotation"
            assertEquals(label, expected.getValue(rotation), turn(image, window, rotation))
        }
    }

    @Test
    fun naturalOrientationFollowsTheWindowAndTheRotation() {
        for (natural in all) for (rotation in DisplayRotation.entries) {
            assertEquals(natural, naturalOrientation(windowAt(natural, rotation), rotation))
        }
    }

    // A naturally landscape display: the content's top goes toward the natural top edge, using the
    // direction in the Display.getRotation documentation. Not checked on a device.
    @Test
    fun naturallyLandscapeDisplayTurnsTowardTheNaturalTopEdge() {
        assertTurns(
            Orientation.Landscape,
            mapOf(
                DisplayRotation.Rotation0 to Turn.Clockwise,
                DisplayRotation.Rotation90 to Turn.CounterClockwise,
                DisplayRotation.Rotation180 to Turn.Clockwise,
                DisplayRotation.Rotation270 to Turn.Clockwise,
            ),
        )
    }

    // A naturally portrait display, like the Pixel Tablet as Android reports it: the content's top goes
    // toward the natural left edge, and toward the natural top edge when both turns are equally close.
    // Rotations 0, 90 and 270 were checked on a Pixel Tablet.
    @Test
    fun naturallyPortraitDisplayTurnsTowardTheNaturalLeftEdge() {
        assertTurns(
            Orientation.Portrait,
            mapOf(
                DisplayRotation.Rotation0 to Turn.CounterClockwise,
                DisplayRotation.Rotation90 to Turn.CounterClockwise,
                DisplayRotation.Rotation180 to Turn.Clockwise,
                DisplayRotation.Rotation270 to Turn.Clockwise,
            ),
        )
    }

    @Test
    fun theTurnIsNeverNullWhenTheOrientationsDiffer() {
        for (rotation in DisplayRotation.entries) for (window in all) {
            assertNotNull(turn(all.first { it != window }, window, rotation))
        }
    }

    // Both turns are equally close to the target edge in these cases. A naturally landscape display
    // (a landscape window at rotation 0 or 180) takes clockwise. A naturally portrait display (a
    // landscape window at rotation 90 or 270) takes the turn toward the natural top edge, which is the
    // window's left edge at rotation 90 and its right edge at rotation 270.
    @Test
    fun ties() {
        for (rotation in listOf(DisplayRotation.Rotation0, DisplayRotation.Rotation180)) {
            assertEquals(Turn.Clockwise, turn(Orientation.Portrait, Orientation.Landscape, rotation))
        }
        assertEquals(
            Turn.CounterClockwise,
            turn(Orientation.Portrait, Orientation.Landscape, DisplayRotation.Rotation90),
        )
        assertEquals(
            Turn.Clockwise,
            turn(Orientation.Portrait, Orientation.Landscape, DisplayRotation.Rotation270),
        )
    }

    @Test
    fun displayRotationFromSurfaceConstants() {
        assertEquals(DisplayRotation.Rotation0, DisplayRotation.ofSurface(Surface.ROTATION_0))
        assertEquals(DisplayRotation.Rotation90, DisplayRotation.ofSurface(Surface.ROTATION_90))
        assertEquals(DisplayRotation.Rotation180, DisplayRotation.ofSurface(Surface.ROTATION_180))
        assertEquals(DisplayRotation.Rotation270, DisplayRotation.ofSurface(Surface.ROTATION_270))
    }
}
