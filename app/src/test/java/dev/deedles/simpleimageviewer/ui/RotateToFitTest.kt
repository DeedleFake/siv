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

// A large display at 1 px per dp, tall enough to hold the window in either orientation.
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

    private fun show(requireLargeDisplay: Boolean = false) {
        rule.setContent {
            val large = rememberIgnoresOrientationRequests()
            Box(Modifier.size(windowWidth.dp, windowHeight.dp)) {
                ZoomableAsyncImage(
                    uri = Uri.EMPTY,
                    onImageSizeDetermined = { _, _ -> },
                    modifier = Modifier
                        .rotateToFit(imageSize, enabled = autoRotate && (large || !requireLargeDisplay))
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

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-mdpi")
    fun phoneDisplayIsNotRotatedEvenWhenTheWindowIsLandscape() {
        windowWidth = 400
        windowHeight = 300
        show(requireLargeDisplay = true)
        assertNotRotated()
    }

    @Test
    fun tabletDisplayIsRotated() {
        show(requireLargeDisplay = true)
        assertRotated()
    }

    @Test
    fun rotationIsClockwiseSoTheContentsTopFacesTheRightEdge() {
        rule.setContent {
            Box(Modifier.size(1280.dp, 800.dp)) {
                Box(Modifier.rotateToFit(portraitImage, enabled = true).fillMaxSize()) {
                    Box(Modifier.size(10.dp).align(Alignment.TopStart).testTag("topStart"))
                    Box(Modifier.size(10.dp).align(Alignment.TopEnd).testTag("topEnd"))
                }
            }
        }
        val topStart = rule.onNodeWithTag("topStart").fetchSemanticsNode().boundsInRoot.center
        val topEnd = rule.onNodeWithTag("topEnd").fetchSemanticsNode().boundsInRoot.center
        assertEquals(Offset(1275f, 5f), topStart)
        assertEquals(Offset(1275f, 795f), topEnd)
    }

    @Test
    fun doubleTapZoomsTowardsTheTappedPointInScreenDirections() {
        show()
        val center = Offset(640f, 400f)
        val tap = Offset(1180f, 100f)
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
    fun dragPansInTheDirectionOfTheFinger() {
        show()
        val middle = Offset(640f, 400f)
        rule.onRoot().performTouchInput { click(middle); advanceEventTime(100); click(middle) }
        settle()
        val before = image().boundsInRoot.center
        rule.onRoot().performTouchInput {
            swipe(Offset(640f, 400f), Offset(740f, 360f), durationMillis = 200)
        }
        settle()
        val moved = image().boundsInRoot.center - before
        assertTrue("moved right by about 100, was ${moved.x}", moved.x in 60f..110f)
        assertTrue("moved up by about 40, was ${moved.y}", moved.y in -50f..-15f)
    }
}
