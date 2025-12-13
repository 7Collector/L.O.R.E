package collector.freya.app.orion

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import collector.freya.app.orion.components.PhotoViewer

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel = hiltViewModel(),
    setAppBarVisibility: (Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    val listState = rememberLazyStaggeredGridState()

    Box {
        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxSize(),
            columns = StaggeredGridCells.Fixed(4),
            verticalItemSpacing = 4.dp,
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(
                    Modifier
                        .statusBarsPadding()
                        .height(80.dp)
                )
            }

            uiState.photos.forEach { (dateHeader, photosInGroup) ->
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = dateHeader,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(photosInGroup) { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable(onClick = { viewModel.openPhoto(photo) }),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
        PhotoViewer(photo = uiState.openedPhoto, onDismiss = {
            viewModel.closePhoto()
            setAppBarVisibility(true)
        }, setAppBarVisibility = setAppBarVisibility)
    }
}