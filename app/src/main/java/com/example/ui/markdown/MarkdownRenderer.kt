package com.example.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocaleManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.foundation.BorderStroke


sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: AnnotatedString) : MarkdownElement()
    data class BulletList(val items: List<AnnotatedString>) : MarkdownElement()
    data class Blockquote(val text: AnnotatedString) : MarkdownElement()
    data class CodeBlock(val text: String) : MarkdownElement()
    data class Image(val altText: String, val url: String) : MarkdownElement()
    data class Table(val headers: List<AnnotatedString>, val rows: List<List<AnnotatedString>>) : MarkdownElement()
}

fun tryProcessTable(lines: List<String>): MarkdownElement.Table? {
    if (lines.size < 2) return null
    val row0Cells = parseTableLine(lines[0])
    val row1Cells = parseTableLine(lines[1])
    if (row0Cells.isEmpty() || row1Cells.isEmpty()) return null
    if (!isSeparatorRow(row1Cells)) return null

    val headers = row0Cells.map { parseInlineStyles(it) }
    val rows = mutableListOf<List<AnnotatedString>>()

    for (i in 2 until lines.size) {
        val rowCells = parseTableLine(lines[i])
        if (rowCells.isNotEmpty()) {
            val annotatedCells = rowCells.map { parseInlineStyles(it) }
            rows.add(annotatedCells)
        }
    }

    return MarkdownElement.Table(headers, rows)
}

fun parseTableLine(line: String): List<String> {
    val temp = line.split("|").map { it.trim() }
    val startIdx = if (temp.firstOrNull() == "") 1 else 0
    val endIdx = if (temp.lastOrNull() == "" && temp.size > 1) temp.size - 1 else temp.size
    if (startIdx >= endIdx) return emptyList()
    return temp.subList(startIdx, endIdx)
}

fun isSeparatorRow(cells: List<String>): Boolean {
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        cell.all { char -> char == '-' || char == ':' || char == ' ' } && cell.any { it == '-' }
    }
}

/**
 * Parses raw markdown into structured elements, scanning for inline styling and [[double links]]
 */
fun parseMarkdown(content: String): List<MarkdownElement> {
    val lines = content.lines()
    val elements = mutableListOf<MarkdownElement>()
    var inCodeBlock = false
    var codeBlockBuilder = StringBuilder()
    var currentListItems = mutableListOf<AnnotatedString>()
    var potentialTableLines = mutableListOf<String>()

    val flushList = {
        if (currentListItems.isNotEmpty()) {
            elements.add(MarkdownElement.BulletList(currentListItems.toList()))
            currentListItems.clear()
        }
    }

    val flushTable = {
        if (potentialTableLines.isNotEmpty()) {
            val processedTable = tryProcessTable(potentialTableLines)
            if (processedTable != null) {
                elements.add(processedTable)
            } else {
                for (ln in potentialTableLines) {
                    val trimmedLn = ln.trim()
                    if (trimmedLn.isNotEmpty()) {
                        elements.add(MarkdownElement.Paragraph(parseInlineStyles(trimmedLn)))
                    }
                }
            }
            potentialTableLines.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()

        // 1. Code Block starts or ends
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                elements.add(MarkdownElement.CodeBlock(codeBlockBuilder.toString().trim()))
                codeBlockBuilder = StringBuilder()
                inCodeBlock = false
            } else {
                flushList()
                flushTable()
                inCodeBlock = true
            }
            continue
        }

        if (inCodeBlock) {
            codeBlockBuilder.append(line).append("\n")
            continue
        }

        // Table detection
        if (trimmed.contains("|")) {
            flushList()
            potentialTableLines.add(line)
            continue
        } else {
            flushTable()
        }

        // 2. Headings
        if (trimmed.startsWith("#")) {
            flushList()
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..4) {
                val headerText = trimmed.drop(level).trim()
                elements.add(MarkdownElement.Header(level, headerText))
                continue
            }
        }

        // 3. Blockquotes
        if (trimmed.startsWith(">")) {
            flushList()
            val text = trimmed.drop(1).trim()
            elements.add(MarkdownElement.Blockquote(parseInlineStyles(text)))
            continue
        }

        // 4. Bullet lists
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
            val itemText = trimmed.drop(2).trim()
            currentListItems.add(parseInlineStyles(itemText))
            continue
        } else {
            // End of consecutive bullet list
            if (trimmed.isNotEmpty() && !trimmed.startsWith("* ") && !trimmed.startsWith("- ")) {
                flushList()
            }
        }

        // 5. Images
        val imageRegex = "!\\[(.*?)\\]\\((.*?)\\)".toRegex()
        val imageMatch = imageRegex.find(trimmed)
        if (imageMatch != null) {
            flushList()
            val alt = imageMatch.groupValues[1]
            val url = imageMatch.groupValues[2]
            elements.add(MarkdownElement.Image(alt, url))
            continue
        }

        // 6. Generic Paragraph
        if (trimmed.isNotEmpty()) {
            elements.add(MarkdownElement.Paragraph(parseInlineStyles(trimmed)))
        } else {
            flushList()
        }
    }
    flushList()
    flushTable()
    return elements
}

