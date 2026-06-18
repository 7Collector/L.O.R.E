package collector.freya.app.mimir

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import collector.freya.app.mimir.components.DriveListItem
import collector.freya.app.mimir.components.DriveGridItem
import collector.freya.app.mimir.components.EmptyDriveListPlaceholder
import collector.freya.app.mimir.components.GenericTextFiledDialog
import collector.freya.app.mimir.components.PathIndicator
import collector.freya.app.mimir.components.SelectionBar

@Composable
fun DriveScreen(viewModel: DriveViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            if (uri != null) viewModel.uploadFile(uri)
        })
    var path by remember { mutableStateOf("/") }

    // Dialog Dialog States
    var showShareDialog by remember { mutableStateOf(false) }
    var showShareResultDialog by remember { mutableStateOf(false) }
    var generatedShareUrl by remember { mutableStateOf("") }
    var showMoveDialog by remember { mutableStateOf(false) }

    LaunchedEffect("k") {
        viewModel.uiEvents.collect { event ->
            when (event) {
                UiEvent.OpenFilePicker -> pickFileLauncher.launch(arrayOf("*/*"))
                UiEvent.ScrollToBottom -> {}
                is UiEvent.CurrentPath -> {
                    path = event.path
                }
                is UiEvent.Toast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
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
        DriveList(
            viewModel,
            path,
            onShareClicked = { showShareDialog = true },
            onMoveClicked = { showMoveDialog = true }
        )

        FABs(modifier = Modifier.align(Alignment.BottomEnd), viewModel)

        // Persistent Upload Progress Card
        if (uiState.isUploading) {
            androidx.compose.material3.Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp)
                    .fillMaxWidth(),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { if (uiState.uploadProgress >= 0) uiState.uploadProgress / 100f else 0f },
                        modifier = Modifier.size(40.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Uploading File...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.uploadStatus} (${if (uiState.uploadProgress >= 0) "${uiState.uploadProgress}%" else "Preparing"})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (uiState.showCreateDialog) {
        GenericTextFiledDialog(onDismiss = viewModel::toggleShowCreateDialog) {
            viewModel.onCreateFolderClicked(it)
        }
    }

    if (uiState.showRenameDialog) {
        GenericTextFiledDialog(
            onDismiss = { viewModel.toggleShowRenameDialog(null, null) },
            placeholderText = viewModel.selectedItemName ?: "",
            titleText = "Rename",
            fieldLabel = "New name",
            doneButtonText = "Rename",
            cancelButtonText = "Cancel"
        ) {
            viewModel.onRenameClicked(it)
        }
    }

    if (showShareDialog) {
        ShareDialog(
            onDismiss = { showShareDialog = false },
            onConfirm = { requiresEmail, emails, password ->
                showShareDialog = false
                viewModel.createShareSelected(requiresEmail, emails, password) { url ->
                    generatedShareUrl = url
                    showShareResultDialog = true
                }
            }
        )
    }

    if (showShareResultDialog) {
        ShareLinkResultDialog(
            shareUrl = generatedShareUrl,
            onDismiss = { showShareResultDialog = false }
        )
    }

    if (showMoveDialog) {
        MoveDialog(
            onDismiss = { showMoveDialog = false },
            onConfirm = { dest ->
                showMoveDialog = false
                viewModel.moveSelected(dest)
            }
        )
    }
}

@Composable
fun DriveList(
    viewModel: DriveViewModel,
    path: String,
    onShareClicked: () -> Unit,
    onMoveClicked: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val files = viewModel.files.collectAsLazyPagingItems()
    val loading = files.loadState.refresh is LoadState.Loading
    val empty = files.loadState.refresh is LoadState.NotLoading && files.itemCount == 0

    BackHandler(uiState.currentPath != "/" || uiState.isSelectionMode) {
        viewModel.navigateUp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(
                Modifier
                    .statusBarsPadding()
                    .height(80.dp)
            )
            PathIndicator(path) {
                viewModel.navigateToPath(it)
            }
            DriveControlBar(
                isGridView = uiState.isGridView,
                onToggleView = viewModel::toggleViewMode,
                currentSort = uiState.sortOrder,
                onSortSelected = viewModel::setSortOrder
            )

            if (uiState.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    items(files.itemCount) { index ->
                        val file = files[index]
                        if (file != null) {
                            DriveGridItem(
                                viewModel = viewModel,
                                file = file,
                                isSelected = uiState.selectedIds.contains(file.id),
                                isSelectionMode = uiState.isSelectionMode,
                                onLongClick = { viewModel.toggleSelection(file.id) },
                                onClick = { viewModel.openItem(file) }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(
                            Modifier
                                .navigationBarsPadding()
                                .height(80.dp)
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files.itemCount) { index ->
                        val file = files[index]
                        if (file != null) {
                            DriveListItem(
                                viewModel = viewModel,
                                file = file,
                                isSelected = uiState.selectedIds.contains(file.id),
                                isSelectionMode = uiState.isSelectionMode,
                                onLongClick = { viewModel.toggleSelection(file.id) },
                                onClick = { viewModel.openItem(file) })
                        }
                        if (index != files.itemCount - 1) {
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
            }
        }

        if (empty) {
            EmptyDriveListPlaceholder()
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (uiState.isSelectionMode) {
            SelectionBar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                selectedCount = uiState.selectedIds.count(),
                dismissSelectionMode = viewModel::clearSelection,
                onDeleteClicked = viewModel::deleteSelected,
                onShareClicked = onShareClicked,
                onMoveClicked = onMoveClicked,
                onDownloadClicked = viewModel::downloadSelected
            )
        }
    }
}

@Composable
fun DriveControlBar(
    isGridView: Boolean,
    onToggleView: () -> Unit,
    currentSort: String,
    onSortSelected: (String) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clickable { showSortMenu = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = when (currentSort) {
                        "NAME_ASC" -> "Name (A-Z)"
                        "NAME_DESC" -> "Name (Z-A)"
                        "DATE_ASC" -> "Date (Oldest)"
                        "DATE_DESC" -> "Date (Newest)"
                        "SIZE_ASC" -> "Size (Smallest)"
                        "SIZE_DESC" -> "Size (Largest)"
                        "TYPE_ASC" -> "Type (A-Z)"
                        "TYPE_DESC" -> "Type (Z-A)"
                        else -> "Sort By"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            androidx.compose.material3.DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                listOf(
                    "DATE_DESC" to "Date (Newest)",
                    "DATE_ASC" to "Date (Oldest)",
                    "NAME_ASC" to "Name (A-Z)",
                    "NAME_DESC" to "Name (Z-A)",
                    "SIZE_DESC" to "Size (Largest)",
                    "SIZE_ASC" to "Size (Smallest)",
                    "TYPE_ASC" to "Type (A-Z)",
                    "TYPE_DESC" to "Type (Z-A)"
                ).forEach { (value, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSortSelected(value)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                contentDescription = "Toggle View Mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FABs(modifier: Modifier = Modifier, viewModel: DriveViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier.padding(bottom = 12.dp, end = 12.dp), horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = uiState.fabExtendedState,
            enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
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

@Composable
fun ShareDialog(
    onDismiss: () -> Unit,
    onConfirm: (requiresEmail: Boolean, emails: List<String>?, password: String?) -> Unit
) {
    var requiresEmail by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    val emails = remember { mutableStateListOf<String>() }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Item") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(requiresEmail, if (requiresEmail) emails.toList() else null, password.takeIf { it.isNotEmpty() })
            }) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restrict to specific people")
                    androidx.compose.material3.Switch(
                        checked = requiresEmail,
                        onCheckedChange = { requiresEmail = it }
                    )
                }

                if (requiresEmail) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Add people (email)") },
                        trailingIcon = {
                            if (emailInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (emailInput.contains("@") && !emails.contains(emailInput.trim())) {
                                        emails.add(emailInput.trim())
                                        emailInput = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Add, "Add")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(emails.size) { index ->
                            val email = emails[index]
                            androidx.compose.material3.InputChip(
                                selected = true,
                                onClick = { emails.remove(email) },
                                label = { Text(email) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password protection (optional)") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun ShareLinkResultDialog(
    shareUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Ready") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Share Link", shareUrl)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Link copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Text("Copy Link")
            }
        },
        text = {
            Column {
                Text("Anyone with this link can view this file:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = shareUrl,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun MoveDialog(
    onDismiss: () -> Unit,
    onConfirm: (destinationPath: String) -> Unit
) {
    var destinationPath by remember { mutableStateOf("/") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Items") },
        confirmButton = {
            TextButton(onClick = { onConfirm(destinationPath) }) { Text("Move") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column {
                Text("Enter the destination folder path (e.g. /photos):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = { destinationPath = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}