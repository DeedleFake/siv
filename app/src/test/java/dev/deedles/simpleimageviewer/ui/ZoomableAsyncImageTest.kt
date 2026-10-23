package dev.deedles.simpleimageviewer.ui

import android.net.Uri
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZoomableAsyncImageTest {
    @get:Rule
    val rule = createComposeRule()

    private var slop = 0f

    private fun show() {
        rule.setContent {
            slop = ViewConfiguration.get(LocalContext.current).scaledDoubleTapSlop.toFloat()
            ZoomableAsyncImage(
                uri = Uri.EMPTY,
                onImageSizeDetermined = { _, _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }
        rule.mainClock.autoAdvance = false
    }

    private fun image() = rule.onNodeWithContentDescription("Image being viewed")

    private fun SemanticsNodeInteraction.widthPx() = fetchSemanticsNode().boundsInRoot.width

    // Taps in quick succession, 100 ms between the end of one tap and the start of the next.
    private fun tap(at: List<Offset>) {
        rule.onRoot().performTouchInput {
            at.forEachIndexed { i, position ->
                if (i > 0) advanceEventTime(100)
                click(position)
            }
        }
    }

    // Lets the zoom animation finish and returns the width the image is currently drawn at.
    private fun zoom(): Float {
        rule.mainClock.advanceTimeBy(3000)
        return image().widthPx()
    }

    @Test
    fun farSecondTapDoesNotZoom() {
        show()
        val before = zoom()
        tap(listOf(Offset(40f, 60f), Offset(40f + slop + 60f, 60f + 60f)))
        assertEquals(before, zoom(), 0.5f)
    }

    @Test
    fun nearSecondTapZooms() {
        show()
        val before = zoom()
        tap(listOf(Offset(100f, 100f), Offset(110f, 110f)))
        assertTrue(zoom() > before * 2f)
    }

    @Test
    fun thirdTapNearFarSecondTapZooms() {
        show()
        val before = zoom()
        val far = Offset(40f + slop + 60f, 60f + 60f)
        tap(listOf(Offset(40f, 60f), far))
        // Well inside the zoom animation's duration, so any zoom started by the second tap shows.
        rule.mainClock.advanceTimeBy(100)
        assertEquals(before, image().widthPx(), 0.5f)
        tap(listOf(far + Offset(10f, 10f)))
        assertTrue(zoom() > before * 2f)
    }
}
