package collector.freya.app.mimir.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import collector.freya.app.helpers.lastIndexOfOrLastIndex
import kotlinx.coroutines.delay

@Composable
fun GenericTextFiledDialog(
    onDismiss: () -> Unit,
    placeholderText: String = "Untitled folder",
    titleText: String = "New folder",
    fieldLabel: String = "Folder name",
    doneButtonText: String = "Create",
    cancelButtonText: String = "Cancel",
    onDone: (String) -> Unit,
) {
    var textState by remember {
        mutableStateOf(
            TextFieldValue(
                text = placeholderText,
                selection = TextRange(0, placeholderText.lastIndexOfOrLastIndex("."))
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = titleText)
        },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(fieldLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onAny = {
                        if (textState.text.isNotBlank()) {
                            onDone(textState.text)
                        }
                    }
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(textState.text) },
                enabled = textState.text.isNotBlank()
            ) {
                Text(doneButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelButtonText)
            }
        }
    )
}