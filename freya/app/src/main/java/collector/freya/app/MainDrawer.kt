package collector.freya.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import collector.freya.app.helpers.getTitleByScreen
import java.util.UUID

@Composable
fun MainDrawer(
    viewModel: MainViewModel,
    currentScreen: MainScreenState,
    onChatSelected: (String) -> Unit,
    closeDrawer: () -> Unit,
) {

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight()
            // .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Freya",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
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
                MainScreenState.ChatScreen -> ChatHistorySection(viewModel, onChatSelected, closeDrawer)
                MainScreenState.DriveScreen -> DriveControlsSection()
                MainScreenState.PhotosScreen -> {}
                MainScreenState.SettingsScreen -> {}
            }
        }
    }
}

@Composable
fun ChatHistorySection(
    viewModel: MainViewModel,
    onChatSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = "Chat History",
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline
    )

    val state = rememberLazyListState()
    val history = viewModel.chats.collectAsLazyPagingItems()

    LazyColumn(modifier = Modifier.fillMaxWidth(), state = state) {
        item {
            NavigationDrawerItem(
                label = { Text("Create New Chat") },
                selected = false,
                onClick = {
                    onChatSelected(UUID.randomUUID().toString())
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        items(history.itemSnapshotList) { chat ->
            if (chat != null) {
                NavigationDrawerItem(
                    label = { Text(chat.title) },
                    selected = false,
                    onClick = {
                        onChatSelected(chat.id)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        history.apply {
            when {
                loadState.append is LoadState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.padding(
                                vertical = 12.dp
                            ), contentAlignment = Alignment.Center

                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
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