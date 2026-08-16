package com.liquidglass.fluxhub.chat

import android.util.Log
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil3.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.kyant.backdrop.highlight.Highlight
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.liquidglass.fluxhub.components.LiquidButton
import com.liquidglass.fluxhub.components.LiquidConfirmationDialog
import com.liquidglass.fluxhub.data.ConversationEntity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.liquidglass.fluxhub.ui.components.richtext.MarkdownBlock
import com.liquidglass.fluxhub.ui.components.richtext.ProvideHighlighter
import com.liquidglass.fluxhub.ui.components.message.MessageAvatar
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import com.liquidglass.fluxhub.ui.components.message.MessageActionButtons
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.liquidglass.fluxhub.ui.components.message.MessageActionsSheet
import com.liquidglass.fluxhub.ui.components.message.ThinkingComponent
import com.composables.icons.lucide.*
import com.composables.icons.lucide.ChevronRight
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawBehind
import com.liquidglass.fluxhub.utils.ImeLazyListAutoScroller
import com.liquidglass.fluxhub.chat.components.TypingIndicator
import com.liquidglass.fluxhub.chat.components.ChatInputBar

private const val TAG = "ChatScreen"

@Composable
fun ChatScreen(
    backdrop: Backdrop,
    bottomPadding: PaddingValues,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAssistantSelection: () -> Unit = {},
    viewModel: ChatViewModel = viewModel(),
    listState: LazyListState = rememberLazyListState(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    initialPrompt: String? = null,
    onPromptConsumed: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf(viewModel.inputText) }
    
    LaunchedEffect(viewModel.inputText) {
        if (viewModel.inputText.isNotEmpty() && inputText != viewModel.inputText) {
            inputText = viewModel.inputText
        }
    }
    
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            inputText = initialPrompt
            viewModel.inputText = initialPrompt
            onPromptConsumed()
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    
    var isInteractingWithButtons by remember { mutableStateOf(false) }
    val isKeyboardVisible = rememberIsKeyboardVisible()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val ScrollBottomKey = "scroll_bottom_spacer"
    
    val visibleMessages by remember { derivedStateOf {
        viewModel.messages.filter { it.role != "system" }
    } }
    val messageCount by remember { derivedStateOf { visibleMessages.size } }
    val isStreaming by remember { derivedStateOf {
        viewModel.messages.lastOrNull()?.isStreaming == true
    } }
    val loadingState by rememberUpdatedState(isStreaming || viewModel.isLoading)
    
    var prevMessageCount by remember { mutableIntStateOf(messageCount) }
    LaunchedEffect(messageCount) {
        if (messageCount > prevMessageCount && !listState.isScrollInProgress) {
            listState.scrollToItem((messageCount - 1).coerceAtLeast(0))
        }
        prevMessageCount = messageCount
    }
    
    val onSendMessage: () -> Unit = {
        if (inputText.isNotBlank()) {
            val textToSend = inputText
            inputText = ""
            viewModel.inputText = ""
            
            if (viewModel.isEditing()) {
                viewModel.handleMessageEdit(textToSend)
            } else {
                viewModel.sendMessage(textToSend)
            }
            
            scope.launch {
                delay(50)
                keyboardController?.hide()
            }
        }
    }
    
    ProvideHighlighter {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ConversationDrawerContent(
                    conversations = viewModel.conversations,
                    currentConversationId = viewModel.currentConversationId,
                    backdrop = backdrop,
                    onSelectConversation = { id ->
                        viewModel.switchConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { id ->
                        viewModel.deleteConversation(id)
                    },
                    onRenameConversation = { id, newTitle ->
                        viewModel.renameConversation(id, newTitle)
                    },
                    onNewConversation = {
                        viewModel.createNewConversation(showNotification = true)
                        scope.launch { drawerState.close() }
                    },
                    onInteractionChanged = { isInteractingWithButtons = it },
                    assistants = viewModel.assistants,
                    currentAssistant = viewModel.currentAssistant,
                    onSwitchAssistant = { viewModel.switchAssistant(it) },
                    onNavigateToAssistantSelection = onNavigateToAssistantSelection
                )
            },
            gesturesEnabled = !isInteractingWithButtons,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LiquidGlassChatContent(
                    viewModel = viewModel,
                    inputText = inputText,
                    onInputTextChange = { inputText = it },
                    listState = listState,
                    onNavigateToSettings = onNavigateToSettings,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSend = onSendMessage,
                    backdrop = backdrop,
                    bottomPadding = bottomPadding,
                    scope = scope,
                    onInteractionChanged = { isInteractingWithButtons = it },
                    onImageSelected = { viewModel.selectedImageUri = it }
                )
                
                // Error message overlay
                AnimatedVisibility(
                    visible = viewModel.showError,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)
                ) {
                    viewModel.error?.let { errorMsg ->
                        Box(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        vibrancy()
                                        blur(4f.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(Color(0xFFFF3B30).copy(alpha = 0.3f))
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            BasicText(
                                text = errorMsg,
                                style = TextStyle(Color.White, 14.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidGlassChatContent(
    viewModel: ChatViewModel,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    listState: LazyListState,
    onNavigateToSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: () -> Unit,
    backdrop: Backdrop,
    bottomPadding: PaddingValues,
    scope: kotlinx.coroutines.CoroutineScope,
    onInteractionChanged: (Boolean) -> Unit,
    onImageSelected: (android.net.Uri?) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onImageSelected(uri)
    }
    
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onImageSelected(uri)
    }
    
    val context = LocalContext.current
    var cameraOutputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<java.io.File?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraOutputUri != null) {
            onImageSelected(cameraOutputUri)
        }
        if (!success) {
            cameraOutputFile?.delete()
        }
        cameraOutputFile = null
        cameraOutputUri = null
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraOutputFile = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                cameraOutputUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cameraOutputFile!!
                )
                cameraLauncher.launch(cameraOutputUri!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var showModelSelector by remember { mutableStateOf(false) }
    var showAssistantSelector by remember { mutableStateOf(false) }
    var showUploadOptions by remember { mutableStateOf(false) }
    var showToolbox by remember { mutableStateOf(false) }

    val controller = com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController
    var wasLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(viewModel.isLoading) {
        if (viewModel.isLoading) {
            wasLoading = true
            controller.showLoading(
                title = "AI is thinking...",
                modelName = viewModel.model.ifBlank { "AI Assistant" },
                avatar = viewModel.currentAssistant?.avatar ?: "🤖"
            )
        } else if (wasLoading) {
            wasLoading = false
            if (viewModel.showError) {
                controller.showError("Failed")
            } else {
                controller.showSuccess("Done")
            }
        }
    }
    
    LaunchedEffect(viewModel.streamingTokenCount) {
        controller.updateTokenCount(viewModel.streamingTokenCount)
    }

    val isKeyboardVisible = rememberIsKeyboardVisible()
    val effectiveBottomPadding = if (isKeyboardVisible) 0.dp else bottomPadding.calculateBottomPadding()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = effectiveBottomPadding)
    ) {
        // Top Bar Capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(ContinuousCapsule)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.22f),
                        shape = ContinuousCapsule
                    )
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            vibrancy()
                            blur(12f.dp.toPx())
                            lens(16f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.12f))
                        }
                    )
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Menu button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ContinuousCapsule)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onOpenDrawer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Menu,
                        contentDescription = "Conversation List",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // Title and Model info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    BasicText(
                        text = viewModel.currentConversationTitle,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 3f)
                        ),
                        maxLines = 1
                    )
                    
                    // Model & Thinking Level Selector Row
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Model Selector Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { 
                                    viewModel.fetchModels()
                                    showModelSelector = true 
                                }
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val aiIcon = remember(viewModel.model) {
                                    val name = viewModel.model.lowercase()
                                    when {
                                        name.contains("gpt") || name.contains("openai") -> Lucide.Zap
                                        name.contains("claude") -> Lucide.Sparkles
                                        name.contains("gemini") -> Lucide.Star
                                        name.contains("deepseek") -> Lucide.Compass
                                        else -> Lucide.Bot
                                    }
                                }
                                Icon(
                                    imageVector = aiIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(Modifier.width(3.dp))
                                BasicText(
                                    text = viewModel.model.ifBlank { "Select Model" },
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 2f)
                                    ),
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Lucide.ChevronDown,
                                    contentDescription = "Switch Model",
                                    modifier = Modifier.size(9.dp),
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Thinking Level Quick Pill
                        val thinkingLevel = viewModel.getThinkingLevelName()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.cycleThinkingLevel() }
                                .background(
                                    if (thinkingLevel == "Off") Color.White.copy(alpha = 0.08f)
                                    else Color(0xFF007AFF).copy(alpha = 0.35f)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (thinkingLevel == "Off") Color.White.copy(alpha = 0.15f) else Color(0xFF007AFF).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Lucide.Brain,
                                    contentDescription = "Thinking Level",
                                    modifier = Modifier.size(10.dp),
                                    tint = if (thinkingLevel == "Off") Color.White.copy(alpha = 0.6f) else Color.White
                                )
                                Spacer(Modifier.width(2.dp))
                                BasicText(
                                    text = thinkingLevel,
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 2f)
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Action Buttons Row (Assistant + New Chat)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Assistant switch button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(ContinuousCapsule)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { showAssistantSelector = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.currentAssistant?.avatar ?: "🤖",
                            fontSize = 18.sp
                        )
                    }
                    
                    // New Chat button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(ContinuousCapsule)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { viewModel.createNewConversation(showNotification = true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Plus,
                            contentDescription = "New Conversation",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        val displayMessages by remember { derivedStateOf {
            viewModel.messages.filter { it.role != "system" }
        } }
        val loadingStateInner = viewModel.isLoading

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                top = 8.dp, 
                bottom = 8.dp, 
                start = 8.dp, 
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val defaultModel = viewModel.model
            items(
                items = displayMessages, 
                key = { it.id },
                contentType = { it.role }
            ) { message ->
                LiquidGlassChatBubble(
                    message = message,
                    backdrop = backdrop,
                    defaultModel = defaultModel,
                    onRegenerate = { viewModel.regenerate(message.id) },
                    onDelete = { viewModel.deleteMessage(message.id) },
                    onEdit = { 
                        viewModel.startEditingMessage(message.id, message.content)
                    },
                    onSaveImage = { url -> viewModel.saveImageToGallery(url) },
                    hapticFeedbackEnabled = viewModel.hapticFeedbackEnabled
                )
            }
            
            item("scroll_bottom_spacer") {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                )
            }
        }

        // Model Selector Dialog
        if (showModelSelector) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showModelSelector = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(24.dp) },
                            effects = { vibrancy(); blur(16.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White.copy(0.2f)) }
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Model",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        blurRadius = 4f
                                    )
                                ),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            LiquidButton(
                                onClick = { viewModel.fetchModels() },
                                backdrop = backdrop,
                                modifier = Modifier.size(40.dp),
                                isInteractive = true,
                                padding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Lucide.RefreshCw, 
                                    contentDescription = "Refresh", 
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Search box
                        var modelSearchQuery by remember { mutableStateOf("") }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Lucide.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                BasicTextField(
                                    value = modelSearchQuery,
                                    onValueChange = { modelSearchQuery = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp
                                    ),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (modelSearchQuery.isEmpty()) {
                                                BasicText(
                                                    text = "Search models...",
                                                    style = TextStyle(
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontSize = 14.sp
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (modelSearchQuery.isNotEmpty()) {
                                    Icon(
                                        Lucide.X,
                                        contentDescription = "Clear",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { modelSearchQuery = "" }
                                    )
                                }
                            }
                        }

                        val filteredModels = remember(viewModel.availableModels, modelSearchQuery) {
                            if (modelSearchQuery.isBlank()) {
                                viewModel.availableModels
                            } else {
                                viewModel.availableModels.filter { 
                                    it.contains(modelSearchQuery, ignoreCase = true) 
                                }
                            }
                        }
                        
                        // Direct Custom Model Option if typing
                        if (modelSearchQuery.isNotBlank() && !filteredModels.contains(modelSearchQuery.trim())) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.saveModel(modelSearchQuery.trim())
                                        showModelSelector = false
                                    }
                                    .background(Color(0xFF007AFF).copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Use \"${modelSearchQuery.trim()}\"",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Icon(
                                    imageVector = Lucide.ArrowRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        if (filteredModels.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (modelSearchQuery.isBlank()) {
                                        if (viewModel.availableModels.isEmpty()) "No models loaded from active provider yet."
                                        else "No models available."
                                    } else "No matching model for \"$modelSearchQuery\".",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                LiquidButton(
                                    onClick = { viewModel.fetchModels() },
                                    backdrop = backdrop,
                                    modifier = Modifier.height(38.dp),
                                    isInteractive = true,
                                    padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    tint = Color.White.copy(alpha = 0.18f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Lucide.RefreshCw, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Text("Fetch Live Models from Provider", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                items(filteredModels) { modelName ->
                                    val isSelected = modelName == viewModel.model
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.saveModel(modelName)
                                                showModelSelector = false
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.95f),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                shadow = Shadow(
                                                    color = Color.Black.copy(alpha = 0.5f),
                                                    blurRadius = 4f
                                                )
                                            )
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Lucide.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                }
                            }
                        }
                        
                        // Thinking Model Level Selector Section
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Thinking Level / Reasoning Effort",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                            val thinkingLevels = listOf(
                                Pair(0, "Off"),
                                Pair(1024, "Low"),
                                Pair(8192, "Med"),
                                Pair(32000, "High"),
                                Pair(-1, "Auto")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                thinkingLevels.forEach { (budget, label) ->
                                    val isSelected = when (budget) {
                                        0 -> viewModel.thinkingBudget == 0
                                        -1 -> viewModel.thinkingBudget == -1
                                        1024 -> viewModel.thinkingBudget in 1..2048
                                        8192 -> viewModel.thinkingBudget in 2049..10000
                                        else -> viewModel.thinkingBudget > 10000
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.updateThinkingBudget(budget) },
                                        backdrop = backdrop,
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        isInteractive = true,
                                        padding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        tint = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = label,
                                            style = TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            "Tip: You can type any model name above or choose from the list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        LiquidButton(
                            onClick = { showModelSelector = false },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            isInteractive = true,
                            tint = Color(0xFF8E8E93).copy(alpha = 0.5f)
                        ) {
                            Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Assistant Selector Dialog
        if (showAssistantSelector) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showAssistantSelector = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(24.dp) },
                            effects = { vibrancy(); blur(16.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White.copy(0.2f)) }
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "Switch Assistant",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (viewModel.assistants.isEmpty()) {
                            Text(
                                text = "No assistants available, please create one first",
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                items(viewModel.assistants) { assistant ->
                                    val isSelected = assistant.id == viewModel.currentAssistant?.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.switchAssistant(assistant)
                                                showAssistantSelector = false
                                            }
                                            .background(
                                                if (isSelected) Color(0xFF007AFF).copy(alpha = 0.2f)
                                                else Color.Transparent
                                            )
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = assistant.avatar ?: "🤖",
                                                fontSize = 20.sp
                                            )
                                        }
                                        
                                        Spacer(Modifier.width(12.dp))
                                        
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = assistant.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = Color.White,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    shadow = Shadow(
                                                        color = Color.Black.copy(alpha = 0.5f),
                                                        blurRadius = 4f
                                                    )
                                                )
                                            )
                                            if (assistant.systemPrompt.isNotBlank()) {
                                                Text(
                                                    text = assistant.systemPrompt.take(30) + if (assistant.systemPrompt.length > 30) "..." else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Lucide.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF34C759),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        LiquidButton(
                            onClick = {
                                showAssistantSelector = false
                                onNavigateToSettings()
                            },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            isInteractive = true,
                            tint = Color(0xFF007AFF).copy(alpha = 0.3f)
                        ) {
                            Icon(Lucide.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Assistants", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        // Input Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                 if (viewModel.selectedImageUri != null) {
                     Box(
                         modifier = Modifier
                             .padding(horizontal = 16.dp, vertical = 4.dp)
                             .size(72.dp)
                     ) {
                         Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedCornerShape(12.dp) },
                                    effects = { 
                                         vibrancy()
                                         blur(10.dp.toPx()) 
                                    },
                                    onDrawSurface = { drawRect(Color.White.copy(0.1f)) }
                                )
                         ) {
                             AsyncImage(
                                 model = viewModel.selectedImageUri,
                                 contentDescription = "Preview",
                                 contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                 modifier = Modifier.fillMaxSize()
                             )
                         }
                         
                         Icon(
                             imageVector = Lucide.X,
                             contentDescription = "Remove",
                             tint = Color.White,
                             modifier = Modifier
                                 .align(Alignment.TopEnd)
                                 .padding(6.dp)
                                 .size(20.dp)
                                 .clip(androidx.compose.foundation.shape.CircleShape)
                                 .background(Color.Black.copy(0.5f))
                                 .clickable { viewModel.selectedImageUri = null }
                                 .padding(3.dp)
                         )
                     }
                 }

                // Editing Indicator
                AnimatedVisibility(
                    visible = viewModel.isEditing(),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(12.dp) },
                                effects = { vibrancy(); blur(8.dp.toPx()) },
                                onDrawSurface = { drawRect(Color(0xFFFF9500).copy(alpha = 0.3f)) }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.PenLine,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                BasicText(
                                    text = "Editing message",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 2f)
                                    )
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.cancelEditing() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                BasicText(
                                    text = "Cancel",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                ChatInputBar(
                    text = inputText,
                    onTextChange = onInputTextChange,
                    onSend = {
                        val isSendable = inputText.isNotBlank() || viewModel.selectedImageUri != null
                        if (isSendable && viewModel.hapticFeedbackEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onSend()
                    },
                    onStop = { 
                        if (viewModel.hapticFeedbackEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.stopStreaming() 
                    },
                    isLoading = viewModel.isLoading,
                    isStreaming = viewModel.messages.any { it.isStreaming },
                    backdrop = backdrop,
                    onInteractionChanged = onInteractionChanged,
                    onPickImage = {
                        showUploadOptions = true
                    },
                    onOpenToolbox = {
                        showToolbox = true
                    }
                )
            }
        }
        
        // Upload options dialog
        if (showUploadOptions) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showUploadOptions = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(24.dp) },
                            effects = { vibrancy(); blur(16.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.Black.copy(0.5f)) }
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Choose Upload Option",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Select image
                        LiquidButton(
                            onClick = {
                                showUploadOptions = false
                                photoPicker.launch("image/*")
                            },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            isInteractive = true,
                            tint = Color(0xFF007AFF).copy(alpha = 0.4f)
                        ) {
                            Icon(Lucide.Image, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Choose Image from Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        
                        // Select file
                        LiquidButton(
                            onClick = {
                                showUploadOptions = false
                                filePicker.launch("*/*")
                            },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            isInteractive = true,
                            tint = Color(0xFF34C759).copy(alpha = 0.4f)
                        ) {
                            Icon(Lucide.File, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Choose File", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        
                        // Take photo
                        LiquidButton(
                            onClick = {
                                showUploadOptions = false
                                val permission = android.Manifest.permission.CAMERA
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        cameraOutputFile = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                                        cameraOutputUri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            cameraOutputFile!!
                                        )
                                        cameraLauncher.launch(cameraOutputUri!!)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(permission)
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            isInteractive = true,
                            tint = Color(0xFFFF9500).copy(alpha = 0.4f)
                        ) {
                            Icon(Lucide.Camera, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Take Photo", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        LiquidButton(
                            onClick = { showUploadOptions = false },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            isInteractive = true,
                            tint = Color(0xFF8E8E93).copy(alpha = 0.5f)
                        ) {
                            Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        // Toolbox Dialog
        if (showToolbox) {
            ToolboxDialog(
                backdrop = backdrop,
                viewModel = viewModel,
                onDismiss = { showToolbox = false }
            )
        }
    }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LiquidGlassChatBubble(
    message: UiMessage,
    backdrop: Backdrop,
    defaultModel: String,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onSaveImage: (String) -> Unit,
    hapticFeedbackEnabled: Boolean
) {
    val isUser = message.role == "user"
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var lastHapticTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(message.content) {
        if (!isUser && message.isStreaming && hapticFeedbackEnabled) {
            val now = System.currentTimeMillis()
            if (now - lastHapticTime > 50) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                lastHapticTime = now
            }
        }
    }

    val bubbleShape = ContinuousRoundedRectangle(20.dp)
    val tintColor = if (isUser) Color(0xFF007AFF) else Color(0xFF1E222D)
    val surfaceColor = if (isUser) Color(0xFF007AFF).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.16f)
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Author label
        Row(
            modifier = Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isUser) "You" else (message.model ?: defaultModel),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        blurRadius = 4f
                    )
                )
            )
        }
        
        // Message bubble
        Box(
            modifier = Modifier
                .then(
                    if (isUser) {
                        Modifier.widthIn(max = 300.dp)
                    } else {
                        Modifier.fillMaxWidth(0.96f)
                    }
                )
                .clip(bubbleShape)
                .border(
                    width = 1.dp,
                    color = if (isUser) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f),
                    shape = bubbleShape
                )
                .background(
                    if (isUser) Color(0xFF007AFF).copy(alpha = 0.88f)
                    else Color(0xFF1E222D).copy(alpha = 0.82f)
                )
                .clickable { keyboardController?.hide() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides Color.White,
                    LocalTextStyle provides TextStyle(
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color.White,
                        fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            blurRadius = 3f,
                            offset = androidx.compose.ui.geometry.Offset(0f, 1f)
                        )
                    )
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Column {
                    // Thinking process
                    message.thinkingContent?.takeIf { it.isNotBlank() }?.let { thinkingContent ->
                        ThinkingComponent(
                            content = thinkingContent,
                            isThinking = message.isStreaming && message.content.isEmpty(),
                            backdrop = backdrop,
                            shouldCollapse = message.content.isNotEmpty()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    val textColor = Color.White
                    val textShadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        blurRadius = 3f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 1f)
                    )

                    val parsedContent = remember(message.content) {
                        ChatMessageContentParser.parse(message.content)
                    }
                    val imageUrl = parsedContent.imageUrl
                    val textContent = parsedContent.text

                    var showImagePreview by remember { mutableStateOf(false) }

                    if (imageUrl != null) {
                         AsyncImage(
                             model = imageUrl,
                             contentDescription = "Image",
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .heightIn(max = 240.dp)
                                 .clip(RoundedCornerShape(12.dp))
                                 .clickable { showImagePreview = true },
                             contentScale = androidx.compose.ui.layout.ContentScale.Crop
                         )
                         Spacer(Modifier.height(8.dp))
                         
                         if (showImagePreview) {
                             var scale by remember { mutableFloatStateOf(1f) }
                             var offsetX by remember { mutableFloatStateOf(0f) }
                             var offsetY by remember { mutableFloatStateOf(0f) }
                             
                             androidx.compose.ui.window.Dialog(
                                 onDismissRequest = { showImagePreview = false },
                                 properties = androidx.compose.ui.window.DialogProperties(
                                     usePlatformDefaultWidth = false,
                                     decorFitsSystemWindows = false
                                 )
                             ) {
                                 Box(
                                     modifier = Modifier
                                         .fillMaxSize()
                                         .background(Color.Black),
                                     contentAlignment = Alignment.Center
                                 ) {
                                     AsyncImage(
                                         model = imageUrl,
                                         contentDescription = "Full Image",
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .graphicsLayer(
                                                 scaleX = scale,
                                                 scaleY = scale,
                                                 translationX = offsetX,
                                                 translationY = offsetY
                                             )
                                             .pointerInput(Unit) {
                                                 detectTransformGestures { _, pan, zoom, _ ->
                                                     scale = (scale * zoom).coerceIn(1f, 5f)
                                                     if (scale > 1f) {
                                                         offsetX += pan.x
                                                         offsetY += pan.y
                                                     } else {
                                                         offsetX = 0f
                                                         offsetY = 0f
                                                     }
                                                 }
                                             },
                                         contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                     )
                                     
                                     // Download button
                                     LiquidButton(
                                         onClick = { onSaveImage(imageUrl) },
                                         backdrop = backdrop,
                                         modifier = Modifier
                                             .align(Alignment.BottomCenter)
                                             .padding(bottom = 64.dp)
                                             .size(56.dp),
                                         isInteractive = true,
                                         tint = Color(0xFF007AFF).copy(alpha = 0.8f)
                                     ) {
                                         Icon(Lucide.Download, null, tint = Color.White)
                                     }
                                     
                                     // Close button
                                     LiquidButton(
                                         onClick = { showImagePreview = false },
                                         backdrop = backdrop,
                                         modifier = Modifier
                                             .align(Alignment.TopEnd)
                                             .padding(top = 48.dp, end = 24.dp)
                                             .size(44.dp),
                                         isInteractive = true,
                                         tint = Color.White.copy(alpha = 0.2f)
                                     ) {
                                         Icon(Lucide.X, null, tint = Color.White)
                                     }
                                 }
                             }
                         }
                    }

                    if (textContent.isNotEmpty()) {
                        MarkdownBlock(
                            content = textContent,
                            style = LocalTextStyle.current.copy(
                                color = textColor,
                                shadow = textShadow
                            )
                        )
                    } else if (message.isStreaming && (message.thinkingContent.isNullOrBlank())) {
                        TypingIndicator(backdrop = backdrop)
                    }
                    
                    if (message.isStreaming && message.content.isNotEmpty()) {
                        BasicText(
                            text = "▌",
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                shadow = textShadow
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                        }
                    }
                }
            }
        
        if (!message.isStreaming && message.content.isNotEmpty()) {
            var showDeleteDialog by remember { mutableStateOf(false) }
            
            if (showDeleteDialog) {
                LiquidConfirmationDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    onConfirm = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    title = "Delete Message",
                    message = "Are you sure you want to delete this message?",
                    confirmText = "Delete",
                    icon = Lucide.Trash2,
                    backdrop = backdrop
                )
            }
            
            MessageActionButtons(
                content = message.content,
                isUser = isUser,
                backdrop = backdrop,
                onRegenerate = if (isUser) null else onRegenerate,
                onEdit = if (isUser) onEdit else null,
                onDelete = { showDeleteDialog = true }
            )
        }
    }
}

