package collector.freya.app.orion

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LOG_TAG
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import collector.freya.app.database.media.models.MediaEntity
import collector.freya.app.orion.components.PhotoViewer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel = hiltViewModel(),
    setAppBarVisibility: (Boolean) -> Unit,
) {

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyStaggeredGridState()
    val media = viewModel.media.collectAsLazyPagingItems()
    Log.d("PHOTOS", "${media.itemCount} ${media.itemSnapshotList.size}")

    Box {
        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxSize(),
            columns = StaggeredGridCells.Fixed(4),
            verticalItemSpacing = 4.dp,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            state = listState
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.statusBarsPadding().height(80.dp))
            }

            items(
                count = media.itemCount,
                key = { index ->
                    val item = media[index]
                    when (item) {
                        is GalleryItem.Photo -> "photo_${item.media.id}"
                        is GalleryItem.Header -> "header_${item.timestamp}"
                        null -> "placeholder_$index"
                    }
                },
                span = { index ->
                    val item = media[index]
                    if (item is GalleryItem.Header) {
                        StaggeredGridItemSpan.FullLine
                    } else {
                        StaggeredGridItemSpan.SingleLane
                    }
                },
                contentType = { index ->
                    val item = media[index]
                    if (item is GalleryItem.Header) 0 else 1
                }
            ) { index ->
                val item = media[index]

                when (item) {
                    is GalleryItem.Header -> {
                        DateHeader(item.timestamp)
                    }
                    is GalleryItem.Photo -> {
                        PhotoItem(item.media, viewModel)
                    }
                    null -> {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
            }
        }

        PhotoViewer(photo = uiState.openedPhoto, onDismiss = {
            viewModel.closePhoto()
            setAppBarVisibility(true)
        }, setAppBarVisibility = setAppBarVisibility)
    }
}

@Composable
private fun DateHeader(timestamp: Long) {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())

    val pattern =
        if (date.year == Instant.now().atZone(ZoneId.systemDefault()).year)
            "dd MMM"
        else
            "dd MMM yyyy"

    Text(
        text = DateTimeFormatter.ofPattern(pattern).format(date),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun PhotoItem(photo: MediaEntity, viewModel: PhotosViewModel) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(photo.uri)
            .size(300, 300)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .build(),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { viewModel.openPhoto(photo) },
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    )
}
