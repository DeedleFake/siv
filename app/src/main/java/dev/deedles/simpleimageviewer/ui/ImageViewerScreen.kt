package dev.deedles.simpleimageviewer.ui

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ImageViewerScreen(
    modifier: Modifier = Modifier,
    uri: Uri?,
    onImageSelected: (Uri) -> Unit,
    onOrientationForImage: (width: Int, height: Int) -> Unit
) {
    val context = LocalContext.current

    var isMaxBrightness by rememberSaveable { mutableStateOf(false) }
    var isAutoRotateEnabled by rememberSaveable { mutableStateOf(true) }
    val displayRotation = rememberDisplayRotation()

    // Store image dimensions to re-apply orientation when toggling auto-rotate
    var imageSize by remember { mutableStateOf<Size?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            if (selectedUri != null) {
                onImageSelected(selectedUri)
            }
        }
    )

    val launchPicker = {
        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(uri) {
        if (uri == null) {
            launchPicker()
        }
    }

    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.let { window ->
            val controller = window.insetsController
            controller?.hide(WindowInsets.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    LaunchedEffect(isMaxBrightness) {
        (context as? Activity)?.window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = if (isMaxBrightness) 1.0f else -1.0f
            }
        }
    }

    val radialMenuItems = remember(isMaxBrightness, isAutoRotateEnabled, imageSize) {
        listOf(
            RadialMenuItem(
                label = "Open Image",
                onSelect = { launchPicker() }
            ),
            RadialMenuItem(
                label = if (isMaxBrightness) "Brightness: MAX" else "Brightness: AUTO",
                onSelect = { isMaxBrightness = !isMaxBrightness }
            ),
            RadialMenuItem(
                label = if (isAutoRotateEnabled) "Auto-Rotate: ON" else "Auto-Rotate: OFF",
                onSelect = { 
                    isAutoRotateEnabled = !isAutoRotateEnabled
                    if (!isAutoRotateEnabled) {
                        onOrientationForImage(0, 0)
                    } else {
                        // Re-trigger rotation with stored size
                        imageSize?.let { onOrientationForImage(it.width.toInt(), it.height.toInt()) }
                    }
                }
            )
        )
    }

    var debugWindowSize by remember { mutableStateOf(IntSize.Zero) } // DEBUG: remove

    RadialMenu(items = radialMenuItems) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { debugWindowSize = it } // DEBUG: remove
        ) {
            if (uri != null) {
                ZoomableAsyncImage(
                    uri = uri,
                    onImageSizeDetermined = { w, h ->
                        imageSize = Size(w.toFloat(), h.toFloat())
                        if (isAutoRotateEnabled) {
                            onOrientationForImage(w, h)
                        }
                    },
                    modifier = Modifier
                        .rotateToFit(imageSize, enabled = isAutoRotateEnabled, displayRotation = displayRotation)
                        .fillMaxSize()
                )
            } else {
                EmptyState(onOpenPicker = launchPicker)
            }
            // DEBUG: remove
            DebugRotationReadout(
                listenerRotation = displayRotation,
                windowSize = debugWindowSize,
                imageSize = imageSize,
                enabled = isAutoRotateEnabled,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

// DEBUG: remove. Temporary readout of the values rotate-to-fit depends on.
@Composable
private fun DebugRotationReadout(
    listenerRotation: DisplayRotation,
    windowSize: IntSize,
    imageSize: Size?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Read straight from the display on every recomposition and every 250 ms, independent of the listener.
    val polled by produceState(initialValue = view.display?.rotation, view) {
        while (true) {
            value = view.display?.rotation
            kotlinx.coroutines.delay(250)
        }
    }
    val direct = view.display?.rotation // DEBUG: remove
    val directRotation = DisplayRotation.ofSurface(direct ?: 0)
    val windowOrientation = Orientation.of(windowSize.width, windowSize.height)
    val imageOrientation = imageSize?.let(Orientation::of)
    val turnListener = rotateToFitTurn(imageOrientation, windowOrientation, enabled, listenerRotation)
    val turnDirect = rotateToFitTurn(imageOrientation, windowOrientation, enabled, directRotation)
    val activity = context as? Activity
    val metrics = context.resources.displayMetrics
    val windowManager = context.getSystemService(WindowManager::class.java)
    val current = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds else null
    val maximum = if (Build.VERSION.SDK_INT >= 30) windowManager.maximumWindowMetrics.bounds else null
    val orientationName = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        else -> "undefined"
    }
    val lines = listOf(
        "rot listener: ${listenerRotation.degrees} (${listenerRotation.name})",
        "rot direct:   $direct (recomposition)",
        "rot polled:   $polled (250ms)",
        "cfg orient:   ${configuration.orientation} $orientationName",
        "cfg sw: ${configuration.smallestScreenWidthDp}dp  ${configuration.screenWidthDp}x${configuration.screenHeightDp}dp",
        "window px:    ${windowSize.width}x${windowSize.height} $windowOrientation",
        "image px:     ${imageSize?.let { "${it.width.toInt()}x${it.height.toInt()}" }} $imageOrientation",
        "auto-rotate:  $enabled",
        "turn listener: $turnListener",
        "turn direct:   $turnDirect",
        "requestedOrientation: ${activity?.requestedOrientation}",
        "displayMetrics: ${metrics.widthPixels}x${metrics.heightPixels}",
        "wm current: ${current?.let { "${it.width()}x${it.height()}" }}",
        "wm maximum: ${maximum?.let { "${it.width()}x${it.height()}" }}",
    )
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.6f)).padding(4.dp)) {
        Column {
            lines.forEach {
                Text(text = it, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenPicker: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onOpenPicker() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No image selected\n\nTap to choose an image, or open one from your gallery or file manager.",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EmptyStatePreview() {
    MaterialTheme {
        EmptyState(onOpenPicker = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ImageViewerScreenEmptyPreview() {
    MaterialTheme {
        ImageViewerScreen(
            uri = null,
            onImageSelected = {},
            onOrientationForImage = { _, _ -> }
        )
    }
}
