package collector.freya.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import collector.freya.app.helpers.getTitleByScreen

@Composable
fun MainDrawer(viewModel: MainViewModel, currentScreen: MainScreenState, closeDrawer: () -> Unit) {

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Freya",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 24.dp)
            )

            Text(
                text = "Modes",
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline
            )

            MainScreenState.entries.forEach { screen ->
                val isSelected = currentScreen == screen

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = getTitleByScreen(screen),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    },
                    selected = isSelected,
                    onClick = {
                        viewModel.onScreenSelected(screen)
                        closeDrawer()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            when (currentScreen) {
                MainScreenState.ChatScreen -> ChatHistorySection()
                MainScreenState.DriveScreen -> DriveControlsSection()
                MainScreenState.PhotosScreen -> {}
                MainScreenState.SettingsScreen -> {}
            }
        }
    }
}

@Composable
fun ChatHistorySection() {
    Text(
        text = "Chat History",
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline
    )

    // Dummy Data for Preview
    val history = listOf("Project L.O.R.E", "Physics Assignment", "Weekend Trip", "Recipe Ideas")

    history.forEach { title ->
        NavigationDrawerItem(
            label = { Text(title) },
            selected = false,
            onClick = { /* Load History */ },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
fun DriveControlsSection() {
    Text(
        text = "Drive Locations",
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline
    )

    val controls = listOf("My Files", "Shared with me", "Starred", "Trash")

    controls.forEach { title ->
        NavigationDrawerItem(
            label = { Text(title) },
            selected = false,
            onClick = { /* Navigate Drive */ },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}