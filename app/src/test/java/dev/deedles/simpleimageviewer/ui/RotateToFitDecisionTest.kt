package dev.deedles.simpleimageviewer.ui

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
