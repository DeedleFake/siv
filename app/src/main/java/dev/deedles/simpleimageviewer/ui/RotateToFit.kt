package dev.deedles.simpleimageviewer.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

enum class Orientation {
    Portrait,
    Landscape;

    companion object {
        fun of(width: Int, height: Int) = if (width > height) Landscape else Portrait

        fun of(size: Size) = if (size.width > size.height) Landscape else Portrait
    }
}

/** Whether content should be turned a quarter turn so an image of [image] orientation fills a [window]. */
fun shouldRotateToFit(image: Orientation?, window: Orientation, enabled: Boolean) =
    enabled && image != null && image != window

/**
 * Lays the content out sideways and turns it 90 degrees clockwise when [shouldRotateToFit] says so,
 * so it fills the window instead of being letterboxed. The decision is remade on every layout.
 */
fun Modifier.rotateToFit(imageSize: Size?, enabled: Boolean): Modifier =
    layout { measurable, constraints ->
        val window = Orientation.of(constraints.maxWidth, constraints.maxHeight)
        if (!shouldRotateToFit(imageSize?.let(Orientation::of), window, enabled)) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val placeable = measurable.measure(Constraints.fixed(height, width))
            layout(width, height) {
                placeable.placeWithLayer((width - height) / 2, (height - width) / 2) {
                    rotationZ = 90f
                }
            }
        }
    }
