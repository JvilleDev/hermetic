package com.hermetic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val blocks = parseMarkdown(markdown, isDark)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(
                                1.dp,
                                if (isDark) Color(0xFF2D2D30) else Color(0xFFE5E5EA),
                                RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isDark) Color(0xFF18181C) else Color(0xFFF2F2F7),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        // Header bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDark) Color(0xFF24242B) else Color(0xFFE5E5EA),
                                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = block.language?.uppercase() ?: "CODE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val context = androidx.compose.ui.platform.LocalContext.current
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Copied Code", block.code)
                                    clipboard.setPrimaryClip(clip)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    text = "Copiar",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Code area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            val highlighted = highlightCode(block.code, block.language, isDark)
                            Text(
                                text = highlighted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)
                            )
                        }
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.annotatedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = color
                    )
                }
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            else -> 17.sp
                        },
                        fontWeight = FontWeight.Bold,
                        lineHeight = when (block.level) {
                            1 -> 28.sp
                            2 -> 24.sp
                            else -> 22.sp
                        },
                        color = color
                    )
                }
                is MarkdownBlock.Table -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Draw Headers
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    block.headers.forEach { header ->
                                        Text(
                                            text = header,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.widthIn(min = 100.dp)
                                        )
                                    }
                                }
                                
                                // Draw Rows
                                block.rows.forEachIndexed { index, row ->
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        thickness = 1.dp
                                    )
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                if (index % 2 == 0) MaterialTheme.colorScheme.surface 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.widthIn(min = 100.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Paragraph(val annotatedText: AnnotatedString) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String? = null) : MarkdownBlock()
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Table(val headers: List<AnnotatedString>, val rows: List<List<AnnotatedString>>) : MarkdownBlock()
}

fun parseMarkdown(text: String, isDark: Boolean = false): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    var inCodeBlock = false
    val codeContent = StringBuilder()
    var codeLanguage: String? = null

    var currentTextBuilder = AnnotatedString.Builder()
    var hasCurrentText = false

    fun flushCurrentText() {
        if (hasCurrentText) {
            blocks.add(MarkdownBlock.Paragraph(currentTextBuilder.toAnnotatedString()))
            currentTextBuilder = AnnotatedString.Builder()
            hasCurrentText = false
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                flushCurrentText()
                blocks.add(MarkdownBlock.CodeBlock(codeContent.toString().trimEnd(), codeLanguage))
                codeContent.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLanguage = trimmed.removePrefix("```").trim().ifEmpty { null }
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeContent.append(line).append("\n")
            i++
            continue
        }

        // Check for table divider separator to recognize a table
        if (trimmed.startsWith("|") && i + 1 < lines.size) {
            val nextLine = lines[i + 1].trim()
            val isTableDivider = nextLine.startsWith("|") && nextLine.replace(Regex("[|:\\-\\s]"), "").isEmpty()
            if (isTableDivider) {
                flushCurrentText()
                
                // Parse headers
                val headers = line.split("|")
                    .map { it.trim() }
                    .filterIndexed { index, _ -> index > 0 && index < line.split("|").lastIndex }
                    .map { parseInlineMarkdown(it, isDark) }

                // Parse rows
                val rows = mutableListOf<List<AnnotatedString>>()
                i += 2 // skip header and divider lines
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val rowLine = lines[i].trim()
                    val cells = rowLine.split("|")
                        .map { it.trim() }
                        .filterIndexed { index, _ -> index > 0 && index < rowLine.split("|").lastIndex }
                        .map { parseInlineMarkdown(it, isDark) }
                    rows.add(cells)
                    i++
                }
                blocks.add(MarkdownBlock.Table(headers, rows))
                continue
            }
        }

        if (trimmed.startsWith("#")) {
            flushCurrentText()
            val level = trimmed.takeWhile { it == '#' }.length
            val headingText = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Heading(headingText, level))
        } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
            if (hasCurrentText) {
                currentTextBuilder.append("\n")
            }
            val itemText = trimmed.drop(2).trim()
            currentTextBuilder.append("• ")
            currentTextBuilder.append(parseInlineMarkdown(itemText, isDark))
            hasCurrentText = true
        } else if (trimmed.isNotEmpty()) {
            if (hasCurrentText) {
                currentTextBuilder.append("\n\n")
            }
            currentTextBuilder.append(parseInlineMarkdown(line, isDark))
            hasCurrentText = true
        } else {
            if (hasCurrentText) {
                currentTextBuilder.append("\n")
            }
        }
        i++
    }

    if (inCodeBlock && codeContent.isNotEmpty()) {
        flushCurrentText()
        blocks.add(MarkdownBlock.CodeBlock(codeContent.toString().trimEnd(), codeLanguage))
    }

    flushCurrentText()
    return blocks
}

