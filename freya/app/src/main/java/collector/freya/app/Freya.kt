package collector.freya.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import collector.freya.app.mimir.DriveScreen
import collector.freya.app.odin.ChatScreen
import collector.freya.app.orion.PhotosScreen
import collector.freya.app.settings.SettingsScreen
import collector.freya.app.ui.theme.FreyaTheme
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    FreyaTheme {
        ModalNavigationDrawer(
            drawerState = drawerState, drawerContent = {
                MainDrawer(viewModel) {
                    scope.launch {
                        drawerState.close()
                    }
                }
            }) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = { AppTopBar(viewModel) }) { padding ->
                Box(Modifier.padding(padding)) {
                    when (uiState.mainScreenState) {
                        MainScreenState.ChatScreen -> ChatScreen()
                        MainScreenState.DriveScreen -> DriveScreen()
                        MainScreenState.PhotosScreen -> PhotosScreen()
                        MainScreenState.SettingsScreen -> SettingsScreen()
                    }
                }
            }
        }
    }
}