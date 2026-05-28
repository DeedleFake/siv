package dev.deedles.simpleimageviewer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2

data class RadialMenuItem(
    val label: String,
    val onSelect: () -> Unit
)

@Composable
fun RadialMenu(
    items: List<RadialMenuItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var menuCenter by remember { mutableStateOf<Offset?>(null) }
    var currentTouch by remember { mutableStateOf(Offset.Zero) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    val menuAlpha by animateFloatAsState(if (menuCenter != null) 1f else 0f, label = "Alpha")

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(items) {
                awaitPointerEventScope {
                    while (true) {
                        // 1. Wait for exactly 2 fingers to go down
                        val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                        if (initialEvent.changes.size == 2 && menuCenter == null) {
                            val centroid = Offset(
                                initialEvent.changes.sumOf { it.position.x.toDouble() }.toFloat() / 2,
                                initialEvent.changes.sumOf { it.position.y.toDouble() }.toFloat() / 2
                            )
                            
                            // 2. Try to detect a long press (consistent 400ms timeout)
                            val isLongPress = withTimeoutOrNull(400) {
                                while (true) {
                                    val moveEvent = awaitPointerEvent(PointerEventPass.Initial)
                                    if (moveEvent.changes.size != 2) break
                                    
                                    val newCentroid = Offset(
                                        (moveEvent.changes[0].position.x + moveEvent.changes[1].position.x) / 2,
                                        (moveEvent.changes[0].position.y + moveEvent.changes[1].position.y) / 2
                                    )
                                    
                                    // If fingers move significantly, it's a zoom. Abort menu.
                                    if ((newCentroid - centroid).getDistance() > 15f) break
                                }
                                false
                            } ?: true // If timeout expires, it's a long press

                            if (isLongPress) {
                                menuCenter = centroid
                                currentTouch = centroid
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                
                                // 3. Menu active: Handle selection and consume all events
                                while (true) {
                                    val dragEvent = awaitPointerEvent(PointerEventPass.Initial)
                                    dragEvent.changes.forEach { it.consume() }

                                    if (dragEvent.type == PointerEventType.Release || dragEvent.changes.isEmpty()) {
                                        if (selectedIndex != -1) {
                                            items[selectedIndex].onSelect()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        menuCenter = null
                                        selectedIndex = -1
                                        break
                                    }
                                    
                                    currentTouch = Offset(
                                        dragEvent.changes.sumOf { it.position.x.toDouble() }.toFloat() / dragEvent.changes.size,
                                        dragEvent.changes.sumOf { it.position.y.toDouble() }.toFloat() / dragEvent.changes.size
                                    )
                                    
                                    val diff = currentTouch - menuCenter!!
                                    val dist = diff.getDistance()
                                    
                                    if (dist > with(density) { 30.dp.toPx() }) {
                                        val angle = atan2(diff.y, diff.x) * 180 / PI
                                        val normalizedAngle = (angle + 90 + 360) % 360
                                        val sliceSize = 360f / items.size
                                        val newIndex = ((normalizedAngle + (sliceSize / 2)) % 360 / sliceSize).toInt()
                                        
                                        if (newIndex != selectedIndex && newIndex < items.size) {
                                            selectedIndex = newIndex
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else {
                                        selectedIndex = -1
                                    }
                                }
                            } else {
                                // 4. Movement was detected: It's a zoom. 
                                // Wait for all fingers to lift before allowing menu again.
                                while (true) {
                                    val releaseEvent = awaitPointerEvent(PointerEventPass.Initial)
                                    if (releaseEvent.changes.none { it.pressed }) break
                                }
                            }
                        }
                    }
                }
            }
    ) {
        content()
        
        if (menuAlpha > 0f && menuCenter != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = menuCenter!!
                val radius = 115.dp.toPx()
                val cancelRadius = 30.dp.toPx()
                
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f * menuAlpha),
                    radius = radius + 20.dp.toPx(),
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
                            size = androidx.compose.ui.geometry.Size(arcPathRadius * 2, arcPathRadius * 2),
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    // Curved Text Implementation
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val textRadius = cancelRadius + (radius - cancelRadius) * 0.55f
                        
                        val paint = android.graphics.Paint().apply {
                            this.color = color.toArgb()
                            this.textSize = with(density) { (if (isSelected) 16.sp else 14.sp).toPx() }
                            this.isAntiAlias = true
                            this.textAlign = android.graphics.Paint.Align.CENTER
                            this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
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
                val isCancelSelected = selectedIndex == -1
                
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
