package collector.freya.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import collector.freya.app.components.ButtonWithIcon
import collector.freya.app.helpers.getTitleByScreen

@Composable
fun AppTopBar(modifier: Modifier = Modifier, viewModel: MainViewModel, screenState: MainScreenState, openDrawer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ButtonWithIcon(
            modifier = Modifier,
            imageVector = Icons.Default.Menu,
            contentDescription = "Menu",
            buttonSize = 48.dp,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            showBorder = true,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = openDrawer
        )

        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { openDrawer() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = getTitleByScreen(screenState),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.weight(1.0f))

        // No such need right now
        /*ButtonWithIcon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Options",
            buttonSize = 48.dp,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            showBorder = true,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { }
        )*/
    }
}