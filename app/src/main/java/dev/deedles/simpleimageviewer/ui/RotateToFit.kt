package dev.deedles.simpleimageviewer.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints

enum class Orientation {
    Portrait,
    Landscape;

    companion object {
        fun of(width: Int, height: Int) = if (width > height) Landscape else Portrait

        fun of(size: Size) = if (size.width > size.height) Landscape else Portrait
    }
}

/**
 * How far the system has turned the drawn graphics clockwise from the device's natural orientation.
 * This is the opposite of how the device was physically turned.
 */
enum class DisplayRotation(val degrees: Int) {
    Rotation0(0),
    Rotation90(90),
    Rotation180(180),
    Rotation270(270);

    companion object {
        fun ofSurface(rotation: Int) = when (rotation) {
            Surface.ROTATION_90 -> Rotation90
            Surface.ROTATION_180 -> Rotation180
            Surface.ROTATION_270 -> Rotation270
            else -> Rotation0
        }
    }
}

/** A quarter turn of the content, as seen on screen. */
enum class Turn(val degrees: Float) {
    Clockwise(90f),
    CounterClockwise(-90f),
}

/** Whether content should be turned a quarter turn so an image of [image] orientation fills a [window]. */
fun shouldRotateToFit(image: Orientation?, window: Orientation, enabled: Boolean) =
    enabled && image != null && image != window

/**
 * The orientation the display has at [DisplayRotation.Rotation0]. A window that is portrait at
 * rotation 0 or 180 means the display is naturally portrait, and so does one that is landscape at
 * rotation 90 or 270.
 */
fun naturalOrientation(window: Orientation, displayRotation: DisplayRotation): Orientation {
    val upright = displayRotation == DisplayRotation.Rotation0 || displayRotation == DisplayRotation.Rotation180
    return if ((window == Orientation.Portrait) == upright) Orientation.Portrait else Orientation.Landscape
}

/**
 * The turn that puts the content's top toward the edge of the screen that is the device's physical
 * top when the device is held in its natural orientation, or null when no turn is needed. Rotating
 * clockwise wins ties.
 *
 * That edge is the natural top edge on a display that is naturally landscape. A display that Android
 * reports as naturally portrait is a landscape-first tablet held upright, such as the Pixel Tablet, so
 * its physical top is the natural left edge. The natural orientation is worked out from [window] and
 * [displayRotation] (see [naturalOrientation]).
 *
 * The direction of [displayRotation] follows the `Display.getRotation` documentation, where
 * [DisplayRotation.Rotation90] means the drawn graphics are turned clockwise from natural. It has
 * been checked on a device only for the Pixel Tablet's portrait hold. That is a naturally portrait
 * display, where the result does not depend on the direction, so the direction itself has not been
 * checked on any device. It only matters on naturally landscape displays.
 */
fun rotateToFitTurn(
    image: Orientation?,
    window: Orientation,
    enabled: Boolean,
    displayRotation: DisplayRotation,
): Turn? {
    if (!shouldRotateToFit(image, window, enabled)) return null
    val naturalLeftIsTop = if (naturalOrientation(window, displayRotation) == Orientation.Portrait) 90 else 0
    val offTarget = { turn: Turn ->
        val degrees = Math.floorMod(turn.degrees.toInt() + displayRotation.degrees + naturalLeftIsTop, 360)
        minOf(degrees, 360 - degrees)
    }
    return Turn.entries.minBy(offTarget)
}

/**
 * The display's current rotation. A display listener is needed because turning the device 180 degrees
 * changes neither the window size nor the configuration, so nothing else would notice.
 */
@Composable
fun rememberDisplayRotation(): DisplayRotation {
    val view = LocalView.current
    // The view has no display until it is attached, and previews never have one.
    val current = { DisplayRotation.ofSurface(view.display?.rotation ?: Surface.ROTATION_0) }
    var rotation by remember { mutableStateOf(current()) }
    DisposableEffect(view) {
        val manager = view.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}

            override fun onDisplayRemoved(displayId: Int) {}

            override fun onDisplayChanged(displayId: Int) {
                rotation = current()
            }
        }
        manager.registerDisplayListener(listener, null)
        rotation = current()
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return rotation
}

/**
 * Lays the content out sideways and turns it a quarter turn, in the direction [rotateToFitTurn]
 * picks, when one is needed, so it fills the window instead of being letterboxed. The decision is
 * remade on every layout.
 */
fun Modifier.rotateToFit(imageSize: Size?, enabled: Boolean, displayRotation: DisplayRotation): Modifier =
    layout { measurable, constraints ->
        val window = Orientation.of(constraints.maxWidth, constraints.maxHeight)
        val turn = rotateToFitTurn(imageSize?.let(Orientation::of), window, enabled, displayRotation)
        if (turn == null) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val placeable = measurable.measure(Constraints.fixed(height, width))
            layout(width, height) {
                placeable.placeWithLayer((width - height) / 2, (height - width) / 2) {
                    rotationZ = turn.degrees
                }
            }
        }
    }
