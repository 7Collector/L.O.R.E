package collector.freya.app.mimir

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import collector.freya.app.database.drive.models.FileItem
import collector.freya.app.network.models.CreateSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.combine
import collector.freya.app.mimir.workers.DriveUploadWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

sealed interface UiEvent {
    object ScrollToBottom : UiEvent
    data class CurrentPath(val path: String) : UiEvent
    object OpenFilePicker : UiEvent
    data class Toast(val message: String) : UiEvent
}

data class DriveUiState(
    val currentPath: String = "/",
    val isLoading: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val fabExtendedState: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val isGridView: Boolean = false,
    val sortOrder: String = "DATE_DESC",
    val uploadProgress: Int = -1,
    val uploadStatus: String = "",
    val isUploading: Boolean = false,
    val uploadWorkId: java.util.UUID? = null
)

@HiltViewModel
class DriveViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val driveRepository: DriveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    private var selectedId: String? = null
    var selectedItemName: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val files: Flow<PagingData<FileItem>> =
        combine(
            uiState.map { it.currentPath }.distinctUntilChanged(),
            uiState.map { it.sortOrder }.distinctUntilChanged()
        ) { path, sort ->
            Pair(path, sort)
        }.flatMapLatest { (path, sort) ->
            viewModelScope.launch {
                driveRepository.syncFiles(path)
            }
            driveRepository.files(path, sort)
        }.cachedIn(viewModelScope)

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun setSortOrder(order: String) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun openItem(item: FileItem) {
        if (_uiState.value.isSelectionMode) {
            toggleSelection(item.id)
        } else if (!item.isFile) {
            val newPath = if (_uiState.value.currentPath == "/") "/${item.name}"
            else "${_uiState.value.currentPath}/${item.name}"
            viewModelScope.launch {
                _uiState.update { it.copy(currentPath = newPath) }
                _uiEvents.emit(UiEvent.CurrentPath(newPath))
            }
        } else {
            // Handle file open
        }
    }

    fun navigateUp(): Boolean {
        if (_uiState.value.isSelectionMode) {
            clearSelection()
            return true
        }
        val index = uiState.value.currentPath.lastIndexOf("/")
        val newPath = if (index == 0) {
            "/"
        } else {
            uiState.value.currentPath.substring(0..index - 1)
        }
        viewModelScope.launch {
            _uiState.update { it.copy(currentPath = newPath) }
            _uiEvents.emit(UiEvent.CurrentPath(newPath))
        }
        return false
    }

    fun navigateToPath(newPath: String) {
        if (uiState.value.currentPath == newPath) return
        clearSelection()
        viewModelScope.launch {
            _uiState.update { it.copy(currentPath = newPath) }
            _uiEvents.emit(UiEvent.CurrentPath(newPath))
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val newSet = state.selectedIds.toMutableSet()
            if (newSet.contains(id)) newSet.remove(id) else newSet.add(id)

            state.copy(
                selectedIds = newSet,
                isSelectionMode = newSet.isNotEmpty(),
                fabExtendedState = false
            )
        }
    }

    fun toggleFabSelection() {
        _uiState.update { it.copy(fabExtendedState = !it.fabExtendedState) }
    }

    fun toggleShowCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = !it.showCreateDialog, fabExtendedState = false
            )
        }
    }

    fun toggleShowRenameDialog(id: String? = null, name: String? = null) {
        selectedId = id
        selectedItemName = name
        _uiState.update {
            it.copy(
                showRenameDialog = !it.showRenameDialog, fabExtendedState = false
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun onCreateFolderClicked(name: String) {
        toggleShowCreateDialog()
        viewModelScope.launch {
            Log.d("DriveViewModel", "Creating folder: $name, Path: ${uiState.value.currentPath}")
            val success = driveRepository.createFolder(uiState.value.currentPath, name)
            if (success) {
                driveRepository.syncFiles(uiState.value.currentPath)
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to create folder"))
            }
        }
    }

    fun onRenameClicked(newName: String) {
        val id = selectedId ?: return
        val oldName = selectedItemName ?: return
        toggleShowRenameDialog()
        viewModelScope.launch {
            val item = driveRepository.driveDao.getById(id)
            val success = driveRepository.renameItem(item.fullPath, newName)
            if (success) {
                driveRepository.syncFiles(uiState.value.currentPath)
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to rename item"))
            }
        }
    }

    fun deleteSelected() {
        val ids = uiState.value.selectedIds.toList()
        clearSelection()
        viewModelScope.launch {
            var successCount = 0
            ids.forEach { id ->
                val item = driveRepository.driveDao.getById(id)
                val success = driveRepository.deleteItem(item.fullPath)
                if (success) {
                    driveRepository.driveDao.deleteById(id)
                    successCount++
                }
            }
            if (successCount > 0) {
                _uiEvents.emit(UiEvent.Toast("Deleted $successCount item(s)"))
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to delete item(s)"))
            }
        }
    }

    fun moveSelected(newParent: String) {
        val ids = uiState.value.selectedIds.toList()
        clearSelection()
        viewModelScope.launch {
            var successCount = 0
            ids.forEach { id ->
                val item = driveRepository.driveDao.getById(id)
                val success = driveRepository.moveItem(item.fullPath, newParent)
                if (success) {
                    driveRepository.driveDao.deleteById(id)
                    successCount++
                }
            }
            if (successCount > 0) {
                driveRepository.syncFiles(uiState.value.currentPath)
                _uiEvents.emit(UiEvent.Toast("Moved $successCount item(s) to $newParent"))
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to move item(s)"))
            }
        }
    }

    fun downloadSelected() {
        val ids = uiState.value.selectedIds.toList()
        clearSelection()
        viewModelScope.launch {
            var successCount = 0
            ids.forEach { id ->
                val item = driveRepository.driveDao.getById(id)
                if (item.isFile) {
                    try {
                        val response = driveRepository.driveApi.downloadFile(item.fullPath)
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                val destinationFile = java.io.File(downloadsDir, item.name)
                                body.byteStream().use { inputStream ->
                                    java.io.FileOutputStream(destinationFile).use { outputStream ->
                                        val buffer = ByteArray(4096)
                                        var read: Int
                                        while (inputStream.read(buffer).also { read = it } != -1) {
                                            outputStream.write(buffer, 0, read)
                                        }
                                        outputStream.flush()
                                    }
                                }
                                successCount++
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (successCount > 0) {
                _uiEvents.emit(UiEvent.Toast("Downloaded $successCount item(s) to Downloads"))
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to download item(s)"))
            }
        }
    }

    fun createShareSelected(
        requiresEmail: Boolean,
        allowedEmails: List<String>?,
        password: String?,
        onShareCreated: (String) -> Unit
    ) {
        val ids = uiState.value.selectedIds.toList()
        clearSelection()
        viewModelScope.launch {
            if (ids.isEmpty()) return@launch
            val item = driveRepository.driveDao.getById(ids.first())
            val resourceId = item.id.toIntOrNull() ?: 1
            val payload = CreateSharePayload(
                resource_type = if (item.isFile) "file" else "folder",
                resource_id = resourceId,
                requires_email = requiresEmail,
                allowed_emails = allowedEmails,
                password = password
            )
            val res = driveRepository.createShare(payload)
            if (res != null) {
                onShareCreated(res.share_url)
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to create share"))
            }
        }
    }

    val sharesState = MutableStateFlow<List<collector.freya.app.network.models.ShareItem>>(emptyList())

    fun loadShares() {
        viewModelScope.launch {
            val list = driveRepository.listShares()
            sharesState.value = list
        }
    }

    fun revokeShare(shareId: Int) {
        viewModelScope.launch {
            val success = driveRepository.deleteShare(shareId)
            if (success) {
                _uiEvents.emit(UiEvent.Toast("Share revoked successfully"))
                loadShares()
            } else {
                _uiEvents.emit(UiEvent.Toast("Failed to revoke share"))
            }
        }
    }

    fun onUploadClicked() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenFilePicker)
        }
    }

    fun uploadFile(uri: Uri) {
        val uploadRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setInputData(
                workDataOf(
                    "uri" to uri.toString(),
                    "serverPath" to uiState.value.currentPath
                )
            )
            .addTag("drive_upload")
            .build()

        WorkManager.getInstance(context).enqueue(uploadRequest)

        val workId = uploadRequest.id
        _uiState.update { it.copy(uploadWorkId = workId) }
        observeUploadProgress(workId)
    }

    private fun observeUploadProgress(id: java.util.UUID) {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(id)
                .collect { workInfo ->
                    if (workInfo != null) {
                        val progress = workInfo.progress.getInt("progress", -1)
                        val status = workInfo.progress.getString("status") ?: ""
                        val isFinished = workInfo.state.isFinished
                        _uiState.update {
                            it.copy(
                                uploadProgress = progress,
                                uploadStatus = status,
                                isUploading = !isFinished
                            )
                        }
                        if (isFinished && workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                            driveRepository.syncFiles(uiState.value.currentPath)
                        }
                    }
                }
        }
    }
}