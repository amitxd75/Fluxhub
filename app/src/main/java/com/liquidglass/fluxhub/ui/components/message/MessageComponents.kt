package com.liquidglass.fluxhub.ui.components.message

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.kyant.backdrop.Backdrop
import com.liquidglass.fluxhub.components.LiquidButton

/**
 * Message avatar component
 */
@Composable
fun MessageAvatar(
    isUser: Boolean,
    modelName: String? = null,
    userName: String = "You",
    userAvatar: String = "",
    timestamp: Long? = null,
    modifier: Modifier = Modifier
) {
    val aiIcon = remember(modelName) {
        val name = modelName?.lowercase() ?: ""
        when {
            name.contains("gpt") || name.contains("openai") -> Lucide.Zap
            name.contains("claude") -> Lucide.Sparkles
            name.contains("gemini") -> Lucide.Star
            name.contains("deepseek") -> Lucide.Compass
            name.contains("qwen") || name.contains("aliyun") -> Lucide.Cloud
            name.contains("llama") || name.contains("meta") -> Lucide.Globe
            else -> Lucide.Bot
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isUser) {
            // AI avatar on the left
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = aiIcon,
                        contentDescription = "AI",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = modelName ?: "AI",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                timestamp?.let {
                    Text(
                        text = formatTimestamp(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            // User avatar on the right
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                timestamp?.let {
                    Text(
                        text = formatTimestamp(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (userAvatar.isNotEmpty()) {
                        Text(
                            text = userAvatar,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = Lucide.User,
                            contentDescription = "User",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Message action buttons - Copy, Regenerate, Edit, Delete
 * Uses Liquid Button styling
 */
@Composable
fun MessageActionButtons(
    content: String,
    isUser: Boolean,
    backdrop: Backdrop,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showCopiedHint by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCopiedHint) {
        if (showCopiedHint) {
            kotlinx.coroutines.delay(2000)
            showCopiedHint = false
        }
    }
    
    FlowRow(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        // Copy button
        LiquidActionButton(
            icon = if (showCopiedHint) Lucide.Check else Lucide.Copy,
            contentDescription = if (showCopiedHint) "Copied" else "Copy",
            backdrop = backdrop,
            onClick = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("message", content))
                    )
                    showCopiedHint = true
                    com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                        message = "Copied to clipboard",
                        avatar = "📝"
                    )
                }
            }
        )
        
        // Regenerate (AI message only)
        if (!isUser && onRegenerate != null) {
            LiquidActionButton(
                icon = Lucide.RefreshCw,
                contentDescription = "Regenerate",
                backdrop = backdrop,
                onClick = onRegenerate
            )
        }
        
        // Edit button
        if (onEdit != null) {
            LiquidActionButton(
                icon = Lucide.Pencil,
                contentDescription = "Edit",
                backdrop = backdrop,
                onClick = onEdit
            )
        }
        
        // Delete button
        if (onDelete != null) {
            LiquidActionButton(
                icon = Lucide.Trash2,
                contentDescription = "Delete",
                backdrop = backdrop,
                tint = Color(0xFFFF453A).copy(alpha = 0.6f),
                onClick = onDelete
            )
        }
    }
}

/**
 * Single liquid glass action button
 * Uses pointerInput to consume drag events and prevent unwanted parent scrolling
 */
@Composable
private fun LiquidActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    backdrop: Backdrop,
    tint: Color = Color.White.copy(alpha = 0.8f), 
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier
            .size(24.dp)
            .pointerInput(Unit) {
                detectDragGestures { _, _ -> }
            },
        isInteractive = true,
        tint = tint,
        padding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(12.dp),
            tint = Color(0xFF1C1C1E).copy(alpha = 0.7f)
        )
    }
}

/**
 * Message actions bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    content: String,
    isUser: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEditAndResend: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Copy
            SheetActionCard(
                icon = Lucide.Copy,
                text = "Copy",
                onClick = {
                    onCopy()
                    onDismiss()
                }
            )
            
            // Edit & Resend (User message only)
            if (isUser && onEditAndResend != null) {
                SheetActionCard(
                    icon = Lucide.Pencil,
                    text = "Edit & Resend",
                    onClick = {
                        onEditAndResend()
                        onDismiss()
                    }
                )
            }
            
            // Regenerate (AI message only)
            if (!isUser && onRegenerate != null) {
                SheetActionCard(
                    icon = Lucide.RefreshCw,
                    text = "Regenerate",
                    onClick = {
                        onRegenerate()
                        onDismiss()
                    }
                )
            }
            
            // Edit (AI message only)
            if (!isUser && onEdit != null) {
                SheetActionCard(
                    icon = Lucide.Pencil,
                    text = "Edit",
                    onClick = {
                        onEdit()
                        onDismiss()
                    }
                )
            }
            
            // Delete (with confirmation)
            if (onDelete != null) {
                if (showDeleteConfirm) {
                    SheetActionCard(
                        icon = Lucide.TriangleAlert,
                        text = "Confirm Delete? Tap to delete",
                        containerColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    )
                } else {
                    SheetActionCard(
                        icon = Lucide.Trash2,
                        text = "Delete",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        onClick = {
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
