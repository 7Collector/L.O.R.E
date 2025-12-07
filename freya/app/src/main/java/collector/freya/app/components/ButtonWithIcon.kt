package collector.freya.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import collector.freya.app.helpers.conditional

@Composable
fun ButtonWithIcon(
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor: Color = Color.Transparent,
    shape: Shape = CircleShape,
    showBorder: Boolean = false,
    buttonSize: Dp = 42.dp,
    iconSize: Dp = 24.dp,
    rotation: Float = 0f,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(shape)
            .background(backgroundColor)
            .conditional(showBorder,
                { Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) })
            .clickable(
                onClick = onClick,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = tint)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier
                .size(iconSize)
                .rotate(rotation),
            imageVector = imageVector,
            tint = tint,
            contentDescription = contentDescription
        )
    }
}