package com.example.jianji.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JianjiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF9AD9BD), onPrimary = Color(0xFF123A2D), primaryContainer = Color(0xFF264E3E), onPrimaryContainer = Color(0xFFBCF1D5),
        secondary = Color(0xFFE7C79A), background = Color(0xFF111B18), surface = Color(0xFF111B18), surfaceVariant = Color(0xFF293C34),
        secondaryContainer = Color(0xFF354E40), onSecondaryContainer = Color(0xFFD7EDDC), surfaceContainerHighest = Color(0xFF25372D),
        surfaceContainer = Color(0xFF1B2B24), surfaceContainerLow = Color(0xFF192820), surfaceContainerHigh = Color(0xFF25372D),
        onSurface = Color(0xFFE2ECE5), onSurfaceVariant = Color(0xFFB7C9BE), outlineVariant = Color(0xFF3D5246),
    ) else lightColorScheme(
        primary = Color(0xFF21694F), onPrimary = Color.White, primaryContainer = Color(0xFFD8EDE0), onPrimaryContainer = Color(0xFF164E36),
        secondary = Color(0xFF8F6540), background = Color(0xFFF4F6F1), surface = Color(0xFFF4F6F1), surfaceVariant = Color(0xFFE5EDE3),
        secondaryContainer = Color(0xFFDCEBDD), onSecondaryContainer = Color(0xFF254C35), surfaceContainerHighest = Color.White,
        surfaceContainer = Color(0xFFEDF2E9), surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFE5EDE3),
        onSurface = Color(0xFF1C3227), onSurfaceVariant = Color(0xFF52685B), outlineVariant = Color(0xFFD6E1D6),
    )
    val base = Typography()
    MaterialTheme(colorScheme = colors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        typography = base.copy(titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold), headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold)), content = content)
}
