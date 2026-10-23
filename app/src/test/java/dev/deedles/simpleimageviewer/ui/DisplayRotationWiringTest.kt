package dev.deedles.simpleimageviewer.ui

import android.view.Surface
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DisplayRotationWiringTest {
    @get:Rule
    val rule = createComposeRule()

    private var seen = DisplayRotation.Rotation0
    private lateinit var view: android.view.View

    private fun show() {
        rule.setContent {
            view = LocalView.current
            Reader()
        }
    }

    @Composable
    private fun Reader() {
        seen = rememberDisplayRotation()
    }

    private fun rotateDisplayTo(rotation: Int) {
        rule.runOnUiThread { shadowOf(view.display).setRotation(rotation) }
        rule.waitForIdle()
    }

    @Test
    fun startsWithTheDisplaysCurrentRotation() {
        show()
        assertEquals(DisplayRotation.Rotation0, seen)
    }

    @Test
    fun followsTheDisplaysRotationWithoutAnyConfigurationChange() {
        show()
        rotateDisplayTo(Surface.ROTATION_90)
        assertEquals(DisplayRotation.Rotation90, seen)
        rotateDisplayTo(Surface.ROTATION_180)
        assertEquals(DisplayRotation.Rotation180, seen)
        rotateDisplayTo(Surface.ROTATION_270)
        assertEquals(DisplayRotation.Rotation270, seen)
    }
}
