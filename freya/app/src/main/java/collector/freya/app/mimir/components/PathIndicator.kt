package collector.freya.app.mimir.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun PathIndicator(path: String, moveToPath: (String) -> Unit) {
    val list = if (path == "/") listOf("/") else listOf("/") + path.substring(1).split("/")
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(path) {
        scope.launch {
            state.animateScrollToItem(list.lastIndex)
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        state = state
    ) {
        itemsIndexed(list) { index, it ->
            val color =
                if (index == list.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.clickable(onClick = {
                        val newPath = "/" + list.subList(1, index + 1).joinToString("/")
                        moveToPath(newPath)
                    }),
                    text = it, style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    ), color = color
                )
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(horizontal = 4.dp),
                    imageVector = Icons.Default.ChevronRight,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = ""
                )
            }
        }
    }
}