package collector.freya.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainDrawer(viewModel: MainViewModel, closeDrawer: () -> Unit) {
    ModalDrawerSheet() {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Freya",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge
            )

            HorizontalDivider()

            Text(
                "Modes",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            MainScreenState.entries.forEach {
                NavigationDrawerItem(
                    label = {
                        Text(
                            it.name,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    selected = true,
                    onClick = {
                        viewModel.onScreenSelected(it)
                        closeDrawer()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            Text(
                "Screen Specific Section",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}