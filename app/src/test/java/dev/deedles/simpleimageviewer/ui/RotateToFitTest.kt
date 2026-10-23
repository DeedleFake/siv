package dev.deedles.simpleimageviewer.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A display at 1 px per dp, big enough to hold the window in either orientation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h1280dp-mdpi")
class RotateToFitTest {
    @get:Rule
    val rule = createComposeRule()

    private val portraitImage = Size(900f, 1600f)
    private val landscapeImage = Size(1600f, 900f)

    private var windowWidth by mutableStateOf(1280)
    private var windowHeight by mutableStateOf(800)
    private var imageSize by mutableStateOf<Size?>(portraitImage)
    private var autoRotate by mutableStateOf(true)
    private var displayRotation by mutableStateOf(DisplayRotation.Rotation0)

    private fun show() {
        rule.setContent {
            Box(Modifier.size(windowWidth.dp, windowHeight.dp)) {
                ZoomableAsyncImage(
                    uri = Uri.EMPTY,
                    onImageSizeDetermined = { _, _ -> },
                    modifier = Modifier
                        .rotateToFit(imageSize, enabled = autoRotate, displayRotation = displayRotation)
                        .fillMaxSize()
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
    }

    private fun image() = rule.onNodeWithContentDescription("Image being viewed").fetchSemanticsNode()

    private fun assertFillsWindow() {
        assertEquals(Rect(0f, 0f, windowWidth.toFloat(), windowHeight.toFloat()), image().boundsInRoot.rounded())
    }

    private fun assertNotRotated() {
        assertEquals(IntSize(windowWidth, windowHeight), image().size)
        assertFillsWindow()
    }

    private fun assertRotated() {
        assertEquals(IntSize(windowHeight, windowWidth), image().size)
        assertFillsWindow()
    }

    private fun Rect.rounded() = Rect(left.toInt().toFloat(), top.toInt().toFloat(), right.toInt().toFloat(), bottom.toInt().toFloat())

    private fun change(block: () -> Unit) {
        rule.runOnUiThread {
            block()
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun settle() = rule.mainClock.advanceTimeBy(5000)

    @Test
    fun portraitImageInLandscapeWindowIsRotatedToFill() {
        show()
        assertRotated()
    }

    @Test
    fun landscapeImageInLandscapeWindowIsNotRotated() {
        imageSize = landscapeImage
        show()
        assertNotRotated()
    }

    @Test
    fun portraitImageInPortraitWindowIsNotRotated() {
        windowWidth = 800
        windowHeight = 1280
        show()
        assertNotRotated()
    }

    @Test
    fun landscapeImageInPortraitWindowIsRotated() {
        windowWidth = 800
        windowHeight = 1280
        imageSize = landscapeImage
        show()
        assertRotated()
    }

    @Test
    fun unknownImageSizeIsNotRotated() {
        imageSize = null
        show()
        assertNotRotated()
    }

    @Test
    fun disabledIsNotRotated() {
        autoRotate = false
        show()
        assertNotRotated()
    }

    @Test
    fun turningTheWindowUndoesAndRedoesTheRotation() {
        show()
        assertRotated()
        change { windowWidth = 800; windowHeight = 1280 }
        assertNotRotated()
        change { windowWidth = 1280; windowHeight = 800 }
        assertRotated()
    }

    @Test
    fun togglingAutoRotateUndoesAndRedoesTheRotation() {
        show()
        change { autoRotate = false }
        assertNotRotated()
        change { autoRotate = true }
        assertRotated()
    }

    @Test
    fun imageSizeArrivingLaterRotates() {
        imageSize = null
        show()
        assertNotRotated()
        change { imageSize = portraitImage }
        assertRotated()
    }

    // Where the content's top goes depends on the display's natural orientation, which comes from the
    // window and the display rotation: a portrait window at rotation 0 or 180, or a landscape one at 90
    // or 270, is a naturally portrait display, whose physical top is its natural left edge. Rotations 0,
    // 90 and 270 were checked on a Pixel Tablet, which is that case. The direction the
    // Display.getRotation documentation gives has not been checked on a naturally landscape display.

    /** Where the content's top-left and top-right corner markers end up in a window of the given size. */
    private fun markers(
        image: Size,
        width: Int,
        height: Int,
        rotation: DisplayRotation,
    ): Pair<Offset, Offset> {
        rule.setContent {
            Box(Modifier.size(width.dp, height.dp)) {
                Box(Modifier.rotateToFit(image, enabled = true, displayRotation = rotation).fillMaxSize()) {
                    Box(Modifier.size(10.dp).align(Alignment.TopStart).testTag("topStart"))
                    Box(Modifier.size(10.dp).align(Alignment.TopEnd).testTag("topEnd"))
                }
            }
        }
        val topStart = rule.onNodeWithTag("topStart").fetchSemanticsNode().boundsInRoot.center
        val topEnd = rule.onNodeWithTag("topEnd").fetchSemanticsNode().boundsInRoot.center
        return topStart to topEnd
    }

    // Clockwise: the content's top faces the window's right edge.
    private fun assertTurnedClockwise(markers: Pair<Offset, Offset>, width: Int, height: Int) {
        assertEquals(Offset(width - 5f, 5f), markers.first)
        assertEquals(Offset(width - 5f, height - 5f), markers.second)
    }

    // Counter-clockwise: the content's top faces the window's left edge.
    private fun assertTurnedCounterClockwise(markers: Pair<Offset, Offset>, width: Int, height: Int) {
        assertEquals(Offset(5f, height - 5f), markers.first)
        assertEquals(Offset(5f, 5f), markers.second)
    }

    // Naturally portrait display (the Pixel Tablet's portrait hold).

    @Test
    fun portraitWindowAtRotation0TurnsLandscapeContentCounterClockwiseSoItsTopFacesTheLeftEdge() {
        assertTurnedCounterClockwise(markers(landscapeImage, 800, 1280, DisplayRotation.Rotation0), 800, 1280)
    }

    @Test
    fun portraitWindowAtRotation180TurnsLandscapeContentClockwiseSoItsTopFacesTheRightEdge() {
        assertTurnedClockwise(markers(landscapeImage, 800, 1280, DisplayRotation.Rotation180), 800, 1280)
    }

    @Test
    fun landscapeWindowAtRotation90TurnsPortraitContentCounterClockwiseSoItsTopFacesTheNaturalTopEdgeOnTheLeft() {
        assertTurnedCounterClockwise(markers(portraitImage, 1280, 800, DisplayRotation.Rotation90), 1280, 800)
    }

    @Test
    fun landscapeWindowAtRotation270TurnsPortraitContentClockwiseSoItsTopFacesTheNaturalTopEdgeOnTheRight() {
        assertTurnedClockwise(markers(portraitImage, 1280, 800, DisplayRotation.Rotation270), 1280, 800)
    }

    // Naturally landscape display, with the direction from the Display.getRotation documentation.

    @Test
    fun landscapeWindowAtRotation0TurnsPortraitContentClockwise() {
        assertTurnedClockwise(markers(portraitImage, 1280, 800, DisplayRotation.Rotation0), 1280, 800)
    }

    @Test
    fun landscapeWindowAtRotation180TurnsPortraitContentClockwise() {
        assertTurnedClockwise(markers(portraitImage, 1280, 800, DisplayRotation.Rotation180), 1280, 800)
    }

    @Test
    fun portraitWindowAtRotation90TurnsLandscapeContentCounterClockwiseSoItsTopFacesTheNaturalTopEdgeOnTheLeft() {
        assertTurnedCounterClockwise(markers(landscapeImage, 800, 1280, DisplayRotation.Rotation90), 800, 1280)
    }

    @Test
    fun portraitWindowAtRotation270TurnsLandscapeContentClockwiseSoItsTopFacesTheNaturalTopEdgeOnTheRight() {
        assertTurnedClockwise(markers(landscapeImage, 800, 1280, DisplayRotation.Rotation270), 800, 1280)
    }

    @Test
    fun changingTheDisplayRotationFlipsTheTurnWithoutChangingTheWindow() {
        rule.setContent {
            Box(Modifier.size(800.dp, 1280.dp)) {
                Box(Modifier.rotateToFit(landscapeImage, enabled = true, displayRotation = displayRotation).fillMaxSize()) {
                    Box(Modifier.size(10.dp).align(Alignment.TopStart).testTag("topStart"))
                    Box(Modifier.size(10.dp).align(Alignment.TopEnd).testTag("topEnd"))
                }
            }
        }
        fun markers() = rule.onNodeWithTag("topStart").fetchSemanticsNode().boundsInRoot.center to
            rule.onNodeWithTag("topEnd").fetchSemanticsNode().boundsInRoot.center
        assertTurnedCounterClockwise(markers(), 800, 1280)
        change { displayRotation = DisplayRotation.Rotation180 }
        assertTurnedClockwise(markers(), 800, 1280)
        change { displayRotation = DisplayRotation.Rotation90 }
        assertTurnedCounterClockwise(markers(), 800, 1280)
        change { displayRotation = DisplayRotation.Rotation270 }
        assertTurnedClockwise(markers(), 800, 1280)
        change { displayRotation = DisplayRotation.Rotation0 }
        assertTurnedCounterClockwise(markers(), 800, 1280)
    }

    // The window is landscape for the clockwise case and portrait for the counter-clockwise case, so the
    // display rotation is 0 in both: a naturally landscape and a naturally portrait display.

    @Test
    fun doubleTapZoomsTowardsTheTappedPointInScreenDirections() = doubleTapZooms()

    @Test
    fun doubleTapZoomsTowardsTheTappedPointWhenTurnedCounterClockwise() {
        showLandscapeContentInPortraitWindow()
        doubleTapZooms()
    }

    private fun showLandscapeContentInPortraitWindow() {
        windowWidth = 800
        windowHeight = 1280
        imageSize = landscapeImage
    }

    private fun doubleTapZooms() {
        show()
        assertRotated()
        val center = Offset(windowWidth / 2f, windowHeight / 2f)
        val tap = Offset(windowWidth - 100f, 100f)
        rule.onRoot().performTouchInput { click(tap) ; advanceEventTime(100); click(tap) }
        settle()
        val zoomed = image().boundsInRoot
        // Zooming 3x about the tapped point keeps that point fixed, so the content's centre moves
        // away from it.
        val expected = tap + (center - tap) * 3f
        assertEquals(expected.x, zoomed.center.x, 2f)
        assertEquals(expected.y, zoomed.center.y, 2f)
        assertEquals(3f, zoomed.width / windowWidth, 0.02f)
    }

    @Test
    fun dragPansInTheDirectionOfTheFinger() = dragPans()

    @Test
    fun dragPansInTheDirectionOfTheFingerWhenTurnedCounterClockwise() {
        showLandscapeContentInPortraitWindow()
        dragPans()
    }

    private fun dragPans() {
        show()
        assertRotated()
        val middle = Offset(windowWidth / 2f, windowHeight / 2f)
        rule.onRoot().performTouchInput { click(middle); advanceEventTime(100); click(middle) }
        settle()
        val before = image().boundsInRoot.center
        rule.onRoot().performTouchInput {
            swipe(middle, middle + Offset(100f, -40f), durationMillis = 200)
        }
        settle()
        val moved = image().boundsInRoot.center - before
        assertTrue("moved right by about 100, was ${moved.x}", moved.x in 60f..110f)
        assertTrue("moved up by about 40, was ${moved.y}", moved.y in -50f..-15f)
    }
}
