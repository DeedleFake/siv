package dev.deedles.simpleimageviewer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.deedles.simpleimageviewer.ui.ImageViewerScreen

/**
 * The single Activity for Simple Image Viewer.
 *
 * - Launched from launcher → friendly empty state
 * - Launched via ACTION_VIEW (from gallery, files, share sheet, etc.) → shows that image fullscreen
 * - Automatically rotates the device to the orientation that best fits the image's aspect ratio
 * - True immersive fullscreen with pinch-to-zoom, pan, and double-tap zoom
 */
class MainActivity : ComponentActivity() {

    private var imageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        imageUri = extractImageUri(intent)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black
                )
            ) {
                ImageViewerScreen(
                    uri = imageUri,
                    onImageSelected = { imageUri = it },
                    onOrientationForImage = { w, h -> setOptimalOrientation(w, h) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        imageUri = extractImageUri(intent)
    }

    private fun extractImageUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            }
            else -> null
        }
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