/**
 * Helper to parse inline styles: **bold**, *italics*, and [[bidirectional obsidian links]]
 */
fun parseInlineStyles(text: String): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        // Find links and match them
        val doubleLinkRegex = "\\[\\[([^\\]\\|]+)(?:\\|([^\\]]+))?\\]\\]".toRegex()
        val matches = doubleLinkRegex.findAll(text).toList()

        var lastIdx = 0
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            // Append standard text before the link
            if (start > lastIdx) {
                appendStyles(text.substring(lastIdx, start))
            }

            // Append the link
            val linkTarget = match.groupValues[1].trim()
            val linkDisplay = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].trim() else linkTarget

            val linkStart = this.length
            append(linkDisplay)
            addStringAnnotation(
                tag = "DOUBLE_LINK",
                annotation = linkTarget,
                start = linkStart,
                end = linkStart + linkDisplay.length
            )
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF1F108E), // Deep Indigo theme link color
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                ),
                start = linkStart,
                end = linkStart + linkDisplay.length
            )

            lastIdx = end
        }

        if (lastIdx < text.length) {
            appendStyles(text.substring(lastIdx, text.length))
        }
    }
}

private fun AnnotatedString.Builder.appendStyles(text: String) {
    // Basic Markdown inline bold ** and italic * styles
    val boldRegex = "\\*\\*(.*?)\\*\\*".toRegex()
    var lastIdx = 0
    val matches = boldRegex.findAll(text).toList()

    for (match in matches) {
        val start = match.range.first
        val end = match.range.last + 1

        if (start > lastIdx) {
            appendItalics(text.substring(lastIdx, start))
        }

        // Bold text
        val boldContent = match.groupValues[1]
        val styleStart = this.length
        appendItalics(boldContent)
        addStyle(
            style = SpanStyle(fontWeight = FontWeight.Bold),
            start = styleStart,
            end = this.length
        )
        lastIdx = end
    }

    if (lastIdx < text.length) {
        appendItalics(text.substring(lastIdx, text.length))
    }
}

private fun AnnotatedString.Builder.appendItalics(text: String) {
    val italicRegex = "\\*(.*?)\\*".toRegex()
    var lastIdx = 0
    val matches = italicRegex.findAll(text).toList()

    for (match in matches) {
        val start = match.range.first
        val end = match.range.last + 1

        if (start > lastIdx) {
            append(text.substring(lastIdx, start))
        }

        // Italic text
        val italicContent = match.groupValues[1]
        val styleStart = this.length
        append(italicContent)
        addStyle(
            style = SpanStyle(fontStyle = FontStyle.Italic),
            start = styleStart,
            end = this.length
        )
        lastIdx = end
    }

    if (lastIdx < text.length) {
        append(text.substring(lastIdx, text.length))
    }
}

