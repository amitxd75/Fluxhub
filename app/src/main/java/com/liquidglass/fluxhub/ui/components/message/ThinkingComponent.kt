package com.liquidglass.fluxhub.ui.components.message

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.composables.icons.lucide.*
import com.liquidglass.fluxhub.ui.components.richtext.MarkdownBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Thinking/Reasoning process component
 */
@Composable
fun ThinkingComponent(
    content: String,
    isThinking: Boolean,
    backdrop: Backdrop,
    shouldCollapse: Boolean = false,
    startTime: Long = remember { System.currentTimeMillis() },
    modifier: Modifier = Modifier
) {
    // Collapse state
    var expanded by remember(shouldCollapse, isThinking) { 
        mutableStateOf(!shouldCollapse || isThinking) 
    }
    
    // Thinking timer
    var duration by remember { mutableStateOf(0L) }
    
    // Real-time timer update
    LaunchedEffect(isThinking) {
        if (isThinking) {
            while (isActive) {
                duration = System.currentTimeMillis() - startTime
                delay(100)
            }
        }
    }
    
    // Auto-collapse logic
    LaunchedEffect(shouldCollapse, isThinking) {
        if (shouldCollapse && !isThinking) {
            delay(800)
            expanded = false
        }
    }
    
    // Shimmer animation while thinking
    val shimmerAlpha = if (isThinking) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_alpha"
        ).value
    } else {
        0.6f
    }
    
    // Formatted elapsed duration
    val durationText = remember(duration) {
        if (duration > 0) {
            val seconds = duration / 1000.0
            "(${String.format("%.1f", seconds)}s)"
        } else ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Lucide.Sparkles,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .alpha(shimmerAlpha),
                    tint = Color(0xFF007AFF)
                )
                
                Text(
                    text = if (isThinking) "Thinking..." else "Thought process",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = shimmerAlpha),
                    letterSpacing = 0.5.sp
                )
                
                if (durationText.isNotEmpty()) {
                    Text(
                        text = durationText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = if (isThinking) shimmerAlpha * 0.7f else 0.5f)
                    )
                }
            }
            
            Icon(
                imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(14.dp),
                tint = Color.White.copy(alpha = 0.6f)
            )
        }

        if (expanded) {
            val scrollState = rememberScrollState()
            
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        return available
                    }
                }
            }
            
            Column {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .nestedScroll(nestedScrollConnection)
                        .verticalScroll(scrollState)
                        .graphicsLayer()
                ) {
                    MarkdownBlock(
                        content = content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }
        }
    }
}
