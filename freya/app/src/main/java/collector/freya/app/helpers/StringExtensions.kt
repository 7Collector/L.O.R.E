package collector.freya.app.helpers

fun CharSequence.lastIndexOfOrLastIndex(
    string: String,
    startIndex: Int = lastIndex,
    ignoreCase: Boolean = false,
): Int {
    val index = this.lastIndexOf(string, startIndex, ignoreCase)
    return if (index == -1) this.lastIndex + 1 else index
}