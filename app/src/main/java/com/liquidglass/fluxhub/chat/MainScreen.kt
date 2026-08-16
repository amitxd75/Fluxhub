package com.liquidglass.fluxhub.chat

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.liquidglass.fluxhub.R
import com.liquidglass.fluxhub.components.LiquidBottomTab
import com.liquidglass.fluxhub.components.LiquidBottomTabs
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import com.liquidglass.fluxhub.ui.theme.GlassTypography
import com.liquidglass.fluxhub.ui.theme.GlassTextStyles

@Composable
fun MainScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // Wait for core settings to initialize before rendering
    if (!viewModel.isSettingsInitialized) {
        return
    }

    // Default to Home (Tab 0)
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val backgroundBitmap = remember(viewModel.wallpaperUri) {
        val uri = viewModel.wallpaperUri
        when {
            uri == null -> {
                // Default wallpaper
                BitmapFactory.decodeResource(context.resources, R.drawable.wallpaper_liquid)
            }
            uri.startsWith("preset:") -> {
                // Preset wallpaper
                val presetName = uri.removePrefix("preset:")
                val resourceId = when (presetName) {
                    "wallpaper_liquid" -> R.drawable.wallpaper_liquid
                    "wallpaper_light" -> R.drawable.wallpaper_light
                    else -> R.drawable.wallpaper_liquid
                }
                BitmapFactory.decodeResource(context.resources, resourceId)
            }
            else -> {
                // Custom wallpaper
                try {
                    val parsedUri = android.net.Uri.parse(uri)
                    
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    
                    val targetSize = 2048
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
                        sampleSize *= 2
                    }
                    
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    } ?: BitmapFactory.decodeResource(context.resources, R.drawable.wallpaper_liquid)
                    
                } catch (e: Exception) {
                    android.util.Log.e("MainScreen", "Failed to load custom wallpaper: ${e.message}", e)
                    BitmapFactory.decodeResource(context.resources, R.drawable.wallpaper_liquid)
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("MainScreen", "OutOfMemory loading wallpaper", e)
                    BitmapFactory.decodeResource(context.resources, R.drawable.wallpaper_liquid)
                }
            }
        }
    }
    
    val backdrop = rememberLayerBackdrop()
    
    // Keyboard visibility observer
    val isKeyboardVisible = rememberIsKeyboardVisible()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val chatListState = rememberLazyListState()
    
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    
    var chatSubPage by remember { mutableStateOf<String?>(null) }
    var settingsSubPage by remember { mutableStateOf<String?>(null) }
    
    var visitedTabs by rememberSaveable { mutableStateOf(setOf(selectedTab)) }
    LaunchedEffect(selectedTab) {
        if (!visitedTabs.contains(selectedTab)) {
            visitedTabs = visitedTabs + selectedTab
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Shared background wallpaper
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
            )
        }
        
        // Dynamic typography styles
        val textStyles = GlassTextStyles.create(
            colorMode = viewModel.textColorMode,
            shadowEnabled = viewModel.textShadowEnabled
        )
        
        // Content Area with Warm Tab Cache
        Box(modifier = Modifier.fillMaxSize()) {
            val bottomPadding = 90.dp
            
            BackHandler(enabled = chatSubPage != null && selectedTab == 1) {
                chatSubPage = null
            }
            BackHandler(enabled = settingsSubPage != null && selectedTab == 2) {
                settingsSubPage = null
            }
            
            // Tab 0: Home Screen (Cached)
            if (visitedTabs.contains(0)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val isCurrent = selectedTab == 0
                            alpha = if (isCurrent) 1f else 0f
                            translationX = if (isCurrent) 0f else (if (selectedTab > 0) -2000f else 2000f)
                        }
                ) {
                    HomeScreen(
                        backdrop = backdrop,
                        bottomPadding = PaddingValues(bottom = bottomPadding),
                        onNavigateToChat = { selectedTab = 1 },
                        onNavigateToAssistantSelection = { 
                            selectedTab = 1
                            chatSubPage = "assistant_selection" 
                        },
                        onQuickPrompt = { prompt ->
                            pendingPrompt = prompt
                            selectedTab = 1
                        },
                        viewModel = viewModel
                    )
                }
            }
            
            // Tab 1: Chat Screen (Cached & Warm)
            if (visitedTabs.contains(1)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val isCurrent = selectedTab == 1
                            alpha = if (isCurrent) 1f else 0f
                            translationX = if (isCurrent) 0f else (if (selectedTab > 1) -2000f else 2000f)
                        }
                ) {
                    if (chatSubPage == "assistant_selection") {
                        AssistantListScreen(
                            onBack = { chatSubPage = null },
                            viewModel = viewModel,
                            backdrop = backdrop,
                            bottomPadding = PaddingValues(bottom = bottomPadding),
                            isSelectionMode = true
                        )
                    } else {
                        ChatScreen(
                            backdrop = backdrop,
                            bottomPadding = PaddingValues(bottom = bottomPadding), 
                            onNavigateToSettings = { selectedTab = 2 },
                            onNavigateToAssistantSelection = { chatSubPage = "assistant_selection" },
                            viewModel = viewModel,
                            listState = chatListState,
                            drawerState = drawerState,
                            initialPrompt = pendingPrompt,
                            onPromptConsumed = { pendingPrompt = null }
                        )
                    }
                }
            }
            
            // Tab 2: Settings Screen (Cached)
            if (visitedTabs.contains(2)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val isCurrent = selectedTab == 2
                            alpha = if (isCurrent) 1f else 0f
                            translationX = if (isCurrent) 0f else 2000f
                        }
                ) {
                    when (settingsSubPage) {
                        "assistants" -> AssistantListScreen(
                            onBack = { settingsSubPage = null },
                            viewModel = viewModel,
                            backdrop = backdrop,
                            bottomPadding = PaddingValues(bottom = bottomPadding)
                        )
                        "providers" -> ProviderListScreen(
                            onBack = { settingsSubPage = null },
                            viewModel = viewModel,
                            backdrop = backdrop,
                            bottomPadding = PaddingValues(bottom = bottomPadding)
                        )
                        "display_settings" -> DisplaySettingsScreen(
                            onBack = { settingsSubPage = null },
                            viewModel = viewModel,
                            backdrop = backdrop,
                            bottomPadding = PaddingValues(bottom = bottomPadding)
                        )
                        "dynamic_island_settings" -> DynamicIslandSettingsScreen(
                            onBack = { settingsSubPage = null },
                            viewModel = viewModel,
                            backdrop = backdrop,
                            bottomPadding = PaddingValues(bottom = bottomPadding)
                        )
                        else -> SettingsScreen(
                            onBack = { selectedTab = 1 },
                            viewModel = viewModel,
                            backdrop = backdrop,
                            isTab = true,
                            bottomPadding = PaddingValues(bottom = bottomPadding),
                            onNavigateToAssistants = { settingsSubPage = "assistants" },
                            onNavigateToProviders = { settingsSubPage = "providers" },
                            onNavigateToDisplay = { settingsSubPage = "display_settings" },
                            onNavigateToDynamicIsland = { settingsSubPage = "dynamic_island_settings" }
                        )
                    }
                }
            }
        }
        
        // Bottom Navigation Bar
        AnimatedVisibility(
            visible = drawerState.isClosed && !isKeyboardVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp, start = 72.dp, end = 72.dp)
                    .widthIn(max = 220.dp)
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { selectedTab },
                    onTabSelected = { 
                        selectedTab = it
                        if (viewModel.hapticFeedbackEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    backdrop = backdrop,
                    tabsCount = 3,
                    glassColor = viewModel.glassColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Tab 0: Home
                    LiquidBottomTab(
                        onClick = { 
                            if (viewModel.hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            selectedTab = 0 
                            settingsSubPage = null
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = "Home",
                                tint = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText(
                                text = "Home",
                                style = textStyles.navLabelStyle(selectedTab == 0)
                            )
                        }
                    }
                    
                    // Tab 1: Chat
                    LiquidBottomTab(
                        onClick = { 
                            if (viewModel.hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            selectedTab = 1 
                            settingsSubPage = null
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Chat",
                                tint = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText(
                                text = "Chat",
                                style = textStyles.navLabelStyle(selectedTab == 1)
                            )
                        }
                    }
                    
                    // Tab 2: Settings
                    LiquidBottomTab(
                        onClick = { 
                            if (viewModel.hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            selectedTab = 2 
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = if (selectedTab == 2) Color.White else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText(
                                text = "Settings",
                                style = textStyles.navLabelStyle(selectedTab == 2)
                            )
                        }
                    }
                }
            }
        }
        
        // ========== Global Dynamic Island ==========
        LaunchedEffect(
            viewModel.dynamicIslandEnabled,
            viewModel.loginNotificationMode,
            viewModel.showTokenCount,
            viewModel.showElapsedTime
        ) {
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.apply {
                isEnabled = viewModel.dynamicIslandEnabled
                loginNotificationMode = viewModel.loginNotificationMode
                showTokenCountEnabled = viewModel.showTokenCount
                showElapsedTimeEnabled = viewModel.showElapsedTime
            }
        }
        
        com.liquidglass.fluxhub.chat.ui.components.DynamicIsland(
            data = com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.toData(),
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            onExpand = { com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.expand() },
            onCollapse = { com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.collapse() },
            onLongPress = { com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showLongPressMenu() },
            onStopGeneration = { viewModel.stopGeneration() },
            onDismiss = { com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.hide() }
        )
        
        // Welcome notification on Home tab after terms acceptance
        var hasShownLoginSuccess by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        var hasShownThisSession by remember { mutableStateOf(false) }
        
        LaunchedEffect(selectedTab, viewModel.agreementAccepted) {
            if (!viewModel.agreementAccepted) return@LaunchedEffect
            if (selectedTab != 0) return@LaunchedEffect
            
            kotlinx.coroutines.delay(100)
            
            if (!viewModel.dynamicIslandEnabled) return@LaunchedEffect
            
            val shouldShow = when (viewModel.loginNotificationMode) {
                "every" -> !hasShownThisSession
                "first" -> !hasShownLoginSuccess
                else -> !hasShownLoginSuccess
            }
            
            if (shouldShow) {
                hasShownLoginSuccess = true
                hasShownThisSession = true
                com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                    message = "Welcome to FluxHub",
                    avatar = "👋",
                    customTitle = "Welcome Back"
                )
            }
        }
        
        // User Agreement Dialog
        if (!viewModel.agreementAccepted) {
            AgreementDialog(
                backdrop = backdrop,
                onAccept = { viewModel.acceptAgreement() },
                onDecline = {
                    (context as? android.app.Activity)?.finishAffinity()
                }
            )
        }
    }
}