@Composable
private fun ConversationDrawerContent(
    conversations: List<com.liquidglass.fluxhub.data.ConversationEntity>,
    currentConversationId: String?,
    backdrop: Backdrop,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onNewConversation: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit = {},
    assistants: List<com.liquidglass.fluxhub.data.AssistantEntity> = emptyList(),
    currentAssistant: com.liquidglass.fluxhub.data.AssistantEntity? = null,
    onSwitchAssistant: (com.liquidglass.fluxhub.data.AssistantEntity) -> Unit = {},
    onNavigateToAssistantSelection: () -> Unit = {}
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .padding(top = 12.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        drawerContainerColor = Color.Transparent,
        drawerShape = ContinuousRoundedRectangle(28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ContinuousRoundedRectangle(28.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = ContinuousRoundedRectangle(28.dp)
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(28.dp) },
                    effects = {
                        vibrancy()
                        blur(20.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.62f))
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 18.dp)
                    .statusBarsPadding()
            ) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Lucide.MessageSquare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Chats",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp,
                                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                            )
                        )
                    }
                    
                    // Compact New Chat Action Button in Header
                    Box(
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(Color(0xFF007AFF))
                            .clickable { onNewConversation() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Lucide.Plus, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Text(
                                text = "New", 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                
                // Search box
                var conversationSearchQuery by remember { mutableStateOf("") }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(16.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = ContinuousRoundedRectangle(16.dp)
                        )
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Lucide.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        BasicTextField(
                            value = conversationSearchQuery,
                            onValueChange = { conversationSearchQuery = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (conversationSearchQuery.isEmpty()) {
                                        BasicText(
                                            text = "Search conversations...",
                                            style = TextStyle(
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (conversationSearchQuery.isNotEmpty()) {
                            Icon(
                                Lucide.X,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { conversationSearchQuery = "" }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                val filteredConversations = remember(conversations, conversationSearchQuery) {
                    if (conversationSearchQuery.isBlank()) {
                        conversations
                    } else {
                        conversations.filter { 
                            it.title.contains(conversationSearchQuery, ignoreCase = true) 
                        }
                    }
                }
                
                if (conversations.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No conversations yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    }
                } else if (filteredConversations.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No matching conversations found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    var conversationToRename by remember { mutableStateOf<ConversationEntity?>(null) }
                    var renameText by remember { mutableStateOf("") }
                    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }

                    conversationToRename?.let { conv ->
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { conversationToRename = null }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(24.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedCornerShape(24.dp) },
                                    effects = { vibrancy(); blur(16.dp.toPx()) },
                                    onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.6f)) }
                                )
                                .padding(24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Rename Conversation",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(Modifier.height(20.dp))
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF007AFF),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFF007AFF)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LiquidButton(
                                        onClick = { conversationToRename = null },
                                        backdrop = backdrop,
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        tint = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text("Cancel", color = Color.White, fontWeight = FontWeight.Medium)
                                    }
                                    LiquidButton(
                                        onClick = {
                                            if (renameText.isNotBlank()) {
                                                onRenameConversation(conv.id, renameText)
                                            }
                                            conversationToRename = null
                                        },
                                        backdrop = backdrop,
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        tint = Color(0xFF007AFF).copy(alpha = 0.8f)
                                    ) {
                                        Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                conversationToDelete?.let { conv ->
                    LiquidConfirmationDialog(
                        onDismissRequest = { conversationToDelete = null },
                        onConfirm = {
                            onDeleteConversation(conv.id)
                            conversationToDelete = null
                        },
                        title = "Delete Conversation",
                        message = "Are you sure you want to delete \"${conv.title}\"? This action cannot be undone.",
                        confirmText = "Delete",
                        icon = Lucide.Trash2,
                        backdrop = backdrop
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        items(
                            count = filteredConversations.size,
                            key = { index -> filteredConversations[index].id }
                        ) { index ->
                            val conversation = filteredConversations[index]
                            val isSelected = conversation.id == currentConversationId
                            
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    when (it) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            conversationToDelete = conversation
                                            false
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            renameText = conversation.title
                                            conversationToRename = conversation
                                            false
                                        }
                                        else -> false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    if (direction != SwipeToDismissBoxValue.Settled) {
                                        val color = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF3B30)
                                            SwipeToDismissBoxValue.StartToEnd -> Color(0xFF007AFF)
                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                        }
                                        val alignment = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            SwipeToDismissBoxValue.Settled -> Alignment.Center
                                        }
                                        val icon = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> Lucide.Trash2
                                            SwipeToDismissBoxValue.StartToEnd -> Lucide.Pencil
                                            SwipeToDismissBoxValue.Settled -> null
                                        }
                                        
                                        if (icon != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 2.dp)
                                                    .clip(ContinuousRoundedRectangle(16.dp))
                                                    .background(color)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = alignment
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                },
                                content = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(ContinuousRoundedRectangle(16.dp))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.22f),
                                                shape = ContinuousRoundedRectangle(16.dp)
                                            )
                                            .background(
                                                if (isSelected) Color(0xFF007AFF).copy(alpha = 0.35f)
                                                else Color.White.copy(alpha = 0.12f)
                                            )
                                            .clickable { onSelectConversation(conversation.id) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(ContinuousRoundedRectangle(10.dp))
                                                    .background(
                                                        if (isSelected) Color(0xFF007AFF)
                                                        else Color.White.copy(alpha = 0.15f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Lucide.MessageCircle else Lucide.MessageSquare,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                BasicText(
                                                    text = conversation.title,
                                                    style = TextStyle(
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        shadow = androidx.compose.ui.graphics.Shadow(
                                                            color = Color.Black.copy(alpha = 0.5f),
                                                            blurRadius = 3f
                                                        )
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                val timeDiff = System.currentTimeMillis() - conversation.updatedAt
                                                val timeText = when {
                                                    timeDiff < 60000 -> "Just now"
                                                    timeDiff < 3600000 -> "${timeDiff / 60000}m ago"
                                                    timeDiff < 86400000 -> "${timeDiff / 3600000}h ago"
                                                    else -> "${timeDiff / 86400000}d ago"
                                                }
                                                BasicText(
                                                    text = timeText,
                                                    style = TextStyle(
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                                            .background(Color(0xFF34C759))
                                                    )
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(ContinuousCapsule)
                                                        .background(Color.White.copy(alpha = 0.12f))
                                                        .clickable { conversationToDelete = conversation },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Lucide.Trash2,
                                                        contentDescription = "Delete",
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========== Toolbox Dialog ==========

private sealed class ToolboxPage {
    object List : ToolboxPage()
    object ThinkingBudget : ToolboxPage()
    object WebSearch : ToolboxPage()
    object StreamOutput : ToolboxPage()
    object ContextSize : ToolboxPage()
}

@Composable
private fun ToolboxDialog(
    backdrop: Backdrop,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf<ToolboxPage>(ToolboxPage.List) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            if (currentPage == ToolboxPage.List) {
                onDismiss()
            } else {
                currentPage = ToolboxPage.List
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(28.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    effects = { vibrancy(); blur(20.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.Black.copy(0.6f)) }
                )
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState == ToolboxPage.List) {
                        slideInHorizontally { -it } + fadeIn() togetherWith 
                            slideOutHorizontally { it } + fadeOut()
                    } else {
                        slideInHorizontally { it } + fadeIn() togetherWith 
                            slideOutHorizontally { -it } + fadeOut()
                    }
                },
                label = "toolbox_page"
            ) { page ->
                when (page) {
                    ToolboxPage.List -> ToolboxListPage(
                        viewModel = viewModel,
                        backdrop = backdrop,
                        onNavigate = { currentPage = it },
                        onDismiss = onDismiss
                    )
                    ToolboxPage.ThinkingBudget -> ToolboxThinkingBudgetPage(
                        viewModel = viewModel,
                        backdrop = backdrop,
                        onBack = { currentPage = ToolboxPage.List }
                    )
                    ToolboxPage.WebSearch -> ToolboxWebSearchPage(
                        viewModel = viewModel,
                        backdrop = backdrop,
                        onBack = { currentPage = ToolboxPage.List }
                    )
                    ToolboxPage.StreamOutput -> ToolboxStreamOutputPage(
                        viewModel = viewModel,
                        backdrop = backdrop,
                        onBack = { currentPage = ToolboxPage.List }
                    )
                    ToolboxPage.ContextSize -> ToolboxContextSizePage(
                        viewModel = viewModel,
                        backdrop = backdrop,
                        onBack = { currentPage = ToolboxPage.List }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxListPage(
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    onNavigate: (ToolboxPage) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat Options",
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                )
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ContinuousCapsule)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Thinking Mode (Reasoning Effort)
            ToolboxListItem(
                icon = Lucide.Brain,
                title = "Thinking Mode",
                value = viewModel.getThinkingLevelName(),
                onClick = { onNavigate(ToolboxPage.ThinkingBudget) }
            )
            
            // 2. Web Search
            ToolboxListItem(
                icon = Lucide.Globe,
                title = "Web Search",
                value = if (viewModel.webSearchEnabled) "Enabled" else "Off",
                onClick = { onNavigate(ToolboxPage.WebSearch) }
            )
            
            // 3. Stream Output
            ToolboxListItem(
                icon = Lucide.Zap,
                title = "Stream Output",
                value = if (viewModel.streamEnabled) "Enabled" else "Off",
                onClick = { onNavigate(ToolboxPage.StreamOutput) }
            )
            
            // 4. Context Window (Tokens)
            ToolboxListItem(
                icon = Lucide.Layers,
                title = "Context Window",
                value = viewModel.getContextSizeDisplayText(),
                onClick = { onNavigate(ToolboxPage.ContextSize) }
            )
        }
    }
}

@Composable
private fun ToolboxListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(16.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = ContinuousRoundedRectangle(16.dp)
            )
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ContinuousRoundedRectangle(10.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    style = TextStyle(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 3f)
                    )
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(ContinuousCapsule)
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolboxDetailHeader(
    title: String,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(ContinuousCapsule)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Lucide.ChevronLeft,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            )
        )
    }
}

@Composable
private fun ToolboxThinkingBudgetPage(
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val currentLevel = when (viewModel.thinkingBudget) {
        0 -> "off"
        -1 -> "auto"
        in 1..2048 -> "low"
        in 2049..10000 -> "medium"
        else -> "high"
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        ToolboxDetailHeader(title = "Thinking Effort", backdrop = backdrop, onBack = onBack)
        
        val statusText = when (currentLevel) {
            "off" -> "Disabled"
            "low" -> "Light Reasoning"
            "medium" -> "Moderate Reasoning"
            "high" -> "Deep Reasoning"
            else -> "Auto / Dynamic"
        }
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Off
            ReasoningLevelCard(
                title = "Off",
                description = "Direct generation without extended reasoning",
                isSelected = currentLevel == "off",
                onClick = { viewModel.updateThinkingBudget(0) }
            )
            
            // Low
            ReasoningLevelCard(
                title = "Low",
                description = "Light reasoning, quick replies for general chat",
                isSelected = currentLevel == "low",
                onClick = { viewModel.updateThinkingBudget(1024) }
            )
            
            // Medium
            ReasoningLevelCard(
                title = "Medium",
                description = "Balanced reasoning for complex multi-step queries",
                isSelected = currentLevel == "medium",
                onClick = { viewModel.updateThinkingBudget(8192) }
            )
            
            // High
            ReasoningLevelCard(
                title = "High",
                description = "Maximum reasoning depth for coding and math",
                isSelected = currentLevel == "high",
                onClick = { viewModel.updateThinkingBudget(32000) }
            )
            
            // Auto
            ReasoningLevelCard(
                title = "Auto",
                description = "Dynamic effort managed automatically by provider",
                isSelected = currentLevel == "auto",
                onClick = { viewModel.updateThinkingBudget(-1) }
            )
        }
        
        Spacer(Modifier.height(14.dp))
        Text(
            text = "⚡ Synchronized with OpenAI, Gemini, and Claude reasoning effort.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ReasoningLevelCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(14.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.2f),
                shape = ContinuousRoundedRectangle(14.dp)
            )
            .background(
                if (isSelected) Color(0xFF007AFF).copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    style = TextStyle(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 3f)
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxWebSearchPage(
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        ToolboxDetailHeader(title = "Web Search", backdrop = backdrop, onBack = onBack)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Web Search",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                )
            )
            com.liquidglass.fluxhub.components.LiquidToggle(
                selected = { viewModel.webSearchEnabled },
                onSelect = { viewModel.updateWebSearchEnabled(it) },
                backdrop = backdrop,
                modifier = Modifier.size(width = 64.dp, height = 36.dp)
            )
        }
        
        Text(
            text = "When enabled, the AI can search the web for up-to-date information. Helpful for real-time news, current data, and events beyond training cutoffs.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            )
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "⚠️ Note: This feature requires provider support. Some standard OpenAI API endpoints may ignore search parameters. Please ensure your provider supports web search.",
            color = Color(0xFFFFCC00).copy(alpha = 0.8f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            )
        )
    }
}

@Composable
private fun ToolboxStreamOutputPage(
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        ToolboxDetailHeader(title = "Stream Output", backdrop = backdrop, onBack = onBack)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Stream Output",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                )
            )
            com.liquidglass.fluxhub.components.LiquidToggle(
                selected = { viewModel.streamEnabled },
                onSelect = { viewModel.updateStreamEnabled(it) },
                backdrop = backdrop,
                modifier = Modifier.size(width = 64.dp, height = 36.dp)
            )
        }
        
        Text(
            text = "Streaming displays the response incrementally as it is generated so you can begin reading immediately. When disabled, the entire response appears once complete.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            )
        )
    }
}

