package collector.freya.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import collector.freya.app.database.PreferencesRepository
import collector.freya.app.mimir.DriveRepository
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

data class ActiveSession(
    val id: String,
    val deviceLabel: String,
    val createdTime: String,
    val lastActive: String,
    val isCurrent: Boolean
)

data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isServerDialogVisible: Boolean = false,
    val hapticEnabled: Boolean = true,
    val syncEnabled: Boolean = false,
    val memoryEnabled: Boolean = true,
    val themeMode: String = "System",
    val dynamicColorEnabled: Boolean = true,
    val serverUsageBytes: Long = 0L,
    val activeSessions: List<ActiveSession> = emptyList()
)

sealed interface SettingsEvent {
    data object RestartApp : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val driveRepository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState = combine(
        _uiState,
        preferencesRepository.getServerBaseUrl(),
        preferencesRepository.getApiKey(),
        preferencesRepository.getThemeMode(),
        preferencesRepository.getDynamicColorEnabled()
    ) { viewState, serverUrl, apiKey, themeMode, dynamicColor ->
        viewState.copy(
            serverUrl = serverUrl,
            apiKey = apiKey,
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColor
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    init {
        // Initialize mock sessions
        _uiState.update {
            it.copy(
                activeSessions = listOf(
                    ActiveSession("1", "Current Device (Android)", "2 hours ago", "Just now", true),
                    ActiveSession("2", "Web Portal (Chrome/Linux)", "Yesterday", "1 hour ago", false),
                    ActiveSession("3", "Lore CLI (Terminal)", "5 days ago", "Yesterday", false)
                )
            )
        }
        // Fetch server disk usage
        fetchServerUsage()
    }

    fun fetchServerUsage() {
        viewModelScope.launch {
            val usage = driveRepository.getUsage()
            _uiState.update { it.copy(serverUsageBytes = usage) }
        }
    }

    fun revokeSession(sessionId: String) {
        _uiState.update { state ->
            state.copy(
                activeSessions = state.activeSessions.filter { it.id != sessionId }
            )
        }
    }

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

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.updateThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateDynamicColorEnabled(enabled)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticEnabled = enabled) }
    }

    fun setSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(syncEnabled = enabled) }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        _uiState.update { it.copy(memoryEnabled = enabled) }
    }
}