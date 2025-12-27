package collector.freya.app.mimir.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import collector.freya.app.mimir.DriveViewModel

@Composable
fun MoreOptionsMenu(
    modifier: Modifier = Modifier,
    id: String,
    name: String,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: DriveViewModel,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        MenuItem(
            text = "Share", icon = Icons.Outlined.PersonAddAlt, onClick = {  })
        MenuItem(
            text = "Manage Access", icon = Icons.Outlined.Groups, onClick = {  })

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        MenuItem(
            text = "Copy Link", icon = Icons.Outlined.Link, onClick = {  })

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        MenuItem(
            text = "Add to Favourites", icon = Icons.Outlined.Star, onClick = {  })
        MenuItem(
            text = "Rename",
            icon = Icons.Outlined.Edit,
            onClick = {
                onDismissRequest()
                viewModel.toggleShowRenameDialog(id, name)
            })
        MenuItem(
            text = "Delete", icon = Icons.Outlined.Delete, onClick = {  })
        MenuItem(
            text = "Properties", icon = Icons.Outlined.Info, onClick = {  })
    }
}

@Composable
fun MenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    )
}