@Composable
fun rememberIsKeyboardVisible(): Boolean {
    val view = LocalView.current
    var isKeyboardVisible by remember { mutableStateOf(false) }
    
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val insets = ViewCompat.getRootWindowInsets(view)
            val isVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
            isKeyboardVisible = isVisible
        }
        
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    
    return isKeyboardVisible
}

@Composable
private fun AgreementDialog(
    backdrop: com.kyant.backdrop.Backdrop,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }
    val canAccept = countdown <= 0
    
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(28.dp) },
                    effects = { 
                        vibrancy()
                        blur(20.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.7f)) }
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                BasicText(
                    text = "Terms of Service & Privacy Policy",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(16.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        item {
                            BasicText(
                                text = """
Welcome to FluxHub!

Before using this application, please read the following terms and conditions carefully:

[Terms of Service]

1. Service Description
FluxHub is an AI conversational client application that connects to third-party AI service providers (such as OpenAI, Anthropic, DeepSeek, etc.) to deliver intelligent chat capabilities. This application operates solely as a client interface and does not directly host or operate AI models.

2. User Responsibilities
• You are responsible for providing valid API credentials.
• You are solely responsible for all conversation content and prompts generated using this application.
• You agree not to use this application for any unlawful or abusive activities.
• You agree not to generate or distribute illegal, infringing, or harmful content.

3. Disclaimers
• This application does not warrant the accuracy, completeness, or reliability of AI-generated responses.
• Any costs or token usage incurred with third-party API providers are the sole responsibility of the user.
• This application is not liable for service interruptions, network errors, or third-party outages.

[Privacy Policy]

1. Data Collection
• This application does not collect, track, or store personal identity information.
• Your API keys are stored locally on your device only and are never uploaded to any intermediary servers.
• Conversation records and chat histories are saved locally on your device.

2. Data Usage
• All conversation requests are transmitted directly from your device to the configured AI API endpoints.
• This application does not analyze, monetize, or share your messages with third parties.

3. Security Recommendations
• We recommend rotating your API credentials periodically.
• Safeguard your device to prevent unauthorized access to local records.

4. Third-Party Services
Third-party AI providers are governed by their respective terms of service and privacy policies. Please review their policies before use.

By continuing to use FluxHub, you confirm that you have read, understood, and agreed to these terms.
                                """.trimIndent(),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                if (!canAccept) {
                    BasicText(
                        text = "Please review the agreement (${countdown}s remaining)",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Decline button
                    com.liquidglass.fluxhub.components.LiquidButton(
                        onClick = onDecline,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f).height(50.dp),
                        isInteractive = true,
                        tint = Color.Red.copy(alpha = 0.5f)
                    ) {
                        BasicText(
                            text = "Decline",
                            style = TextStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                    
                    // Accept button
                    com.liquidglass.fluxhub.components.LiquidButton(
                        onClick = { if (canAccept) onAccept() },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1.5f).height(50.dp),
                        isInteractive = canAccept,
                        tint = if (canAccept) Color(0xFF34C759) else Color.Gray.copy(alpha = 0.3f)
                    ) {
                        BasicText(
                            text = if (canAccept) "I Agree & Accept" else "Please Review Agreement...",
                            style = TextStyle(
                                color = if (canAccept) Color.White else Color.White.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
