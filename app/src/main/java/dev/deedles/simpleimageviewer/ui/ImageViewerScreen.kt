package dev.deedles.simpleimageviewer.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
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

    RadialMenu(items = radialMenuItems) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EmptyState(onOpenPicker = launchPicker)
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
