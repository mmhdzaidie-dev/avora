package com.zai.avora.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zai.avora.ui.theme.AvoraAppTheme

val canBlurBackdrop: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    artworkUrl: String? = null,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = AvoraAppTheme.colors.isDark

    val bodyFill = if (dark) {
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))
    } else {
        listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.36f))
    }
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.14f else 0.45f),
        0.45f to Color.Transparent
    )
    val rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = if (dark) 0.25f else 0.70f)
        )
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(if (dark) Color(0xFF141414) else Color(0xFFECECF0))
    ) {
        if (artworkUrl != null && canBlurBackdrop) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(40.dp)
                    .alpha(if (dark) 0.55f else 0.60f)
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(bodyFill)))
        Box(Modifier.matchParentSize().background(sheen))
        Box(Modifier.matchParentSize().border(1.dp, rim, shape))
        content()
    }
}
