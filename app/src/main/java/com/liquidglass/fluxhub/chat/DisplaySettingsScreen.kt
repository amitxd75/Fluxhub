package com.liquidglass.fluxhub.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.liquidglass.fluxhub.components.LiquidButton
import com.liquidglass.fluxhub.components.LiquidSlider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.liquidglass.fluxhub.ui.theme.GlassTextStyles

@Composable
fun DisplaySettingsScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    bottomPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val wallpaperUri = viewModel.wallpaperUri
    val glassOpacity = viewModel.glassOpacity
    val glassBlur = viewModel.glassBlur
    
    val textStyles = GlassTextStyles.create(
        colorMode = viewModel.textColorMode,
        shadowEnabled = viewModel.textShadowEnabled
    )
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flag)
                viewModel.updateWallpaperUri(it.toString())
            } catch (e: Exception) {
                viewModel.updateWallpaperUri(it.toString())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottomPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier.size(44.dp),
                isInteractive = true,
                padding = PaddingValues(0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Text(
                "Display Settings",
                style = textStyles.title.copy(fontSize = 24.sp)
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Wallpaper Config
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(16.dp) },
                    effects = { vibrancy(); blur(glassBlur.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = glassOpacity)) }
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    "Background Wallpaper",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                
                // Preset Wallpapers
                Text(
                    "Preset Wallpapers",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PresetWallpaperItem(
                        resourceId = com.liquidglass.fluxhub.R.drawable.wallpaper_liquid,
                        isSelected = wallpaperUri == null || wallpaperUri == "preset:wallpaper_liquid",
                        onClick = {
                            viewModel.updateWallpaperUri(null)
                        }
                    )
                    
                    PresetWallpaperItem(
                        resourceId = com.liquidglass.fluxhub.R.drawable.wallpaper_light,
                        isSelected = wallpaperUri == "preset:wallpaper_light",
                        onClick = {
                            viewModel.updateWallpaperUri("preset:wallpaper_light")
                        }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Custom Wallpaper
                Text(
                    "Custom Wallpaper",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiquidButton(
                        onClick = { launcher.launch("image/*") },
                        backdrop = backdrop,
                        modifier = Modifier.height(44.dp).weight(1f),
                        tint = Color(0xFF007AFF)
                    ) {
                        Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose Image", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    if (wallpaperUri != null) {
                        LiquidButton(
                            onClick = { viewModel.updateWallpaperUri(null) },
                            backdrop = backdrop,
                            modifier = Modifier.height(44.dp),
                            tint = Color.Red.copy(alpha = 0.6f)
                        ) {
                            Text("Reset Default", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (wallpaperUri != null && !wallpaperUri.startsWith("preset:")) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Custom wallpaper in use",
                        style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        // Glass Color Config
        Text(
            "Liquid Glass Color",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(16.dp) },
                    effects = { vibrancy(); blur(glassBlur.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = glassOpacity)) }
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    "Select frosted glass tint for the bottom bar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorOption(
                        color = Color.White,
                        label = "Default",
                        isSelected = viewModel.glassColor == "default",
                        onClick = { viewModel.updateGlassColor("default") }
                    )
                    ColorOption(
                        color = Color(0xFF007AFF),
                        label = "Blue",
                        isSelected = viewModel.glassColor == "007AFF",
                        onClick = { viewModel.updateGlassColor("007AFF") }
                    )
                    ColorOption(
                        color = Color(0xFFAF52DE),
                        label = "Purple",
                        isSelected = viewModel.glassColor == "AF52DE",
                        onClick = { viewModel.updateGlassColor("AF52DE") }
                    )
                    ColorOption(
                        color = Color(0xFF34C759),
                        label = "Green",
                        isSelected = viewModel.glassColor == "34C759",
                        onClick = { viewModel.updateGlassColor("34C759") }
                    )
                    ColorOption(
                        color = Color(0xFFFF9500),
                        label = "Orange",
                        isSelected = viewModel.glassColor == "FF9500",
                        onClick = { viewModel.updateGlassColor("FF9500") }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        // Glass Blur Strength & Hardness Config
        Text(
            "Glass Blur & Hardness",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(16.dp) },
                    effects = { vibrancy(); blur(glassBlur.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = glassOpacity)) }
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Blur Strength",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${glassBlur.toInt()} dp",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                var blurSliderValue by remember(glassBlur) { mutableFloatStateOf(glassBlur) }
                LiquidSlider(
                    value = { blurSliderValue },
                    onValueChange = { 
                        blurSliderValue = it
                        viewModel.updateGlassBlur(it) 
                    },
                    valueRange = 4f..40f,
                    visibilityThreshold = 1f,
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val blurPresets = listOf("Soft" to 8f, "Balanced" to 16f, "Frosted" to 24f, "Deep" to 36f)
                    blurPresets.forEach { (label, value) ->
                        val isSelected = (glassBlur - value).let { kotlin.math.abs(it) < 2f }
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                                    blurSliderValue = value
                                    viewModel.updateGlassBlur(value) 
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Interaction Config
        Text(
            "Interaction & Haptics",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(16.dp) },
                    effects = { vibrancy(); blur(glassBlur.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = glassOpacity)) }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Haptic Feedback",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Provide tactile response on interactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                com.liquidglass.fluxhub.components.LiquidToggle(
                    selected = { viewModel.hapticFeedbackEnabled },
                    onSelect = { 
                        viewModel.updateHapticFeedbackEnabled(it)
                        if (it) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    backdrop = backdrop
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Text Style Config
        Text(
            "Typography Style",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(16.dp) },
                    effects = { vibrancy(); blur(glassBlur.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = glassOpacity)) }
                )
                .padding(16.dp)
        ) {
            Column {
                // Text shadow toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Text Shadow",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Enhance legibility against dynamic backgrounds",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    com.liquidglass.fluxhub.components.LiquidToggle(
                        selected = { viewModel.textShadowEnabled },
                        onSelect = { viewModel.updateTextShadowEnabled(it) },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetWallpaperItem(
    resourceId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.3f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(resourceId),
            contentDescription = "Wallpaper",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorOption(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = if (color == Color.White) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        )
    }
}
