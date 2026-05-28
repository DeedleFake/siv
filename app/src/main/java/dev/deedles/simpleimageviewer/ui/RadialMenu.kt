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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    
    val menuAlpha by animateFloatAsState(if (menuCenter != null) 1f else 0f, label = "Alpha")

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(items) {
                awaitPointerEventScope {
                    while (true) {
                        // Use Initial pass to intercept before ZoomableAsyncImage
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val changes = event.changes
                        
                        if (changes.size == 2 && menuCenter == null) {
                            val centroid = Offset(
                                (changes[0].position.x + changes[1].position.x) / 2,
                                (changes[0].position.y + changes[1].position.y) / 2
                            )
                            
                            val startTime = System.currentTimeMillis()
                            var isLongPress = true
                            
                            // Tighter slop: If they move more than 10 pixels, cancel menu
                            while (System.currentTimeMillis() - startTime < 400) {
                                val moveEvent = awaitPointerEvent(PointerEventPass.Initial)
                                if (moveEvent.changes.size != 2) {
                                    isLongPress = false
                                    break
                                }
                                val newCentroid = Offset(
                                    (moveEvent.changes[0].position.x + moveEvent.changes[1].position.x) / 2,
                                    (moveEvent.changes[0].position.y + moveEvent.changes[1].position.y) / 2
                                )
                                if ((newCentroid - centroid).getDistance() > 10f) {
                                    isLongPress = false
                                    break
                                }
                            }
                            
                            if (isLongPress) {
                                menuCenter = centroid
                                currentTouch = centroid
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                
                                // Menu is active! Consume everything to stop zooming
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
                                        // Normalize: 0 is Top, 90 is Right, etc.
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
                val radius = 100.dp.toPx()
                
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f * menuAlpha),
                    radius = radius + 20.dp.toPx(),
                    center = center
                )
                
                items.forEachIndexed { index, item ->
                    val sliceAngle = 360f / items.size
                    val midAngle = index * sliceAngle - 90f
                    val startAngle = midAngle - (sliceAngle / 2f)
                    
                    val isSelected = index == selectedIndex
                    val color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                    
                    if (isSelected) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.25f),
                            startAngle = startAngle,
                            sweepAngle = sliceAngle,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
                    }

                    val textRad = midAngle * PI / 180
                    val textPos = Offset(
                        center.x + (radius * 0.65f * cos(textRad)).toFloat(),
                        center.y + (radius * 0.65f * sin(textRad)).toFloat()
                    )
                    
                    val textLayout = textMeasurer.measure(
                        item.label,
                        style = TextStyle(color = color, fontSize = if (isSelected) 17.sp else 15.sp)
                    )
                    
                    drawText(
                        textLayout,
                        topLeft = Offset(textPos.x - textLayout.size.width / 2, textPos.y - textLayout.size.height / 2)
                    )
                }
                
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f * menuAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
