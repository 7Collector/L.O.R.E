package collector.freya.app.odin.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import collector.freya.app.odin.models.Attachment

@Composable
fun AttachmentView(attachment: Attachment, removeAttachment: () -> Unit) {
    AssistChip(
        onClick = { },
        label = { Text("📎 $attachment") },
        modifier = Modifier.padding(top = 4.dp)
    )
}