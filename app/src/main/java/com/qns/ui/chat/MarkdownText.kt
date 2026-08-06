package com.qns.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color

fun markdownText(value: String): AnnotatedString = buildAnnotatedString {
    val lines = value.replace("\r\n", "\n").split('\n')
    lines.forEachIndexed { index, line ->
        when {
            line.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)) { append(line.removePrefix("### ")) }
            line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.removePrefix("## ")) }
            line.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.removePrefix("# ")) }
            line.startsWith("> ") -> withStyle(SpanStyle(color = Color(0xFF5F6368))) { append("│ "); appendInline(this, line.removePrefix("> ")) }
            else -> appendInline(this, line)
        }
        if (index < lines.lastIndex) append('\n')
    }
}

private fun appendInline(builder: AnnotatedString.Builder, value: String, depth: Int = 0) {
    if (depth > 4) {
        builder.append(value)
        return
    }
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("```", index) -> {
                val end = value.indexOf("```", index + 3)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x16000000))) {
                        append(value.substring(index + 3, end).trim('\n'))
                    }
                    index = end + 3
                } else {
                    builder.append(value[index])
                    index++
                }
            }
            value[index] == '`' -> {
                val end = value.indexOf('`', index + 1)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x16000000))) {
                        append(value.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    builder.append(value[index])
                    index++
                }
            }
            value.startsWith("**", index) || value.startsWith("__", index) -> {
                val marker = value.substring(index, index + 2)
                val end = value.indexOf(marker, index + 2)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(this, value.substring(index + 2, end), depth + 1)
                    }
                    index = end + 2
                } else {
                    builder.append(value[index])
                    index++
                }
            }
            value.startsWith("~~", index) -> {
                val end = value.indexOf("~~", index + 2)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(this, value.substring(index + 2, end), depth + 1)
                    }
                    index = end + 2
                } else {
                    builder.append(value[index])
                    index++
                }
            }
            value[index] == '*' || value[index] == '_' -> {
                val marker = value[index]
                val end = value.indexOf(marker, index + 1)
                if (end > index + 1) {
                    builder.withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        appendInline(this, value.substring(index + 1, end), depth + 1)
                    }
                    index = end + 1
                } else {
                    builder.append(value[index])
                    index++
                }
            }
            value[index] == '[' -> {
                val labelEnd = value.indexOf(']', index + 1)
                val urlStart = if (labelEnd >= 0 && labelEnd + 1 < value.length && value[labelEnd + 1] == '(') labelEnd + 2 else -1
                val urlEnd = if (urlStart >= 0) value.indexOf(')', urlStart) else -1
                if (labelEnd >= 0 && urlStart >= 0 && urlEnd > urlStart) {
                    builder.withStyle(SpanStyle(color = Color(0xFF006874), textDecoration = TextDecoration.Underline)) {
                        append(value.substring(index + 1, labelEnd))
                    }
                    index = urlEnd + 1
                } else {
                    builder.append(value[index])
                    index++
                }
            }
            else -> {
                builder.append(value[index])
                index++
            }
        }
    }
}
