package com.liquidglass.fluxhub.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.capsule.ContinuousRoundedRectangle
import com.liquidglass.fluxhub.components.LiquidButton
import com.liquidglass.fluxhub.components.LiquidToggle

@Composable
fun DynamicIslandSettingsScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel,
    backdrop: Backdrop,
    bottomPadding: PaddingValues = PaddingValues(0.dp)
) {
    val glassOpacity = viewModel.glassOpacity
    val glassBlur = viewModel.glassBlur

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottomPadding)
            .padding(16.dp)
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
                "Dynamic Island Settings",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                )
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Enable Dynamic Island
        SettingsCardSimple(backdrop, glassOpacity, glassBlur) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Dynamic Island", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Show floating status indicator during chat generation",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                LiquidToggle(
                    selected = { viewModel.dynamicIslandEnabled },
                    onSelect = { viewModel.updateDynamicIslandEnabled(it) },
                    backdrop = backdrop
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Welcome Notification Mode
        SettingsCardSimple(backdrop, glassOpacity, glassBlur) {
            Column {
                Text(
                    "Welcome Notification",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Control when the welcome banner appears on the home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeOption(
                        selected = viewModel.loginNotificationMode == "first",
                        onClick = { viewModel.updateLoginNotificationMode("first") },
                        label = "First Launch Only",
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ModeOption(
                        selected = viewModel.loginNotificationMode == "every",
                        onClick = { viewModel.updateLoginNotificationMode("every") },
                        label = "Every Launch",
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Display Options
        SettingsCardSimple(backdrop, glassOpacity, glassBlur) {
            Column {
                Text(
                    "Display Information",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                
                // Show Token Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Token Count", color = Color.White)
                    LiquidToggle(
                        selected = { viewModel.showTokenCount },
                        onSelect = { viewModel.updateShowTokenCount(it) },
                        backdrop = backdrop
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Show Elapsed Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Elapsed Time", color = Color.White)
                    LiquidToggle(
                        selected = { viewModel.showElapsedTime },
                        onSelect = { viewModel.updateShowElapsedTime(it) },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCardSimple(
    backdrop: Backdrop,
    glassOpacity: Float,
    glassBlur: Float,
    content: @Composable ColumnScope.() -> Unit
) {
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
        Column(content = content)
    }
}

@Composable
private fun ModeOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.height(48.dp),
        isInteractive = true,
        tint = if (selected) Color(0xFF34C759) else Color.White,
        surfaceColor = if (selected) Color(0xFF34C759).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
