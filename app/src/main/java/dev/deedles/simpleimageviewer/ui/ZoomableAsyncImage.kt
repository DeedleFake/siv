package dev.deedles.simpleimageviewer.ui

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

/**
 * Full-featured zoomable image viewer using Coil + Compose gestures.
 * Supports pinch, pan, double-tap to zoom, and proper bounds clamping.
 *
 * @param uri The URI of the image to display.
 * @param onImageSizeDetermined Callback when the intrinsic size of the image is known.
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
fun ZoomableAsyncImage(
    uri: Uri,
    onImageSizeDetermined: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Zoom state - mutable states for high-performance gesture tracking
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // We need the container size to do proper clamping
    var containerSize by remember { mutableStateOf(Offset(1f, 1f)) }

    // Create a good ImageLoader for large photos (viewer use case)
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }

    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(180)
            .build()
    }

    LaunchedEffect(uri) {
        // Reset zoom state when a completely new image arrives
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                containerSize = Offset(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(uri) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val center = Offset(containerSize.x / 2f, containerSize.y / 2f)
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    val zoomFactor = if (scale == 0f) 1f else newScale / scale

                    // Zoom towards the centroid for a natural feel
                    val targetOffset = (Offset(offsetX, offsetY) + (center - centroid)) * zoomFactor - (center - centroid) + pan
                    val clamped = clampOffset(targetOffset, newScale, containerSize)

                    scale = newScale
                    offsetX = clamped.x
                    offsetY = clamped.y
                }
            }
            .pointerInput(uri) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            val targetScale = if (scale > 1.15f) 1f else 3f
                            val center = Offset(containerSize.x / 2f, containerSize.y / 2f)

                            val targetOffset = if (targetScale <= 1f) {
                                Offset.Zero
                            } else {
                                (center - tapOffset) * (targetScale - 1f)
                            }

                            val clamped = clampOffset(targetOffset, targetScale, containerSize)
                            val startScale = scale
                            val startOffset = Offset(offsetX, offsetY)

                            val anim = Animatable(0f)
                            anim.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) {
                                scale = startScale + (targetScale - startScale) * value
                                offsetX = startOffset.x + (clamped.x - startOffset.x) * value
                                offsetY = startOffset.y + (clamped.y - startOffset.y) * value
                            }
                        }
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = "Image being viewed",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        onImageSizeDetermined(size.width.toInt(), size.height.toInt())
                    }
                }
            }
        )
    }
}

/**
 * Keeps the zoomed image from being panned completely outside the visible area.
 */
private fun clampOffset(offset: Offset, scale: Float, container: Offset): Offset {
    if (scale <= 1f) return Offset.Zero

    // Max translation is half the extra width/height created by scaling
    val maxX = container.x * (scale - 1f) / 2f
    val maxY = container.y * (scale - 1f) / 2f

    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ZoomableAsyncImagePreview() {
    ZoomableAsyncImage(
        uri = Uri.EMPTY,
        onImageSizeDetermined = { _, _ -> },
        modifier = Modifier.fillMaxSize()
    )
}
