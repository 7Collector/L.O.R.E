package collector.freya.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {

        Spacer(
            Modifier
                .statusBarsPadding()
                .height(80.dp)
        )
        // -------------------------
        // 1. GENERAL
        // -------------------------
        SettingsSectionHeader("General")

        SettingsItem(
            icon = Icons.Outlined.Contrast,
            title = "Theme",
            value = "System",
            onClick = {}
        )
        SettingsItem(
            icon = Icons.Outlined.Language,
            title = "App Language",
            value = "English",
            onClick = {}
        )
        SettingsItem(
            icon = Icons.Outlined.Notifications,
            title = "Notifications",
            onClick = {}
        )

        // Example of a Toggle Item
        var hapticEnabled by remember { mutableStateOf(true) }
        SettingsSwitchItem(
            icon = Icons.Outlined.TouchApp, // Or generic interaction icon
            title = "Haptic Feedback",
            checked = hapticEnabled,
            onCheckedChange = { hapticEnabled = it }
        )

        Spacer(Modifier.height(24.dp))

        // -------------------------
        // 2. ODIN (Chat & AI)
        // -------------------------
        SettingsSectionHeader("Odin")

        SettingsItem(
            icon = Icons.Outlined.Psychology,
            title = "Default Model",
            value = "Freya 4o", // Example model name
            onClick = {}
        )
        SettingsItem(
            icon = Icons.Outlined.Chat,
            title = "System Instructions",
            subtitle = "Customize how Odin responds",
            onClick = {}
        )
        SettingsItem(
            icon = Icons.Outlined.Mic,
            title = "Voice Mode",
            value = "Standard",
            onClick = {}
        )

        Spacer(Modifier.height(24.dp))

        // -------------------------
        // 3. ORION (Drive & Files)
        // -------------------------
        SettingsSectionHeader("Orion")

        SettingsItem(
            icon = Icons.Outlined.Cloud,
            title = "Linked Accounts",
            value = "Google Drive",
            onClick = {}
        )

        var syncEnabled by remember { mutableStateOf(false) }
        SettingsSwitchItem(
            icon = Icons.Outlined.Folder,
            title = "Auto-Sync Projects",
            checked = syncEnabled,
            onCheckedChange = { syncEnabled = it }
        )

        SettingsItem(
            icon = Icons.Outlined.Storage,
            title = "Local Storage",
            value = "1.2 GB Used",
            onClick = {}
        )

        Spacer(Modifier.height(24.dp))

        // -------------------------
        // 4. MIMIR (Memory)
        // -------------------------
        SettingsSectionHeader("Mimir")

        var memoryEnabled by remember { mutableStateOf(true) }
        SettingsSwitchItem(
            icon = Icons.Outlined.Memory, // Memory chip icon
            title = "Enable Long-term Memory",
            checked = memoryEnabled,
            onCheckedChange = { memoryEnabled = it }
        )

        SettingsItem(
            icon = Icons.Outlined.Dataset,
            title = "Manage Memories",
            onClick = {}
        )

        SettingsItem(
            icon = Icons.Outlined.Delete,
            title = "Clear All Context",
            titleColor = MaterialTheme.colorScheme.error,
            iconTint = MaterialTheme.colorScheme.error,
            showChevron = false,
            onClick = {}
        )

        // Bottom padding for scrolling
        Spacer(Modifier.height(32.dp))
    }
}

// ----------------------------------------------------------------
// REUSABLE COMPONENTS
// ----------------------------------------------------------------

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Value (e.g., "English", "Freya 4o")
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Chevron
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp), // Slightly tighter vertical padding for switches
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = Color.Transparent
            )
        )
    }
}

// Temporary Icon for TouchApp since it might need extended icons
// You can replace this with any icon from Icons.Default
val androidx.compose.material.icons.Icons.Outlined.TouchApp: ImageVector
    get() = Icons.Outlined.Memory // Placeholder if TouchApp isn't available in core