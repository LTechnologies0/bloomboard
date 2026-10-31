package dev.ltechnologies.bloomboard.ui.keyboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun KeyCap(
    label: String,
    active: Boolean,
    popup: String?,
    modifier: Modifier = Modifier,
    onDown: () -> Unit,
    onUp: (longPress: Boolean) -> Unit,
    onLong: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "key-scale",
    )
    val shape = if (label.length > 8) RoundedCornerShape(28.dp) else RoundedCornerShape(16.dp)
    val container = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        pressed -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .zIndex(if (pressed) 2f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                var longPressed = false
                detectTapGestures(
                    onLongPress = {
                        longPressed = true
                        onLong()
                    },
                    onPress = {
                        longPressed = false
                        pressed = true
                        onDown()
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            onUp(longPressed)
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (pressed) 0.dp else 1.dp, shape)
                .clip(shape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = content,
                fontSize = if (label.length > 2) 12.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (pressed && !popup.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-46).dp)
                    .size(width = 48.dp, height = 52.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = popup,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
