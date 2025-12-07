package collector.freya.app.odin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import collector.freya.app.odin.components.ChatMessageItem
import collector.freya.app.odin.components.EmptyChatScreen
import collector.freya.app.odin.components.InputBottomBar
import collector.freya.app.odin.components.MoreOptionsBottomSheet

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect("k") {
        viewModel.events.collect { event ->
            when (event) {
                UIEvent.ScrollToBottom -> TODO()
                is UIEvent.Toast -> TODO()
            }
        }
    }


    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.messages.isEmpty()) {
            Spacer(Modifier.weight(1.0f))
            EmptyChatScreen(onPromptSelected = viewModel::updateInput)
            Spacer(Modifier.weight(1.0f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                items(uiState.messages) { message ->
                    ChatMessageItem(message)
                }
                item {
                    Spacer(Modifier.weight(1.0f))
                }
            }
        }
        InputBottomBar(viewModel)
    }

    if (uiState.isOptionsBottomSheetOpen) {
        MoreOptionsBottomSheet(viewModel)
    }
}