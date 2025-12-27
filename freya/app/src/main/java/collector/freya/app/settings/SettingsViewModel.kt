package collector.freya.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import collector.freya.app.database.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isServerDialogVisible: Boolean = false,
    val hapticEnabled: Boolean = true,
    val syncEnabled: Boolean = false,
    val memoryEnabled: Boolean = true
)

sealed interface SettingsEvent {
    data object RestartApp : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState = combine(
        _uiState,
        preferencesRepository.getServerBaseUrl(),
        preferencesRepository.getApiKey()
    ) { viewState, serverUrl, apiKey ->
        viewState.copy(
            serverUrl = serverUrl,
            apiKey = apiKey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    fun toggleServerDialog(isOpen: Boolean) {
        _uiState.update { it.copy(isServerDialogVisible = isOpen) }
    }

    fun saveServerConfigAndRestart(newUrl: String, newKey: String) {
        viewModelScope.launch {
            preferencesRepository.updateServerConfiguration(newUrl, newKey)
            toggleServerDialog(false)
            _events.emit(SettingsEvent.RestartApp)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticEnabled = enabled) }
        // TODO: Persist this to PreferencesRepository if needed
    }

    fun setSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(syncEnabled = enabled) }
        // TODO: Persist this to PreferencesRepository if needed
    }

    fun setMemoryEnabled(enabled: Boolean) {
        _uiState.update { it.copy(memoryEnabled = enabled) }
        // TODO: Persist this to PreferencesRepository if needed
    }
}