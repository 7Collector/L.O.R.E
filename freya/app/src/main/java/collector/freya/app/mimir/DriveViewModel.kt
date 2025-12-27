package collector.freya.app.mimir

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import collector.freya.app.database.drive.models.FileItem
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
    val showRenameDialog: Boolean = false
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
        uiState.map { it.currentPath }.distinctUntilChanged().flatMapLatest { path ->
            driveRepository.files(path)
        }.cachedIn(viewModelScope)

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
            driveRepository.createFolder(uiState.value.currentPath, name)
        }
    }

    fun onRenameClicked(newName: String) {
        toggleShowCreateDialog()
    }

    fun onUploadClicked() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenFilePicker)
        }
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            driveRepository.uploadFile(uri, uiState.value.currentPath)
        }
    }
}