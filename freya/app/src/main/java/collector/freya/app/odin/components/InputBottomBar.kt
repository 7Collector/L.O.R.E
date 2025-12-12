package collector.freya.app.odin.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import collector.freya.app.R
import collector.freya.app.components.ButtonWithIcon
import collector.freya.app.odin.ChatViewModel

@Composable
fun InputBottomBar(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {

            if (uiState.attachedElements.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.attachedElements.forEach {
                        AttachmentView(attachment = it) {
                            viewModel.removeAttachment(it)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                ButtonWithIcon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Attachment",
                    buttonSize = 48.dp,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    showBorder = true,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = { viewModel.toggleBottomSheet() }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 140.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .animateContentSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (uiState.inputText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.start_chat_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    BasicTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::updateInput,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val showSendButton =
                    uiState.inputText.isNotEmpty() || uiState.attachedElements.isNotEmpty() || uiState.isResponding

                AnimatedVisibility(
                    visible = showSendButton,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = scaleOut(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    ButtonWithIcon(
                        imageVector = if (uiState.isResponding) Icons.Default.Square else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (uiState.isResponding) "Stop" else "Send Message",
                        buttonSize = 48.dp,
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        onClick = { if (uiState.isResponding) viewModel.stopGeneration() else viewModel.sendMessage() }
                    )
                }
            }
        }
    }
}