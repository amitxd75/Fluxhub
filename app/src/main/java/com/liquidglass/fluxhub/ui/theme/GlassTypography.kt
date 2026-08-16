package com.liquidglass.fluxhub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Currently active typography styles passed down via CompositionLocal
 */
val LocalGlassTextStyles = compositionLocalOf { GlassTextStyles.default() }

/**
 * Dynamic glass typography provider wrapper
 */
@Composable
fun ProvideGlassTextStyles(
    colorMode: String,
    shadowEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val styles = GlassTextStyles.create(colorMode, shadowEnabled)
    CompositionLocalProvider(LocalGlassTextStyles provides styles) {
        content()
    }
}

/**
 * Dynamically generated typography style collection
 */
data class GlassTextStyles(
    val baseColor: Color,
    val shadowColor: Color,
    val shadowEnabled: Boolean
) {
    companion object {
        fun default() = create("white", true)
        
        fun create(colorMode: String, shadowEnabled: Boolean): GlassTextStyles {
            val baseColor = if (colorMode == "black") Color.Black else Color.White
            val shadowColor = if (colorMode == "black") Color.White else Color.Black
            return GlassTextStyles(baseColor, shadowColor, shadowEnabled)
        }
    }
    
    private fun shadow(alpha: Float, blurRadius: Float, offset: Offset = Offset.Zero): Shadow? {
        return if (shadowEnabled) {
            Shadow(color = shadowColor.copy(alpha = alpha), blurRadius = blurRadius, offset = offset)
        } else null
    }
    
    // ============== Headline / Title Styles ==============
    
    val displayLarge: TextStyle get() = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = baseColor,
        shadow = shadow(0.6f, 12f, Offset(0f, 2f))
    )
    
    val title: TextStyle get() = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = baseColor,
        shadow = shadow(0.6f, 10f, Offset(0f, 2f))
    )
    
    val titleMedium: TextStyle get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = baseColor,
        shadow = shadow(0.5f, 6f, Offset(0f, 1f))
    )
    
    val titleSmall: TextStyle get() = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = baseColor,
        shadow = shadow(0.5f, 5f, Offset(0f, 1f))
    )
    
    // ============== Body Styles ==============
    
    val body: TextStyle get() = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = baseColor,
        shadow = shadow(0.5f, 4f)
    )
    
    val bodyMedium: TextStyle get() = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = baseColor,
        shadow = shadow(0.5f, 4f)
    )
    
    val bodyLarge: TextStyle get() = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = baseColor,
        shadow = shadow(0.5f, 4f)
    )
    
    // ============== Auxiliary Styles ==============
    
    val label: TextStyle get() = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = baseColor.copy(alpha = 0.9f),
        letterSpacing = 1.sp,
        shadow = shadow(0.5f, 4f)
    )
    
    val caption: TextStyle get() = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = baseColor.copy(alpha = 0.8f),
        shadow = shadow(0.4f, 3f)
    )
    
    val small: TextStyle get() = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = baseColor.copy(alpha = 0.7f),
        shadow = shadow(0.4f, 3f)
    )
    
    val tiny: TextStyle get() = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        color = baseColor.copy(alpha = 0.7f),
        shadow = shadow(0.3f, 2f)
    )
    
    // ============== Button Styles ==============
    
    val button: TextStyle get() = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = baseColor,
        shadow = shadow(0.4f, 3f)
    )
    
    val buttonSmall: TextStyle get() = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = baseColor,
        shadow = shadow(0.4f, 3f)
    )
    
    // ============== Navigation Label Styles ==============
    
    fun navLabelStyle(isSelected: Boolean): TextStyle {
        return if (isSelected) {
            TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                shadow = shadow(0.6f, 4f, Offset(0f, 1f))
            )
        } else {
            TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f),
                shadow = shadow(0.4f, 3f, Offset(0f, 1f))
            )
        }
    }
    
    // ============== Card Styles ==============
    
    val cardTitle: TextStyle get() = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        shadow = shadow(0.5f, 4f)
    )
    
    val cardBody: TextStyle get() = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White.copy(alpha = 0.8f),
        shadow = shadow(0.4f, 3f)
    )
}

// ============== Legacy Compatibility Static Object ==============
object GlassTypography {
    val displayLarge = GlassTextStyles.default().displayLarge
    val title = GlassTextStyles.default().title
    val titleMedium = GlassTextStyles.default().titleMedium
    val titleSmall = GlassTextStyles.default().titleSmall
    val body = GlassTextStyles.default().body
    val bodyMedium = GlassTextStyles.default().bodyMedium
    val bodyLarge = GlassTextStyles.default().bodyLarge
    val label = GlassTextStyles.default().label
    val caption = GlassTextStyles.default().caption
    val small = GlassTextStyles.default().small
    val tiny = GlassTextStyles.default().tiny
    val button = GlassTextStyles.default().button
    val buttonSmall = GlassTextStyles.default().buttonSmall
    val navLabel = GlassTextStyles.default().navLabelStyle(false)
    val navLabelSelected = GlassTextStyles.default().navLabelStyle(true)
    
    fun navLabelStyle(isSelected: Boolean) = GlassTextStyles.default().navLabelStyle(isSelected)
}
