package collector.freya.app.helpers

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import collector.freya.app.MainScreenState
import collector.freya.app.R

// For Module Names
@Composable
fun getTitleByScreen(screen: MainScreenState): String {
    return when (screen) {
        MainScreenState.ChatScreen -> stringResource(R.string.title_chat_screen)
        MainScreenState.DriveScreen -> stringResource(R.string.title_drive_screen)
        MainScreenState.PhotosScreen -> stringResource(R.string.title_photos_screen)
        MainScreenState.SettingsScreen -> stringResource(R.string.title_settings_screen)
    }
}