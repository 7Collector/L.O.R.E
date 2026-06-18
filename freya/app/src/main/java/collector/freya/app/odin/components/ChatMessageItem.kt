package collector.freya.app.odin.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        UserMessageBubble(prompt = message.prompt)

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
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
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
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = message.model.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

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

            if (message.reply.isNotEmpty()) {
                AIMessageRenderer(
                    markdown = message.reply,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (message.state == MessageState.PROCESSING && message.reply.isEmpty()) {
                ThreeDotLoadingIndicator()
            }

            if (message.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                message.attachments.forEach { attachment ->
                    AttachmentView(attachment) { }
                }
            }

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

@Composable
fun ThreeDotLoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")

    val delay1 = 0
    val delay2 = 150
    val delay3 = 300

    @Composable
    fun dotAlpha(delay: Int): Float {
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 600
                    0.2f at delay
                    1f at delay + 150
                    0.2f at delay + 300
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_alpha_$delay"
        )
        return alpha
    }

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        val size = 6.dp

        Box(Modifier.size(size).background(dotColor.copy(alpha = dotAlpha(delay1)), CircleShape))
        Box(Modifier.size(size).background(dotColor.copy(alpha = dotAlpha(delay2)), CircleShape))
        Box(Modifier.size(size).background(dotColor.copy(alpha = dotAlpha(delay3)), CircleShape))
    }
}