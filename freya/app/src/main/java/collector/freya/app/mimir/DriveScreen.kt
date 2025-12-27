package collector.freya.app.mimir

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import collector.freya.app.mimir.components.DriveListItem
import collector.freya.app.mimir.components.EmptyDriveListPlaceholder
import collector.freya.app.mimir.components.GenericTextFiledDialog
import collector.freya.app.mimir.components.PathIndicator
import collector.freya.app.mimir.components.SelectionBar

@Composable
fun DriveScreen(viewModel: DriveViewModel = hiltViewModel()) {

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            if (uri != null) viewModel.uploadFile(uri)
        })
    var path by remember { mutableStateOf("/") }
    LaunchedEffect("k") {
        viewModel.uiEvents.collect { event ->
            when (event) {
                UiEvent.OpenFilePicker -> pickFileLauncher.launch(arrayOf("*/*"))
                UiEvent.ScrollToBottom -> TODO()
                is UiEvent.CurrentPath -> {
                    path = event.path
                }

                is UiEvent.Toast -> TODO()
            }
        }
    }
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = uiState.fabExtendedState,
                onClick = { viewModel.toggleFabSelection() },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Log.d("DriveScreen", "Recomposed: $path")
        DriveList(viewModel, path)

        FABs(modifier = Modifier.align(Alignment.BottomEnd), viewModel)
    }

    if (uiState.showCreateDialog) {
        GenericTextFiledDialog(onDismiss = viewModel::toggleShowCreateDialog) {
            viewModel.onCreateFolderClicked(it)
        }
    }

    if (uiState.showRenameDialog) {
        GenericTextFiledDialog(
            onDismiss = viewModel::toggleShowRenameDialog,
            placeholderText = viewModel.selectedItemName ?: "",
            titleText = "Rename",
            fieldLabel = "New name",
            doneButtonText = "Rename",
            cancelButtonText = "Cancel"
        ) {
            viewModel.onRenameClicked(it)
        }
    }
}

@Composable
fun DriveList(viewModel: DriveViewModel, path: String) {
    val uiState by viewModel.uiState.collectAsState()
    val files = viewModel.files.collectAsLazyPagingItems()
    val loading = files.loadState.refresh is LoadState.Loading
    val empty = files.loadState.refresh is LoadState.NotLoading && files.itemCount == 0

    BackHandler(uiState.currentPath != "/" || uiState.isSelectionMode) {
        viewModel.navigateUp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Spacer(
                    Modifier
                        .statusBarsPadding()
                        .height(80.dp)
                )
                PathIndicator(path) {
                    viewModel.navigateToPath(it)
                }
            }
            items(files.itemCount) {
                val file = files[it]
                if (file != null) {
                    DriveListItem(
                        viewModel,
                        file,
                        uiState.selectedIds.contains(file.id),
                        uiState.isSelectionMode,
                        { viewModel.toggleSelection(file.id) },
                        onClick = { viewModel.openItem(file) })
                }
                if (it != files.itemCount - 1) {
                    HorizontalDivider()
                }
            }

            item {
                Spacer(
                    Modifier
                        .navigationBarsPadding()
                        .height(80.dp)
                )
            }
        }

        if (empty) {
            EmptyDriveListPlaceholder()
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }

        if (uiState.isSelectionMode) {
            SelectionBar(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                uiState.selectedIds.count(),
                viewModel::clearSelection
            ) {
                viewModel.clearSelection()
                // Delete the selected items
            }
        }
    }
}

@Composable
fun FABs(modifier: Modifier = Modifier, viewModel: DriveViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier.padding(bottom = 12.dp, end = 12.dp), horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = uiState.fabExtendedState,
            enter = slideInHorizontally(initialOffsetX = { it -> it / 2 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it -> it / 2 }) + fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onUploadClicked,
                    icon = { Icon(Icons.Filled.Upload, "Extended floating action button.") },
                    text = { Text(text = "Upload") },
                )
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = viewModel::toggleShowCreateDialog,
                    icon = { Icon(Icons.Filled.Folder, "Extended floating action button.") },
                    text = { Text(text = "Create Folder") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FloatingActionButton(onClick = viewModel::toggleFabSelection) {
            Icon(
                if (uiState.fabExtendedState) Icons.Filled.Close else Icons.Filled.Add,
                "Floating action button."
            )
        }
    }
}