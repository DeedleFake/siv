package dev.deedles.simpleimageviewer

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The single Activity for Simple Image Viewer.
 *
 * - Launched from launcher → friendly empty state
 * - Launched via ACTION_VIEW (from gallery, files, share sheet, etc.) → shows that image fullscreen
 * - Automatically rotates the device to the orientation that best fits the image's aspect ratio
 * - True immersive fullscreen with pinch-to-zoom, pan, and double-tap zoom
 */
class MainActivity : ComponentActivity() {

    private var lastHandledUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow drawing under cutouts and system bars (we control visibility ourselves)
        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black
                )
            ) {
                ImageViewerApp(
                    initialUri = extractImageUri(intent),
                    onOrientationForImage = { w, h -> setOptimalOrientation(w, h) }
                )
            }
        }

        handleNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNewIntent(intent)
    }

    private fun handleNewIntent(intent: Intent) {
        val uri = extractImageUri(intent)
        if (uri != null && uri != lastHandledUri) {
            lastHandledUri = uri
            // Recreate is the simplest way to get a fresh composition with the new image
            // while keeping the single-activity contract simple.
            recreate()
        }
    }

    private fun extractImageUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_VIEW) return null
        return intent.data
    }

    /**
     * Requests the best screen orientation for the given image dimensions.
     * Landscape photos → landscape, portrait photos → portrait.
     */
    private fun setOptimalOrientation(imageWidth: Int, imageHeight: Int) {
        if (imageWidth <= 0 || imageHeight <= 0) return

        val desired = if (imageWidth > imageHeight) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        if (requestedOrientation != desired) {
            requestedOrientation = desired
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                   UI                                       */
/* -------------------------------------------------------------------------- */

@Composable
fun ImageViewerApp(
    initialUri: Uri?,
    onOrientationForImage: (width: Int, height: Int) -> Unit
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf(initialUri) }

    // Hide system bars for true fullscreen / immersive experience
    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (imageUri != null) {
            ZoomableAsyncImage(
                uri = imageUri!!,
                onImageSizeDetermined = onOrientationForImage,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            EmptyState()
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No image selected\n\nOpen this app from your gallery, file manager, or any app that can share images.",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

/**
 * Full-featured zoomable image viewer using Coil + Compose gestures.
 * Supports pinch, pan, double-tap to zoom, and proper bounds clamping.
 */
@Composable
fun ZoomableAsyncImage(
    uri: Uri,
    onImageSizeDetermined: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // For smooth double-tap animation
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    // We need the container size to do proper clamping
    var containerSize by remember { mutableStateOf(Offset(1f, 1f)) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(0.6f, 8f)
        val scaleFactor = if (scale == 0f) 1f else newScale / scale

        offset = Offset(
            x = offset.x * scaleFactor + offsetChange.x,
            y = offset.y * scaleFactor + offsetChange.y
        )

        scale = newScale
        offset = clampOffset(offset, scale, containerSize)
    }

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
        offset = Offset.Zero
        scaleAnim.snapTo(1f)
        offsetXAnim.snapTo(0f)
        offsetYAnim.snapTo(0f)
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(uri) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            val targetScale = if (scale > 1.15f) 1f else 2.8f

                            val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
                            val tapVector = tapOffset - center

                            val newOffset = if (targetScale <= 1f) {
                                Offset.Zero
                            } else {
                                // Bring the tapped point toward the center as we zoom in
                                -tapVector * (targetScale - 1f) * 0.55f
                            }

                            val spec: AnimationSpec<Float> = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )

                            launch { scaleAnim.animateTo(targetScale, spec) }
                            launch { offsetXAnim.animateTo(newOffset.x, spec) }
                            launch { offsetYAnim.animateTo(newOffset.y, spec) }

                            // Drive the actual state from the animatables
                            scaleAnim.value.let { s ->
                                val factor = if (scale == 0f) 1f else s / scale
                                offset = Offset(offset.x * factor, offset.y * factor) + (newOffset - offset) * 0.6f
                            }
                            scale = scaleAnim.value
                            offset = clampOffset(Offset(offsetXAnim.value, offsetYAnim.value), scale, containerSize)
                        }
                    }
                )
            }
            .transformable(state = transformableState)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationX = offsetXAnim.value
                translationY = offsetYAnim.value
            }
    ) {
        containerSize = Offset(maxWidth.value, maxHeight.value)

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

    // Allow panning but always keep a portion of the image visible
    val maxX = container.x * (scale - 1f) * 0.85f
    val maxY = container.y * (scale - 1f) * 0.85f

    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}
