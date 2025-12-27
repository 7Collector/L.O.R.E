package collector.freya.app.mimir.components

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import collector.freya.app.database.drive.models.FileItem
import collector.freya.app.mimir.DriveViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveListItem(
    viewModel: DriveViewModel,
    file: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    var expanded by remember { mutableStateOf(false) }
    val formattedDate = remember(file.timestamp) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.timestamp))
    }
    val formattedSize = remember(file.size) {
        if (!file.isFile) "" else Formatter.formatFileSize(context, file.size.toLong())
    }
    val subtitleText = if (file.isFile) "$formattedDate • $formattedSize" else formattedDate

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick, onLongClick = onLongClick
            )
            .background(backgroundColor)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                val (icon, tint) = getFileIconAndColor(file.name, file.isFile)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable(onClick = onLongClick)
                        .padding(4.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }


        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MoreOptionsMenu(
                    viewModel = viewModel,
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    id = file.id,
                    name = file.name
                )
            }
        }
    }

}

// --- Helper: Icon Guessing Logic ---
@Composable
fun getFileIconAndColor(filename: String, isFile: Boolean): Pair<ImageVector, Color> {
    if (!isFile) {
        // Folder Color (Usually a dark gray or subtle color in Drive list view)
        return Pair(Icons.Filled.Folder, Color(0xFF5F6368))
    }

    val extension = filename.substringAfterLast('.', "").lowercase()

    return when (extension) {
        // Images
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> Pair(
            Icons.Outlined.Image, Color(0xFFD93025)
        ) // Red-ish

        // PDFs
        "pdf" -> Pair(Icons.Default.PictureAsPdf, Color(0xFFF40F02)) // PDF Red

        // Audio
        "mp3", "wav", "aac", "flac", "m4a" -> Pair(
            Icons.Default.AudioFile, Color(0xFF1A73E8)
        ) // Blue

        // Video
        "mp4", "mkv", "avi", "mov" -> Pair(Icons.Default.VideoFile, Color(0xFFD93025)) // Red

        // Code / Web
        "html", "css", "js", "json", "xml", "kt", "java", "py" -> Pair(
            Icons.Default.Code, Color(0xFF1E8E3E)
        ) // Green

        // Archives
        "zip", "rar", "7z", "tar" -> Pair(
            Icons.Default.FolderZip, Color(0xFFF9AB00)
        ) // Yellow/Orange

        // Documents (Approximation for Drive colors)
        "doc", "docx", "txt" -> Pair(Icons.Outlined.Description, Color(0xFF1A73E8)) // Docs Blue
        "xls", "xlsx", "csv" -> Pair(Icons.Default.TableChart, Color(0xFF1E8E3E)) // Sheets Green
        "ppt", "pptx" -> Pair(Icons.Default.Slideshow, Color(0xFFF9AB00)) // Slides Yellow

        // Default
        else -> Pair(Icons.Outlined.Description, Color(0xFF5F6368)) // Gray
    }
}