@Composable
private fun ToolboxContextSizePage(
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val initialK = if (viewModel.contextSize <= 0) 200f else (viewModel.contextSize / 1024f).coerceIn(4f, 200f)
    var sliderKValue by remember { mutableFloatStateOf(initialK) }
    val isMax = viewModel.contextSize <= 0
    
    LaunchedEffect(viewModel.contextSize) {
        val targetK = if (viewModel.contextSize <= 0) 200f else (viewModel.contextSize / 1024f).coerceIn(4f, 200f)
        if (kotlin.math.abs(sliderKValue - targetK) >= 1f) {
            sliderKValue = targetK
        }
    }

    val liveDisplayText = remember(sliderKValue, isMax) {
        if (isMax && sliderKValue >= 199f) "Max (Unlimited)"
        else "${sliderKValue.toInt()}K tokens (${(sliderKValue * 1024).toInt()} tokens)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        ToolboxDetailHeader(title = "Context Window", backdrop = backdrop, onBack = onBack)
        
        Text(
            text = liveDisplayText,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(16.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = ContinuousRoundedRectangle(16.dp)
                )
                .background(Color.White.copy(alpha = 0.12f))
                .padding(16.dp)
        ) {
            Column {
                com.liquidglass.fluxhub.components.LiquidSlider(
                    value = { sliderKValue },
                    onValueChange = { 
                        sliderKValue = it
                        val tokens = (it * 1024).toInt()
                        viewModel.updateContextSize(tokens)
                    },
                    valueRange = 4f..200f,
                    visibilityThreshold = 0.5f,
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isMax) "All previous messages are preserved without truncation" 
                           else "Retains approximately ~${(sliderKValue * 1024 / 4).toInt()} characters of past conversation history",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Presets",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                4096 to "4K",
                8192 to "8K",
                16384 to "16K",
                32768 to "32K",
                65536 to "64K",
                131072 to "128K",
                200000 to "200K",
                0 to "Max"
            )
            items(presets) { (value, label) ->
                val isSelected = if (value <= 0) viewModel.contextSize <= 0 else (viewModel.contextSize == value || (viewModel.contextSize / 1024) == (value / 1024))
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(ContinuousCapsule)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.2f),
                            shape = ContinuousCapsule
                        )
                        .background(
                            if (isSelected) Color(0xFF007AFF)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable { 
                            viewModel.updateContextSize(value)
                            sliderKValue = if (value <= 0) 200f else (value / 1024f).coerceIn(4f, 200f)
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Context Window specifies the token budget allocated for past conversation history. When conversations exceed this limit, older messages are trimmed while preserving your system instructions and latest turns.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            )
        )
    }
}
