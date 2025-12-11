package collector.freya.app.odin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 1. Main Entry Point ---
@Composable
fun AIMessageRenderer(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Re-parse whenever the stream updates
            val segments = remember(markdown) { parseStreamingMarkdown(markdown) }

            segments.forEach { segment ->
                when (segment) {
                    is MarkdownSegment.CodeBlock -> {
                        CodeWindow(
                            code = segment.content,
                            language = segment.language
                        )
                    }
                    is MarkdownSegment.Text -> {
                        MarkdownText(
                            content = segment.content,
                            style = style,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

// --- 2. Text Renderer (Fixed Bullet Points) ---
@Composable
private fun MarkdownText(content: String, style: TextStyle, color: Color) {
    val lines = content.split("\n")
    val boldNumberedListRegex = Regex("^\\*\\*\\d+\\..*")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val trimmed = line.trim()

            when {
                // 1. Bold Numbered Headers: "**1. Title**"
                trimmed.matches(boldNumberedListRegex) -> {
                    Text(
                        text = parseBoldFormatting(trimmed),
                        style = style.copy(fontSize = (style.fontSize.value * 1.1).sp),
                        color = color,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                // 2. Bullet Points (* or -)
                // We check if it starts with * or - AND has a space after (or is just the char)
                (trimmed.startsWith("*") || trimmed.startsWith("-")) && trimmed.length > 1 -> {
                    // Robust clean: remove the * or - and trim leading spaces
                    val cleanText = trimmed.substring(1).trim()

                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "•",
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = color
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseBoldFormatting(cleanText),
                            style = style,
                            color = color
                        )
                    }
                }

                // 3. Regular Text
                else -> {
                    // Preserve some indentation for code-like text in normal paragraphs
                    val originalIndent = line.takeWhile { it == ' ' }.length
                    val modifier = if (originalIndent > 2) Modifier.padding(start = 16.dp) else Modifier

                    Text(
                        text = parseBoldFormatting(trimmed),
                        style = style,
                        color = color,
                        modifier = modifier
                    )
                }
            }
        }
    }
}

// --- 3. Code Window (Wrapped & Better Highlight) ---
@Composable
private fun CodeWindow(code: String, language: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E)) // Dark Theme Background
            .padding(12.dp)
    ) {
        // Language Label
        if (language.isNotBlank()) {
            Text(
                text = language.uppercase(),
                color = Color(0xFF80CBC4),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Code Content - Now WRAPPED (No horizontal scroll)
        Text(
            text = highlightSyntax(code),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            softWrap = true // Wraps text to next line
        )
    }
}

// --- 4. Syntax Highlighting (Keywords, Functions, Comments) ---
fun highlightSyntax(code: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        fun applyStyle(regex: Regex, style: SpanStyle) {
            regex.findAll(code).forEach { matchResult ->
                // Ensure we don't crash if regex finds something out of bounds (safety check)
                if (matchResult.range.last < code.length) {
                    addStyle(style, matchResult.range.first, matchResult.range.last + 1)
                }
            }
        }

        // A. Comments (# for Python, // for others) -> GREY ITALIC
        // We do this first so other rules don't override comments
        val comments = "(#.*)|(//.*)"
        applyStyle(Regex(comments), SpanStyle(color = Color(0xFF757575), fontStyle = FontStyle.Italic))

        // B. Keywords (Python + General) -> ORANGE
        val keywords = "\\b(def|class|return|import|from|if|else|elif|for|while|try|except|with|as|pass|lambda|public|private|fun|val|var|true|false|null|None|True|False)\\b"
        applyStyle(Regex(keywords), SpanStyle(color = Color(0xFFCC7832), fontWeight = FontWeight.Bold))

        // C. Strings ('...' or "...") -> GREEN
        val strings = "(\".*?\")|('.*?')"
        applyStyle(Regex(strings), SpanStyle(color = Color(0xFF6A8759)))

        // D. Function Calls (word followed by opening paren) -> BLUE/YELLOW
        // Lookahead (?=\() ensures we match the word before the '('
        val functionCalls = "\\b\\w+(?=\\()"
        applyStyle(Regex(functionCalls), SpanStyle(color = Color(0xFFFFC66D))) // IDE-like yellow for functions

        // E. Numbers -> BLUE
        val numbers = "\\b\\d+(\\.\\d+)?\\b"
        applyStyle(Regex(numbers), SpanStyle(color = Color(0xFF6897BB)))
    }
}

// --- 5. Streaming Parser (Preserved) ---
sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class CodeBlock(val content: String, val language: String) : MarkdownSegment()
}

fun parseStreamingMarkdown(input: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    var currentIndex = 0

    while (currentIndex < input.length) {
        val openIndex = input.indexOf("```", currentIndex)

        if (openIndex == -1) {
            val text = input.substring(currentIndex)
            if (text.isNotBlank()) segments.add(MarkdownSegment.Text(text))
            break
        }

        if (openIndex > currentIndex) {
            segments.add(MarkdownSegment.Text(input.substring(currentIndex, openIndex)))
        }

        val closeIndex = input.indexOf("```", openIndex + 3)

        if (closeIndex == -1) {
            // Streaming: Unclosed code block
            val rawContent = input.substring(openIndex + 3)
            val firstLineEnd = rawContent.indexOf('\n')
            val language = if (firstLineEnd != -1) rawContent.substring(0, firstLineEnd).trim() else ""
            val code = if (firstLineEnd != -1) rawContent.substring(firstLineEnd + 1) else ""
            segments.add(MarkdownSegment.CodeBlock(code, language))
            break
        } else {
            // Closed code block
            val rawContent = input.substring(openIndex + 3, closeIndex)
            val firstLineEnd = rawContent.indexOf('\n')
            val language = if (firstLineEnd != -1) rawContent.substring(0, firstLineEnd).trim() else ""
            val code = if (firstLineEnd != -1) rawContent.substring(firstLineEnd + 1) else rawContent
            segments.add(MarkdownSegment.CodeBlock(code, language))
            currentIndex = closeIndex + 3
        }
    }
    return segments
}

// --- 6. Formatting Helper ---
fun parseBoldFormatting(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(part)
                pop()
            } else {
                append(part)
            }
        }
    }
}