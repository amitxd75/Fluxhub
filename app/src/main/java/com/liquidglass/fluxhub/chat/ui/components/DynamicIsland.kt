package com.liquidglass.fluxhub.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.composables.icons.lucide.*
import com.composables.icons.lucide.Lucide
import com.liquidglass.fluxhub.components.LiquidButton
import kotlin.math.PI
import kotlin.math.sin

enum class DynamicIslandState {
    Hidden,
    Collapsed,
    Expanded,
    LongPressMenu
}

data class DynamicIslandData(
    val title: String = "Thinking...",
    val modelName: String? = null,
    val assistantAvatar: String? = null,
    val state: DynamicIslandState = DynamicIslandState.Hidden,
    val tokenCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val successMessage: String = "Done",
    val showTokenCount: Boolean = true,
    val showElapsedTime: Boolean = true,
    val triggerId: Long = 0L
)

@Composable
fun DynamicIsland(
    data: DynamicIslandData,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
    onCollapse: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onStopGeneration: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val isVisible = data.state != DynamicIslandState.Hidden
    
    val transition = updateTransition(targetState = data.state, label = "DynamicIslandTransition")
    
    val width by transition.animateDp(
        transitionSpec = {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        },
        label = "width"
    ) { state ->
        when (state) {
            DynamicIslandState.Hidden -> 90.dp
            DynamicIslandState.Collapsed -> {
                if (data.showTokenCount || data.showElapsedTime) 160.dp else 120.dp
            }
            DynamicIslandState.Expanded -> 312.dp
            DynamicIslandState.LongPressMenu -> 280.dp
        }
    }

    val height by transition.animateDp(
        transitionSpec = {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        },
        label = "height"
    ) { state ->
        when (state) {
            DynamicIslandState.Hidden -> 36.dp
            DynamicIslandState.Collapsed -> 36.dp
            DynamicIslandState.Expanded -> 150.dp
            DynamicIslandState.LongPressMenu -> 160.dp
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.5f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = 300f
            ),
            transformOrigin = TransformOrigin(0.5f, 0f)
        ) + fadeIn(animationSpec = tween(150)),
        exit = scaleOut(
            targetScale = 0.5f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = 300f
            ),
            transformOrigin = TransformOrigin(0.5f, 0f)
        ) + fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width, height)
                .clip(RoundedCornerShape(40.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(40.dp) },
                    effects = {
                        blur(10f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color(0xFF141720).copy(alpha = 0.90f))
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .combinedClickable(
                    onClick = {
                        if (data.state == DynamicIslandState.Collapsed) {
                            onExpand()
                        } else {
                            onCollapse()
                        }
                    },
                    onLongClick = {
                        if (data.state == DynamicIslandState.Collapsed) {
                            onLongPress()
                        }
                    }
                )
        ) {
            if (data.state == DynamicIslandState.Expanded) {
                WaveAnimationBackground()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = data.state,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(90))
                    },
                    label = "content"
                ) { targetState ->
                    when (targetState) {
                        DynamicIslandState.Collapsed -> CollapsedContent(data)
                        DynamicIslandState.Expanded -> ExpandedContent(data)
                        DynamicIslandState.LongPressMenu -> LongPressMenuContent(data, onStopGeneration, onCollapse, backdrop)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedContent(data: DynamicIslandData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (data.isFailed) {
            AnimatedXMark(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Failed",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.Bold
                )
            )
        } else if (data.isCompleted) {
            AnimatedCheckmark(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = data.successMessage,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color(0xFF34C759),
                    fontWeight = FontWeight.Bold
                )
            )
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing)
                ),
                label = "rotation"
            )
            
            Icon(
                imageVector = Lucide.Loader,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = angle }
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            if (data.showTokenCount && data.tokenCount > 0) {
                Text(
                    text = "${data.tokenCount}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "tok",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            if (data.showElapsedTime) {
                Text(
                    text = "${data.elapsedSeconds}s",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun AnimatedCheckmark(modifier: Modifier = Modifier) {
    val circleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        circleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
        checkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    }
    
    val greenColor = Color(0xFF34C759)
    
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val radius = (size.minDimension / 2) - strokeWidth
        val center = center
        
        if (circleProgress.value > 0f) {
            drawArc(
                color = greenColor,
                startAngle = -90f,
                sweepAngle = 360f * circleProgress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        
        if (checkProgress.value > 0f) {
            val checkPath = Path().apply {
                val startX = center.x - radius * 0.4f
                val startY = center.y + radius * 0.1f
                val midX = center.x - radius * 0.1f
                val midY = center.y + radius * 0.4f
                val endX = center.x + radius * 0.5f
                val endY = center.y - radius * 0.35f
                
                moveTo(startX, startY)
                
                val firstStrokeProgress = (checkProgress.value / 0.4f).coerceIn(0f, 1f)
                if (firstStrokeProgress > 0f) {
                    lineTo(
                        startX + (midX - startX) * firstStrokeProgress,
                        startY + (midY - startY) * firstStrokeProgress
                    )
                }
                
                if (checkProgress.value > 0.4f) {
                    val secondStrokeProgress = ((checkProgress.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    lineTo(
                        midX + (endX - midX) * secondStrokeProgress,
                        midY + (endY - midY) * secondStrokeProgress
                    )
                }
            }
            
            drawPath(
                path = checkPath,
                color = greenColor,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun AnimatedXMark(modifier: Modifier = Modifier) {
    val circleProgress = remember { Animatable(0f) }
    val xProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        circleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
        xProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    }
    
    val redColor = Color(0xFFFF3B30)
    
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val radius = (size.minDimension / 2) - strokeWidth
        val center = center
        
        if (circleProgress.value > 0f) {
            drawArc(
                color = redColor,
                startAngle = -90f,
                sweepAngle = 360f * circleProgress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        
        if (xProgress.value > 0f) {
            val offset = radius * 0.4f
            
            val firstStrokeProgress = (xProgress.value / 0.5f).coerceIn(0f, 1f)
            if (firstStrokeProgress > 0f) {
                val startX1 = center.x - offset
                val startY1 = center.y - offset
                val endX1 = center.x + offset
                val endY1 = center.y + offset
                
                val path1 = Path().apply {
                    moveTo(startX1, startY1)
                    lineTo(
                        startX1 + (endX1 - startX1) * firstStrokeProgress,
                        startY1 + (endY1 - startY1) * firstStrokeProgress
                    )
                }
                drawPath(
                    path = path1,
                    color = redColor,
                    style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            
            if (xProgress.value > 0.5f) {
                val secondStrokeProgress = ((xProgress.value - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val startX2 = center.x + offset
                val startY2 = center.y - offset
                val endX2 = center.x - offset
                val endY2 = center.y + offset
                
                val path2 = Path().apply {
                    moveTo(startX2, startY2)
                    lineTo(
                        startX2 + (endX2 - startX2) * secondStrokeProgress,
                        startY2 + (endY2 - startY2) * secondStrokeProgress
                    )
                }
                drawPath(
                    path = path2,
                    color = redColor,
                    style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun ExpandedContent(data: DynamicIslandData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = data.assistantAvatar ?: "🤖",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column {
                val title = when {
                    data.isFailed -> "Error Occurred"
                    data.isCompleted -> data.successMessage
                    else -> "AI is responding"
                }
                val titleColor = when {
                    data.isFailed -> Color(0xFFFF3B30)
                    data.isCompleted -> Color(0xFF34C759)
                    else -> Color.White
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = titleColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (!data.isCompleted && !data.isFailed && data.modelName != null) {
                    Text(
                        text = data.modelName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
        
        if (!data.isCompleted && !data.isFailed) {
            Spacer(modifier = Modifier.height(16.dp))
            ThinkingWaveform()
        }
    }
}

@Composable
private fun LongPressMenuContent(
    data: DynamicIslandData,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    backdrop: Backdrop
) {
    val emptyBackdrop = rememberBackdrop(backdrop) {}

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        LiquidButton(
            onClick = {
                onStop()
                onCancel()
            },
            backdrop = emptyBackdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            isInteractive = true,
            surfaceColor = Color(0xFFFF3B30).copy(alpha = 0.2f),
            tint = Color(0xFFFF453A)
        ) {
           Row(verticalAlignment = Alignment.CenterVertically) {
               Icon(Lucide.Square, contentDescription = null, modifier = Modifier.size(16.dp))
               Spacer(Modifier.width(8.dp))
               Text("Stop Generation", fontWeight = FontWeight.Bold)
           }
        }
        
        Spacer(Modifier.height(8.dp))
        
        LiquidButton(
            onClick = onCancel,
            backdrop = emptyBackdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            isInteractive = true,
            surfaceColor = Color.White.copy(alpha = 0.1f),
            tint = Color.White
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun WaveAnimationBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    val color1 = Color(0xFF007AFF).copy(alpha = 0.15f)
    val color2 = Color(0xFF5856D6).copy(alpha = 0.15f)
    
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "phase1"
    )
    
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = LinearEasing)
        ),
        label = "phase2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        val step = 50
        
        val path1 = Path().apply {
            moveTo(0f, centerY)
            for (x in 0..width.toInt() step step) {
                val xFloat = x.toFloat()
                val y = centerY + 10.dp.toPx() * sin((xFloat / width) * 2 * PI + phase1).toFloat()
                lineTo(xFloat, y)
            }
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(path1, color1)
        
        val path2 = Path().apply {
            moveTo(0f, centerY)
            for (x in 0..width.toInt() step step) {
                val xFloat = x.toFloat()
                val y = centerY + 8.dp.toPx() * sin((xFloat / width) * 3 * PI + phase2).toFloat()
                lineTo(xFloat, y)
            }
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(path2, color2)
    }
}

@Composable
private fun ThinkingWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(30.dp)
    ) {
        repeat(5) { index ->
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightScale)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
        }
    }
}