fun parseInlineMarkdown(text: String, isDark: Boolean = false): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("***", i) || text.startsWith("___", i) -> {
                    val end = findClosingToken(text, i + 3, if (text.startsWith("***", i)) "***" else "___")
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(parseInlineMarkdown(text.substring(i + 3, end), isDark))
                        }
                        i = end + 3
                    } else {
                        append(text[i].toString())
                        i++
                    }
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val end = findClosingToken(text, i + 2, if (text.startsWith("**", i)) "**" else "__")
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(parseInlineMarkdown(text.substring(i + 2, end), isDark))
                        }
                        i = end + 2
                    } else {
                        append(text[i].toString())
                        i++
                    }
                }
                text.startsWith("*", i) || text.startsWith("_", i) -> {
                    val end = findClosingToken(text, i + 1, if (text.startsWith("*", i)) "*" else "_")
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(parseInlineMarkdown(text.substring(i + 1, end), isDark))
                        }
                        i = end + 1
                    } else {
                        append(text[i].toString())
                        i++
                    }
                }
                text.startsWith("`", i) -> {
                    val end = findClosingToken(text, i + 1, "`")
                    if (end != -1) {
                        val codeContent = text.substring(i + 1, end)
                        if (codeContent.isNotEmpty()) {
                            withStyle(SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                                background = if (isDark) Color(0xFF2D2D30) else Color(0xFFE5E5EA),
                            )) {
                                append(codeContent)
                            }
                        }
                        i = end + 1
                    } else {
                        append(text[i].toString())
                        i++
                    }
                }
                else -> {
                    append(text[i].toString())
                    i++
                }
            }
        }
    }
}

private fun findClosingToken(text: String, startIdx: Int, token: String): Int {
    var i = startIdx
    while (i <= text.length - token.length) {
        if (text.startsWith(token, i)) {
            return i
        }
        i++
    }
    return -1
}

fun highlightCode(code: String, language: String?, isDark: Boolean): AnnotatedString {
    val keywordColor = if (isDark) Color(0xFFFF79C6) else Color(0xFFD73A49)
    val stringColor = if (isDark) Color(0xFFF1FA8C) else Color(0xFF032F62)
    val commentColor = if (isDark) Color(0xFF6272A4) else Color(0xFF6A737D)
    val numberColor = if (isDark) Color(0xFFBD93F9) else Color(0xFF005CC5)
    val tagColor = if (isDark) Color(0xFF8BE9FD) else Color(0xFF22863A)

    val builder = AnnotatedString.Builder(code)
    val lang = language?.lowercase() ?: ""

    val commentRegex = if (lang == "python" || lang == "bash" || lang == "yaml" || lang == "dockerfile") {
        Regex("#.*")
    } else {
        Regex("//.*|/\\*[\\s\\S]*?\\*/")
    }

    val stringRegex = Regex("\".*?\"|'.*?'")
    val numberRegex = Regex("\\b\\d+\\b")

    val keywords = when (lang) {
        "kotlin", "java", "android" -> listOf("val", "var", "fun", "class", "interface", "package", "import", "return", "if", "else", "when", "for", "while", "private", "public", "protected", "override", "internal", "object", "null", "true", "false", "this", "super")
        "javascript", "typescript", "js", "ts", "json" -> listOf("const", "let", "var", "function", "class", "return", "if", "else", "for", "while", "import", "export", "from", "default", "null", "true", "false", "this", "new", "try", "catch", "async", "await")
        "python" -> listOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "in", "is", "not", "and", "or", "try", "except", "lambda", "None", "True", "False", "self")
        "html", "xml" -> listOf("doctype", "html", "head", "title", "body", "div", "span", "p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "img", "button", "input", "script", "style")
        else -> listOf("if", "else", "for", "while", "return", "class", "import", "null", "true", "false")
    }

    numberRegex.findAll(code).forEach { match ->
        builder.addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
    }

    if (lang == "html" || lang == "xml") {
        val tagPattern = Regex("<[^>]+>")
        tagPattern.findAll(code).forEach { match ->
            builder.addStyle(SpanStyle(color = tagColor), match.range.first, match.range.last + 1)
        }
    } else {
        keywords.forEach { keyword ->
            val wordRegex = Regex("\\b$keyword\\b")
            wordRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }
        }
    }

    stringRegex.findAll(code).forEach { match ->
        builder.addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
    }

    commentRegex.findAll(code).forEach { match ->
        builder.addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
    }

    return builder.toAnnotatedString()
}
