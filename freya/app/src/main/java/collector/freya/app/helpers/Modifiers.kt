package collector.freya.app.helpers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.conditional(
    condition: Boolean,
    modifier: @Composable Modifier.() -> Modifier,
): Modifier {
    return if (condition) {
        this.then(modifier(Modifier))
    } else {
        this
    }
}