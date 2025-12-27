package collector.freya.app.mimir.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import collector.freya.app.components.ButtonWithIcon

@Composable
fun SelectionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    dismissSelectionMode: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .padding(end = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ButtonWithIcon(
            modifier = Modifier,
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            buttonSize = 48.dp,
            showBorder = false,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = dismissSelectionMode
        )
        Text(
            modifier = Modifier.widthIn(min = 20.dp),
            text = "$selectedCount",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        ButtonWithIcon(
            modifier = Modifier,
            imageVector = Icons.Default.Delete,
            contentDescription = "Close",
            buttonSize = 48.dp,
            showBorder = false,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onDeleteClicked
        )
    }
}