package collector.freya.app.orion

import android.content.ContentResolver
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import collector.freya.app.database.media.models.MediaEntity
import collector.freya.app.orion.components.PhotoViewer
import collector.freya.app.network.models.MemoryAlbum
import collector.freya.app.network.models.MemoryPhoto
import collector.freya.app.network.models.MapPhoto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun MemoryPhoto.toMediaEntity(serverUrl: String): MediaEntity {
    val local = serverUrl.firstOrNull()?.isDigit() == true
    val scheme = if (local) "http" else "https"
    val fileUri = "$scheme://$serverUrl/orion/file/$id"
    return MediaEntity(
        id = id.toString(),
        uri = fileUri,
        name = name,
        size = 0,
        timestamp = 0,
        dayEpoch = 0,
        isFavorite = favorite,
        isUploaded = true
    )
}

fun MapPhoto.toMediaEntity(serverUrl: String): MediaEntity {
    val local = serverUrl.firstOrNull()?.isDigit() == true
    val scheme = if (local) "http" else "https"
    val fileUri = "$scheme://$serverUrl/orion/file/$id"
    return MediaEntity(
        id = id.toString(),
        uri = fileUri,
        name = name,
        size = 0,
        timestamp = 0,
        dayEpoch = 0,
        isFavorite = favorite,
        isUploaded = true
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel = hiltViewModel(),
    setAppBarVisibility: (Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val listState = rememberLazyStaggeredGridState()
    val media = viewModel.media.collectAsLazyPagingItems()
    val localContentResolver = LocalContext.current.contentResolver

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Photos", "Memories", "Map")

    Box {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(
                Modifier
                    .statusBarsPadding()
                    .height(80.dp)
            )
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxSize(),
                        columns = StaggeredGridCells.Fixed(4),
                        verticalItemSpacing = 4.dp,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        state = listState
                    ) {
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
                                    PhotoItem(item.media, localContentResolver, viewModel)
                                }
                                null -> {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    MemoriesView(uiState.memories, serverUrl) { mediaEntity ->
                        viewModel.openPhoto(mediaEntity)
                        setAppBarVisibility(false)
                    }
                }
                2 -> {
                    MapPhotosView(uiState.mapPhotos, serverUrl) { mediaEntity ->
                        viewModel.openPhoto(mediaEntity)
                        setAppBarVisibility(false)
                    }
                }
            }
        }

        PhotoViewer(photo = uiState.openedPhoto, onDismiss = {
            viewModel.closePhoto()
            setAppBarVisibility(true)
        }, onFavorite = {
            viewModel.setFavorite()
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
private fun PhotoItem(
    photo: MediaEntity,
    contentResolver: ContentResolver?,
    viewModel: PhotosViewModel,
) {
    val context = LocalContext.current
    val model = if (photo.uri.startsWith("http")) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .setHeader("x-api-key", "koala")
            .build()
    } else {
        contentResolver?.loadThumbnail(photo.uri.toUri(), Size(300, 300), null) ?: photo.uri
    }

    AsyncImage(
        model = model,
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

@Composable
fun MemoriesView(
    memories: List<MemoryAlbum>,
    serverUrl: String,
    onPhotoClick: (MediaEntity) -> Unit
) {
    if (memories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No memories for today yet!")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(memories.size) { index ->
                val memory = memories[index]
                Column {
                    Text(
                        text = "${memory.title} (${memory.year})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(memory.photos.size) { photoIndex ->
                            val photo = memory.photos[photoIndex]
                            val local = serverUrl.firstOrNull()?.isDigit() == true
                            val scheme = if (local) "http" else "https"
                            val thumbUrl = "$scheme://$serverUrl/orion/thumb/${photo.id}"
                            val context = LocalContext.current
                            val model = ImageRequest.Builder(context)
                                .data(thumbUrl)
                                .setHeader("x-api-key", "koala")
                                .build()

                            AsyncImage(
                                model = model,
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .clickable { onPhotoClick(photo.toMediaEntity(serverUrl)) },
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapPhotosView(
    mapPhotos: List<MapPhoto>,
    serverUrl: String,
    onPhotoClick: (MediaEntity) -> Unit
) {
    if (mapPhotos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos with location coordinates found.")
        }
    } else {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            items(mapPhotos.size) { index ->
                val photo = mapPhotos[index]
                val local = serverUrl.firstOrNull()?.isDigit() == true
                val scheme = if (local) "http" else "https"
                val thumbUrl = "$scheme://$serverUrl${photo.thumbnail_url}"
                val context = LocalContext.current
                val model = ImageRequest.Builder(context)
                    .data(thumbUrl)
                    .setHeader("x-api-key", "koala")
                    .build()

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .aspectRatio(1f)
                        .clickable { onPhotoClick(photo.toMediaEntity(serverUrl)) }
                ) {
                    AsyncImage(
                        model = model,
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    // Geotag coordinates overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%.3f, %.3f", photo.latitude, photo.longitude),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}