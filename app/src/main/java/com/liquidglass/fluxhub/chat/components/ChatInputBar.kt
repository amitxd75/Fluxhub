package com.liquidglass.fluxhub.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.capsule.ContinuousRoundedRectangle
import com.liquidglass.fluxhub.components.LiquidButton

/**
 * Chat input bar - Clean, reliable Liquid Glass message bar
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    isStreaming: Boolean = false,
    backdrop: Backdrop,
    onInteractionChanged: (Boolean) -> Unit = {},
    onPickImage: () -> Unit = {},
    onOpenToolbox: () -> Unit = {}
) {
    val isGenerating = isLoading || isStreaming
    val hasText = text.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Attachment Button (+)
        LiquidButton(
            onClick = onPickImage,
            backdrop = backdrop,
            modifier = Modifier.size(44.dp),
            isInteractive = true,
            onPressed = onInteractionChanged,
            tint = Color.White.copy(alpha = 0.15f)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Attachment",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Toolbox Button
        LiquidButton(
            onClick = onOpenToolbox,
            backdrop = backdrop,
            modifier = Modifier.size(44.dp),
            isInteractive = true,
            onPressed = onInteractionChanged,
            tint = Color.White.copy(alpha = 0.15f)
        ) {
            Icon(
                imageVector = Lucide.SlidersHorizontal,
                contentDescription = "Toolbox",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Message Input Box
        Box(
            modifier = Modifier
                .weight(1f)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(22.dp) },
                    effects = {
                        vibrancy()
                        blur(10f.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.18f))
                    }
                )
                .heightIn(min = 44.dp, max = 130.dp)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Type a message...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )
        }
            
        // Send / Stop Button
        LiquidButton(
            onClick = if (isGenerating) onStop else onSend,
            backdrop = backdrop,
            modifier = Modifier.size(44.dp),
            isInteractive = isGenerating || hasText,
            onPressed = onInteractionChanged,
            tint = if (isGenerating) Color(0xFFFF3B30).copy(alpha = 0.85f)
                   else if (hasText) Color(0xFF007AFF).copy(alpha = 0.85f)
                   else Color.White.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isGenerating) "Stop" else "Send",
                tint = if (isGenerating || hasText) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
