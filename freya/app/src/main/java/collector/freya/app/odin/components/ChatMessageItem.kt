package collector.freya.app.odin.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        // 1. User Message (Right Aligned Bubble)
        UserMessageBubble(prompt = message.prompt)

        // 2. AI Response (Left Aligned Icon + Text)
        // Show AI part if there is a reply, updates, or it's currently processing/failed
        if (message.reply.isNotEmpty() || message.updates.isNotEmpty() || message.state != MessageState.SUCCESS) {
            Spacer(modifier = Modifier.height(16.dp))
            AiResponseBlock(message)
        }
    }
}

@Composable
private fun UserMessageBubble(prompt: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AiResponseBlock(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // AI Avatar
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome, // Or your app logo
                contentDescription = "AI",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            // Model Name (Optional Caption)
            Text(
                text = message.model.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Status Updates (Searching, Processing...)
            if (message.updates.isNotEmpty() && message.reply.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.updates.last(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Main Content
            if (message.reply.isNotEmpty()) {
                AIMessageRenderer(
                    markdown = message.reply,
                    // Pass your specific styling here so it matches the rest of your app
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (message.state == MessageState.PROCESSING && message.updates.isEmpty()) {
                // Blink cursor or simple text for initial loading
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Attachments
            if (message.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                message.attachments.forEach { attachment ->
                    AssistChip(
                        onClick = { },
                        label = { Text("📎 $attachment") },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Error State
            if (message.state == MessageState.FAILED || message.state == MessageState.NETWORK_ERROR) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.state.showText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}