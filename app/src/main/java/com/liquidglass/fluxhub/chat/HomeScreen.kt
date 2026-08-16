package com.liquidglass.fluxhub.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.liquidglass.fluxhub.components.LiquidButton
import com.liquidglass.fluxhub.components.LiquidConfirmationDialog
import com.liquidglass.fluxhub.components.PersonaCard
import com.liquidglass.fluxhub.data.Personas
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.liquidglass.fluxhub.ui.theme.GlassTypography
import com.liquidglass.fluxhub.ui.theme.GlassTextStyles

/**
 * Home Screen
 */
@Composable
fun HomeScreen(
    backdrop: Backdrop,
    bottomPadding: PaddingValues = PaddingValues(0.dp),
    onNavigateToChat: () -> Unit,
    onNavigateToAssistantSelection: () -> Unit,
    onQuickPrompt: (String) -> Unit = { },
    viewModel: ChatViewModel = viewModel()
) {
    val textStyles = GlassTextStyles.create(
        colorMode = viewModel.textColorMode,
        shadowEnabled = viewModel.textShadowEnabled
    )
    
    val recentConversations = viewModel.conversations.take(5)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val calendar = remember { Calendar.getInstance() }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..18 -> "Good afternoon"
        else -> "Good evening"
    }
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
    val dateString = dateFormat.format(calendar.time)

    var showStatsDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottomPadding),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Greeting Header & Date
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = dateString.uppercase(),
                        style = textStyles.label
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = greeting,
                        style = textStyles.displayLarge
                    )
                    BasicText(
                        text = "Ready to start a new conversation?",
                        style = textStyles.bodyLarge.copy(color = textStyles.baseColor.copy(alpha = 0.8f)),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Version badge (Top End)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousRoundedRectangle(12.dp) },
                            effects = { blur(10.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) }
                        )
                        .clickable { showChangelogDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "v1.2.0",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        
        // 2. Quick Actions Grid
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main button: New Chat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { ContinuousRoundedRectangle(24.dp) },
                                effects = {
                                    vibrancy()
                                    blur(10.dp.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(Color(0xFF007AFF).copy(alpha = 0.85f))
                                }
                            )
                            .clickable { 
                                viewModel.createNewConversation()
                                onNavigateToChat()
                            }
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Lucide.Plus, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            
                            Column {
                                Text(
                                    text = "New Chat",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Begin your journey",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                    
                    // Right cards
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Random chat
                        QuickActionCard(
                            title = "Random Chat",
                            subtitle = "Discover personas",
                            icon = Lucide.Sparkles,
                            color = Color(0xFFAF52DE),
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onClick = {
                                val randomPersona = Personas.all.random()
                                viewModel.createNewConversation(randomPersona.systemPrompt, randomPersona.name)
                                onNavigateToChat()
                            }
                        )
                        
                        // Today's Stats
                        val todayStart = remember {
                            Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }
                        val todayConversations = viewModel.conversations.count { it.createdAt >= todayStart }
                        val totalConversations = viewModel.conversations.size
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { ContinuousRoundedRectangle(16.dp) },
                                    effects = {
                                        vibrancy()
                                        blur(8.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(Color(0xFF34C759).copy(alpha = 0.75f))
                                    }
                                )
                                .clickable { showStatsDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Activity Stats",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Today $todayConversations · Total $totalConversations",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    Lucide.Zap,
                                    null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(24.dp)) }

        // 3. Featured Personas Carousel
        item {
            Column {
                PaddingLabel(text = "Featured Personas", icon = Lucide.Sparkles)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(Personas.all) { persona ->
                        PersonaCard(
                            persona = persona,
                            backdrop = backdrop,
                            blurStrength = viewModel.glassBlur,
                            onClick = {
                                viewModel.createNewConversation(persona.systemPrompt, persona.name)
                                onNavigateToChat()
                            },
                            modifier = Modifier.width(200.dp).height(130.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 4. Quick Prompts
        item {
            Column {
                PaddingLabel(text = "Explore Prompts", icon = Lucide.Zap)
                
                val prompts = listOf(
                    "Write a Python script to parse JSON", "Explain quantum entanglement",
                    "Compose a poem about the spring", "Create a personalized workout plan",
                    "Translate text naturally", "Analyze this business model",
                    "Recommend sci-fi novels", "How to brew great espresso"
                )
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(prompts) { prompt ->
                        QuickPromptChip(
                            text = prompt, 
                            backdrop = backdrop,
                            blurStrength = viewModel.glassBlur
                        ) { 
                            onQuickPrompt(prompt) 
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 5. Recent Conversations
        if (recentConversations.isNotEmpty()) {
            item {
                Column {
                    PaddingLabel(text = "Recent Chats", icon = Lucide.History)
                    
                    var conversationToDeleteOnHome by remember { mutableStateOf<com.liquidglass.fluxhub.data.ConversationEntity?>(null) }
                    
                    conversationToDeleteOnHome?.let { conv ->
                        LiquidConfirmationDialog(
                            onDismissRequest = { conversationToDeleteOnHome = null },
                            onConfirm = {
                                viewModel.deleteConversation(conv.id)
                                conversationToDeleteOnHome = null
                            },
                            title = "Delete Chat",
                            message = "Are you sure you want to delete \"${conv.title}\"? This action cannot be undone.",
                            confirmText = "Delete",
                            icon = Lucide.Trash2,
                            backdrop = backdrop
                        )
                    }
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentConversations, key = { it.id }) { conversation ->
                            Box(
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(118.dp)
                                    .clip(ContinuousRoundedRectangle(20.dp))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.22f),
                                        shape = ContinuousRoundedRectangle(20.dp)
                                    )
                                    .drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { ContinuousRoundedRectangle(20.dp) },
                                        effects = {
                                            vibrancy()
                                            blur(viewModel.glassBlur.dp.toPx())
                                        },
                                        onDrawSurface = {
                                            drawRect(Color.White.copy(alpha = 0.14f))
                                        }
                                    )
                                    .clickable {
                                        viewModel.switchConversation(conversation.id)
                                        onNavigateToChat()
                                    }
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(ContinuousCapsule)
                                                    .background(Color(0xFF007AFF).copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Lucide.MessageCircle, 
                                                    null, 
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            val timeDiff = System.currentTimeMillis() - conversation.updatedAt
                                            val timeText = when {
                                                timeDiff < 60000 -> "Just now"
                                                timeDiff < 3600000 -> "${timeDiff / 60000}m ago"
                                                timeDiff < 86400000 -> "${timeDiff / 3600000}h ago"
                                                else -> "${timeDiff / 86400000}d ago"
                                            }
                                            Text(
                                                text = timeText,
                                                style = TextStyle(
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                        
                                        // Delete Button on Recent Chat Card
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(ContinuousCapsule)
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .clickable { conversationToDeleteOnHome = conversation },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Lucide.Trash2,
                                                contentDescription = "Delete",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = conversation.title,
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 3f)
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Stats Dialog
    if (showStatsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showStatsDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousRoundedRectangle(28.dp) },
                        effects = {
                            vibrancy()
                            blur(20.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(0.15f))
                        }
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        "My Statistics",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    val todayStart = remember {
                        Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    val todayCount = viewModel.conversations.count { it.createdAt >= todayStart }
                    val totalCount = viewModel.conversations.size
                    val progressRatio = if (totalCount > 0) (todayCount.toFloat() / totalCount.coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
                    
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier.size(100.dp),
                            color = Color(0xFF34C759),
                            trackColor = Color.White.copy(alpha = 0.1f),
                            strokeWidth = 8.dp,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$todayCount",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                "Today's Chats",
                                style = TextStyle(
                                    color = Color.White.copy(0.6f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                icon = Lucide.MessageCircle,
                                label = "Total Chats",
                                value = "$totalCount",
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                icon = Lucide.Zap,
                                label = "Today's Activity",
                                value = "$todayCount",
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                icon = Lucide.Bot,
                                label = "Current Model",
                                value = viewModel.model.ifBlank { "AUTO" }.uppercase().take(6),
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                icon = Lucide.Award,
                                label = "Exploration",
                                value = "Explorer",
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    LiquidButton(
                        onClick = { showStatsDialog = false },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        isInteractive = true,
                        tint = Color(0xFF007AFF).copy(0.8f)
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Changelog Dialog
    if (showChangelogDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showChangelogDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousRoundedRectangle(28.dp) },
                        effects = {
                            vibrancy()
                            blur(20.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(0.15f))
                        }
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column {
                        Text(
                            "Release Notes v1.2.0",
                            style = TextStyle(
                                 color = Color.White,
                                 fontSize = 18.sp,
                                 fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "2026-08-16",
                            style = TextStyle(
                                color = Color.White.copy(0.5f),
                                fontSize = 12.sp
                            )
                        )
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val updates = listOf(
                            "🧠 New: 5-level unified Thinking Mode with Auto reasoning effort",
                            "🎛️ New: Liquid Glass blur strength & hardness slider with presets",
                            "⚡ Performance: 60-120 FPS high-speed conversation drawer and smooth typing",
                            "🎨 Polished: Redesigned chat options toolbox and frosted history cards"
                        )
                        updates.forEach { update ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "• ",
                                    style = TextStyle(color = Color(0xFFAF52DE), fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    update,
                                    style = TextStyle(
                                        color = Color.White.copy(0.9f),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                )
                            }
                        }
                    }
                    
                    LiquidButton(
                        onClick = { showChangelogDialog = false },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        isInteractive = true,
                        tint = Color(0xFF007AFF).copy(0.8f)
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(80.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(16.dp) },
                effects = { blur(0f) },
                onDrawSurface = { drawRect(Color.White.copy(0.08f)) }
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
            Column {
                Text(
                    value,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    label,
                    style = TextStyle(
                        color = Color.White.copy(0.5f),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(16.dp) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(color.copy(alpha = 0.6f))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PaddingLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = text,
            style = GlassTypography.bodyMedium
        )
    }
}

@Composable
private fun QuickPromptChip(
    text: String, 
    backdrop: Backdrop, 
    blurStrength: Float = 16f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(ContinuousRoundedRectangle(14.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = ContinuousRoundedRectangle(14.dp)
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(14.dp) },
                effects = {
                    vibrancy()
                    blur(blurStrength.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.12f))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 3f)
            )
        )
    }
}
