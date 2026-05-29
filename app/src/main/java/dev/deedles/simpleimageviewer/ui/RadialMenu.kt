package dev.deedles.simpleimageviewer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2

private val MenuRadius = 115.dp
private val CancelThreshold = 30.dp
private val MenuBackgroundPadding = 20.dp

data class RadialMenuItem(
    val label: String,
    val onSelect: () -> Unit
)

@Composable
fun RadialMenu(
    modifier: Modifier = Modifier,
    items: List<RadialMenuItem>,
    content: @Composable () -> Unit,
) {
    var menuCenter by remember { mutableStateOf<Offset?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val cancelThresholdPx = remember(density) { with(density) { CancelThreshold.toPx() } }
    val menuAlpha by animateFloatAsState(if (menuCenter != null) 1f else 0f, label = "Alpha")

    Box(
        modifier = modifier
            .fillMaxSize()
            .twoPointerLongDrag(
                key = items,
                onStart = { event ->
                    menuCenter = event.centroid()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onEnd = {
                    items.getOrNull(selectedIndex ?: -1)?.onSelect()
                    menuCenter = null
                    selectedIndex = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { event ->
                    val newIndex = calculateSelectedIndex(
                        location = event.centroid(),
                        menuCenter = menuCenter ?: return@twoPointerLongDrag,
                        itemCount = items.size,
                        cancelThreshold = cancelThresholdPx
                    )
                    if (newIndex != selectedIndex) {
                        selectedIndex = newIndex
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
            )
    ) {
        content()

        if (menuAlpha > 0f && menuCenter != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = menuCenter!!
                val radius = MenuRadius.toPx()
                val cancelRadius = CancelThreshold.toPx()

                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f * menuAlpha),
                    radius = radius + MenuBackgroundPadding.toPx(),
                    center = center
                )

                items.forEachIndexed { index, item ->
                    val sliceAngle = 360f / items.size
                    val midAngle = index * sliceAngle - 90f
                    val startAngle = midAngle - (sliceAngle / 2f)

                    val isSelected = index == selectedIndex
                    val color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)

                    if (isSelected) {
                        val strokeWidth = radius - cancelRadius
                        val arcPathRadius = cancelRadius + (strokeWidth / 2f)
                        drawArc(
                            color = Color.White.copy(alpha = 0.2f),
                            startAngle = startAngle,
                            sweepAngle = sliceAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - arcPathRadius, center.y - arcPathRadius),
                            size = androidx.compose.ui.geometry.Size(
                                arcPathRadius * 2,
                                arcPathRadius * 2
                            ),
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    // Curved Text Implementation
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val textRadius = cancelRadius + (radius - cancelRadius) * 0.55f

                        val paint = android.graphics.Paint().apply {
                            this.color = color.toArgb()
                            this.textSize =
                                with(density) { (if (isSelected) 16.sp else 14.sp).toPx() }
                            this.isAntiAlias = true
                            this.textAlign = android.graphics.Paint.Align.CENTER
                            this.typeface = android.graphics.Typeface.create(
                                android.graphics.Typeface.DEFAULT,
                                if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                            )
                        }

                        val path = android.graphics.Path()
                        val rect = android.graphics.RectF(
                            center.x - textRadius,
                            center.y - textRadius,
                            center.x + textRadius,
                            center.y + textRadius
                        )

                        // If the text is in the bottom half of the circle (0 to 180 degrees),
                        // we need to reverse the arc so the text isn't upside down.
                        val isBottomHalf = midAngle > -10 && midAngle < 190

                        if (isBottomHalf) {
                            // Bottom half: Path goes counter-clockwise, and we offset the radius
                            // slightly so the text sits "on top" of the path instead of "hanging".
                            val bottomRect = android.graphics.RectF(
                                center.x - (textRadius + paint.textSize),
                                center.y - (textRadius + paint.textSize),
                                center.x + (textRadius + paint.textSize),
                                center.y + (textRadius + paint.textSize)
                            )
                            path.addArc(bottomRect, midAngle + 90f, -180f)
                            nativeCanvas.drawTextOnPath(item.label, path, 0f, 0f, paint)
                        } else {
                            // Top half: Normal clockwise path
                            path.addArc(rect, midAngle - 90f, 180f)
                            nativeCanvas.drawTextOnPath(item.label, path, 0f, 0f, paint)
                        }
                    }
                }

                drawCircle(
                    color = Color.White.copy(alpha = 0.3f * menuAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw central "Cancel" zone
                val isCancelSelected = selectedIndex == null

                if (isCancelSelected) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = cancelRadius,
                        center = center
                    )
                }

                drawCircle(
                    color = Color.White.copy(alpha = 0.4f * menuAlpha),
                    radius = cancelRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Draw a small 'X' to indicate cancel
                val xSize = 6.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.6f * menuAlpha),
                    start = Offset(center.x - xSize, center.y - xSize),
                    end = Offset(center.x + xSize, center.y + xSize),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.6f * menuAlpha),
                    start = Offset(center.x + xSize, center.y - xSize),
                    end = Offset(center.x - xSize, center.y + xSize),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

private fun Modifier.twoPointerLongDrag(
    key: Any?,
    onStart: (PointerEvent) -> Unit,
    onEnd: (PointerEvent) -> Unit,
    onDrag: (PointerEvent) -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val event = awaitTwoPointerLongPress()
        onStart(event)
        multiPointerDrag(onDrag)
        onEnd(currentEvent)

        while (currentEvent.changes.any { it.pressed }) {
            awaitPointerEvent(PointerEventPass.Initial)
            currentEvent.changes.forEach { it.consume() }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitTwoPointerLongPress(): PointerEvent {
    while (true) {
        awaitPointerEvent(PointerEventPass.Initial)
        if (currentEvent.changes.size != 2 || currentEvent.changes.any { !it.pressed }) {
            continue
        }

        awaitTwoPointerLongPressInvalidation() ?: return currentEvent
        while (currentEvent.changes.size == 2 && currentEvent.changes.all { it.pressed }) {
            awaitPointerEvent(PointerEventPass.Initial)
        }
    }
}

/**
 * Waits for any event that would invalidate a two pointer long press. If such an event happens
 * within the long press timeout window, it is returned. Otherwise, null is returned.
 */
private suspend fun AwaitPointerEventScope.awaitTwoPointerLongPressInvalidation(originalEvent: PointerEvent = currentEvent): PointerEvent? {
    val touchSlopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop

    return withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        do {
            awaitPointerEvent(PointerEventPass.Initial)
        } while (isValidLongPressEvent(originalEvent, currentEvent, touchSlopSquared))
        return@withTimeoutOrNull currentEvent
    }
}

private fun isValidLongPressEvent(
    originalEvent: PointerEvent,
    currentEvent: PointerEvent,
    touchSlopSquared: Float
): Boolean =
    currentEvent.changes.size == 2 && currentEvent.changes.all { it.pressed } && currentEvent.changes.all { change ->
        val original = originalEvent.changes.find { it.id == change.id } ?: return@all false
        (original.position - change.position).getDistanceSquared() < touchSlopSquared
    }

private suspend fun AwaitPointerEventScope.multiPointerDrag(
    onDrag: (PointerEvent) -> Unit
) {
    while (currentEvent.changes.all { it.pressed }) {
        awaitPointerEvent(PointerEventPass.Initial)
        if (currentEvent.changes.any { !it.pressed }) {
            return
        }

        onDrag(currentEvent)
        currentEvent.changes.forEach { it.consume() }
    }
}

private fun PointerEvent.centroid(): Offset =
    changes
        .map { it.position }
        .reduce { acc, offset -> acc + offset }
        .let { Offset(it.x / changes.size, it.y / changes.size) }

private fun calculateSelectedIndex(
    location: Offset,
    menuCenter: Offset,
    itemCount: Int,
    cancelThreshold: Float
): Int? {
    val offset = location - menuCenter
    if (offset.getDistance() < cancelThreshold) {
        return null
    }

    val angle = atan2(offset.y, offset.x) * 180 / PI
    val normalizedAngle = (angle + 90 + 360) % 360
    val sliceSize = 360f / itemCount
    return ((normalizedAngle + (sliceSize / 2)) % 360 / sliceSize).toInt()
        .coerceIn(0, itemCount - 1)
}