@Composable
fun MarkdownViewer(
    content: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    elements: List<MarkdownElement> = remember(content) { parseMarkdown(content) },
    language: AppLanguage = AppLanguage.CHINESE,
    onTableClick: ((MarkdownElement.Table) -> Unit)? = null
) {
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(elements) { index, element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val style = when (element.level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = Color(0xFF0B1C30)
                        )
                        2 -> MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            color = Color(0xFF0B1C30)
                        )
                        else -> MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF464553)
                        )
                    }
                    Text(
                        text = element.text,
                        style = style,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is MarkdownElement.Paragraph -> {
                    ClickableText(
                        text = element.text,
                        style = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFF464553)
                        ),
                        onClick = { offset ->
                            element.text.getStringAnnotations("DOUBLE_LINK", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    onLinkClick(annotation.item)
                                }
                        }
                    )
                }

                is MarkdownElement.BulletList -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        element.items.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = Color(0xFF1F108E)
                                )
                                ClickableText(
                                    text = item,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = Color(0xFF464553)
                                    ),
                                    onClick = { offset ->
                                        item.getStringAnnotations("DOUBLE_LINK", offset, offset)
                                            .firstOrNull()?.let { annotation ->
                                                onLinkClick(annotation.item)
                                            }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownElement.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF4FF))
                            .border(width = 1.dp, color = Color(0xFFC8C4D5), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(IntrinsicSize.Max)
                                .background(Color(0xFF1F108E))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ClickableText(
                            text = element.text,
                            style = LocalTextStyle.current.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF1F108E)
                            ),
                            onClick = { offset ->
                                element.text.getStringAnnotations("DOUBLE_LINK", offset, offset)
                                    .firstOrNull()?.let { annotation ->
                                        onLinkClick(annotation.item)
                                    }
                            }
                        )
                    }
                }

                is MarkdownElement.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF4FF))
                            .border(width = 1.dp, color = Color(0xFFC8C4D5), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = element.text,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF351000)
                        )
                    }
                }

                is MarkdownElement.Image -> {
                    AsyncImage(
                        model = element.url,
                        contentDescription = element.altText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE5EEFF), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                is MarkdownElement.Table -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEFF4FF))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == AppLanguage.CHINESE) "双语卡片表格" else "Data Table Cards",
                                color = Color(0xFF1F108E),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1F108E))
                                    .clickable { onTableClick?.invoke(element) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = LocaleManager.getString("immersive_table_view", language),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Row(modifier = Modifier.background(Color(0xFFEFF4FF))) {
                                element.headers.forEach { header ->
                                    Box(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .border(0.5.dp, Color(0xFFC8C4D5))
                                            .padding(12.dp)
                                    ) {
                                        ClickableText(
                                            text = header,
                                            style = TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0B1C30),
                                                fontSize = 14.sp
                                            ),
                                            onClick = { offset ->
                                                header.getStringAnnotations("DOUBLE_LINK", offset, offset)
                                                    .firstOrNull()?.let { annotation ->
                                                        onLinkClick(annotation.item)
                                                    }
                                            }
                                        )
                                    }
                                }
                            }
                            element.rows.forEachIndexed { rowIdx, rowCells ->
                                Row(
                                    modifier = Modifier.background(if (rowIdx % 2 == 0) Color.White else Color(0xFFF9FAFC))
                                ) {
                                    for (colIdx in element.headers.indices) {
                                        val cell = rowCells.getOrNull(colIdx) ?: AnnotatedString("")
                                        Box(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .border(0.5.dp, Color(0xFFC8C4D5))
                                                .padding(12.dp)
                                        ) {
                                            ClickableText(
                                                text = cell,
                                                style = TextStyle(
                                                    color = Color(0xFF464553),
                                                    fontSize = 13.sp
                                                ),
                                                onClick = { offset ->
                                                    cell.getStringAnnotations("DOUBLE_LINK", offset, offset)
                                                        .firstOrNull()?.let { annotation ->
                                                            onLinkClick(annotation.item)
                                                        }
                                                }
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
