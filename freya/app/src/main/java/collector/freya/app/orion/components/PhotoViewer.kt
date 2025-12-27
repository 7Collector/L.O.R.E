package collector.freya.app.orion.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import collector.freya.app.components.ButtonWithIcon
import collector.freya.app.database.media.models.MediaEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PhotoViewer(
    photo: MediaEntity?,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    setAppBarVisibility: (Boolean) -> Unit,
) {
    var isVisible by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val view = LocalView.current
    val window = (view.context as? ComponentActivity)?.window

    BackHandler(enabled = photo != null) {
        onDismiss()
    }

    if (showInfoDialog && photo != null) {
        PhotoInfoDialog(
            photo = photo, onDismiss = { showInfoDialog = false })
    }

    AnimatedVisibility(
        visible = photo != null, enter = fadeIn(), exit = fadeOut()
    ) {
        photo?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {

                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                AsyncImage(
                    model = item.uri.toUri(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    val windowsInsetsController =
                                        WindowCompat.getInsetsController(window!!, window.decorView)
                                    if (isVisible) windowsInsetsController.hide(
                                        WindowInsetsCompat.Type.systemBars()
                                    )
                                    else windowsInsetsController.show(WindowInsetsCompat.Type.systemBars())
                                    isVisible = !isVisible
                                    setAppBarVisibility(isVisible)
                                })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    val newOffset = offset + pan
                                    offset = newOffset
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        })

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize()) {

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 16.dp, end = 16.dp)
                        ) {
                            ButtonWithIcon(
                                modifier = Modifier,
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                buttonSize = 48.dp,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                showBorder = true,
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = onDismiss
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent, Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                                .navigationBarsPadding()
                                .padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val activity = LocalActivity.current
                                ViewerActionButton(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    onClick = { share(activity, item.uri.toUri()) })
                                ViewerActionButton(
                                    icon = if (item.isFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                                    label = "Favorite",
                                    onClick = onFavorite
                                )
                                ViewerActionButton(
                                    icon = Icons.Default.Delete, label = "Delete", onClick = {

                                    })
                                ViewerActionButton(
                                    icon = Icons.Default.Info,
                                    label = "Info",
                                    onClick = { showInfoDialog = true })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon, contentDescription = label, tint = Color.White
        )
    }
}

@Composable
fun PhotoInfoDialog(
    photo: MediaEntity,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val instant = Instant.ofEpochMilli(photo.timestamp)
    val dateTime = instant.atZone(ZoneId.systemDefault())
    val formattedDate = DateTimeFormatter.ofPattern("MMM dd, yyyy  h:mm a").format(dateTime)

    val formattedSize = Formatter.formatFileSize(context, photo.size.toLong())

    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = onDismiss) {
            Text("Close")
        }
    }, title = {
        Text(text = "Details")
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(label = "Date", value = formattedDate)
            InfoRow(label = "Name", value = photo.name)
            InfoRow(label = "Size", value = formattedSize)
            InfoRow(label = "Path", value = photo.uri.toUri().path ?: "Unknown")
        }
    })
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value, style = MaterialTheme.typography.bodyMedium
        )
    }
}

fun share(activity: Activity?, uri: Uri) {
    val shareIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "image/jpeg"
    }
    activity?.startActivity(Intent.createChooser(shareIntent, null))
}