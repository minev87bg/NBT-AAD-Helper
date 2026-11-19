package com.nbtaad.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbtaad.editor.experimental.CodeColors
import com.nbtaad.editor.experimental.FileLink
import com.nbtaad.editor.experimental.Links
import com.nbtaad.editor.experimental.Markdown
import com.nbtaad.editor.experimental.MdAlign
import com.nbtaad.editor.experimental.MdBlock
import com.nbtaad.editor.experimental.MdSpan
import com.nbtaad.editor.experimental.Tok

/** The colours of GitHub's pages, light and dark (the dark ones are read from GitHub's own dark theme). */
internal class MdColors(
    val link: Color,
    val muted: Color,
    val border: Color,
    val codeBlock: Color,
    val inlineCode: Color,
    val stripe: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
)

@Composable
private fun mdColors(): MdColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        MdColors(
            link = Color(0xFF4493F8), muted = Color(0xFF9198A1), border = Color(0xFF3D444D),
            codeBlock = Color(0xFF151B23), inlineCode = Color(0xFF262C36), stripe = Color(0xFF151B23),
            keyword = Color(0xFFFF7B72), string = Color(0xFFA5D6FF), number = Color(0xFF79C0FF), comment = Color(0xFF9198A1),
        )
    } else {
        MdColors(
            link = Color(0xFF0969DA), muted = Color(0xFF59636E), border = Color(0xFFD1D9E0),
            codeBlock = Color(0xFFF6F8FA), inlineCode = Color(0xFFE6EAEF), stripe = Color(0xFFF6F8FA),
            keyword = Color(0xFFCF222E), string = Color(0xFF0A3069), number = Color(0xFF0550AE), comment = Color(0xFF59636E),
        )
    }
}

/** What a post shows under a line that links a file: the buttons that fit the file. Null where there is nothing to show. */
private val LocalFileActions = compositionLocalOf<(@Composable (FileLink) -> Unit)?> { null }

/**
 * Text written in Markdown, drawn the way GitHub draws it: headings, lists, tables, code, quotes, links.
 * With [fileActions] every line that links a file gets room around it and the actions for that file under it.
 */
@Composable
fun MarkdownView(
    blocks: List<MdBlock>,
    modifier: Modifier = Modifier,
    size: TextUnit = 13.sp,
    fileActions: (@Composable (FileLink) -> Unit)? = null,
) {
    val c = mdColors()
    CompositionLocalProvider(LocalFileActions provides fileActions) {
        MdBlocks(blocks, c, size, 0, modifier)
    }
}

@Composable
private fun MdBlocks(blocks: List<MdBlock>, c: MdColors, size: TextUnit, level: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) MdBlockView(b, c, size, level)
    }
}

private fun styled(spans: List<MdSpan>, c: MdColors, size: TextUnit): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        if (s.picture) continue   // pictures are shown in the grid under the post
        val st = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = if (s.strike) TextDecoration.LineThrough else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            fontSize = if (s.code) size * 0.92f else TextUnit.Unspecified,
            background = if (s.code) c.inlineCode else Color.Unspecified,
        )
        val url = s.link
        if (url != null) {
            val link = LinkAnnotation.Url(
                url,
                TextLinkStyles(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)),
            )
            withLink(link) { withStyle(st) { append(s.text) } }
        } else {
            withStyle(st) { append(s.text) }
        }
    }
}

@Composable
private fun MdBlockView(b: MdBlock, c: MdColors, size: TextUnit, level: Int) {
    when (b) {
        is MdBlock.Para -> if (b.spans.any { !it.picture && it.text.isNotBlank() }) {
            val actions = LocalFileActions.current
            val hasFile = actions != null && b.spans.any { s -> !s.picture && s.link?.let { Links.file(it, "", "") } != null }
            if (actions != null && hasFile) {
                FileParagraph(b, c, size, actions)
            } else {
                Text(styled(b.spans, c, size), fontSize = size, lineHeight = size * 1.5f)
            }
        }

        is MdBlock.Heading -> {
            val px = when (b.level) {
                1 -> size.value + 9f
                2 -> size.value + 6f
                3 -> size.value + 3.5f
                4 -> size.value + 2f
                else -> size.value + 1f
            }
            Column {
                Text(
                    styled(b.spans, c, px.sp),
                    fontSize = px.sp,
                    lineHeight = (px * 1.3f).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (b.level <= 2) HorizontalDivider(Modifier.padding(top = 6.dp), color = c.border)
            }
        }

        is MdBlock.Code -> {
            val runs = remember(b) { CodeColors.runs(b.text, b.lang) }
            val text = buildAnnotatedString {
                for (r in runs) {
                    val color = when (r.tok) {
                        Tok.KEYWORD -> c.keyword
                        Tok.STRING -> c.string
                        Tok.NUMBER -> c.number
                        Tok.COMMENT -> c.comment
                        Tok.PLAIN -> Color.Unspecified
                    }
                    withStyle(SpanStyle(color = color)) { append(r.text) }
                }
            }
            val codeSize = (size.value - 1f).sp
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.codeBlock)) {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = codeSize,
                    lineHeight = codeSize * 1.45f,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }

        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.border))
            Spacer(Modifier.width(10.dp))
            CompositionLocalProvider(LocalContentColor provides c.muted) {
                MdBlocks(b.blocks, c, size, level + 1, Modifier.weight(1f))
            }
        }

        is MdBlock.Items -> Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEachIndexed { idx, item ->
                Row {
                    val mark = if (b.ordered) {
                        "${b.start + idx}."
                    } else {
                        when (level % 3) {
                            0 -> "•"
                            1 -> "◦"
                            else -> "▪"
                        }
                    }
                    Text(
                        mark,
                        fontSize = size,
                        lineHeight = size * 1.5f,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(if (b.ordered) 28.dp else 18.dp).padding(end = 6.dp),
                    )
                    MdBlocks(item, c, size, level + 1, Modifier.weight(1f))
                }
            }
        }

        is MdBlock.Table -> MdTable(b, c, size)

        MdBlock.Rule -> HorizontalDivider(color = c.border)
    }
}

/** A paragraph with files in it: each line that links a file stands apart, with the file's buttons under it. */
@Composable
private fun FileParagraph(b: MdBlock.Para, c: MdColors, size: TextUnit, actions: @Composable (FileLink) -> Unit) {
    val pieces = remember(b) { Markdown.pieces(b.spans) { Links.file(it, "", "") != null } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in pieces) {
            if (p.files.isEmpty()) {
                if (p.spans.any { !it.picture && it.text.isNotBlank() }) {
                    Text(styled(p.spans, c, size), fontSize = size, lineHeight = size * 1.5f)
                }
            } else {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (p.spans.any { !it.picture && it.text.isNotBlank() }) {
                        Text(styled(p.spans, c, size), fontSize = size, lineHeight = size * 1.5f)
                    }
                    for (url in p.files) {
                        val f = remember(url) { Links.file(url, "", "") }
                        if (f != null) {
                            if (p.files.size > 1) {
                                Text(f.name, fontSize = 11.sp, color = c.muted, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                            }
                            actions(f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, c: MdColors, size: TextUnit) {
    val cols = t.head.size
    // a column is as wide as its longest line, within limits; longer text wraps
    val widths = remember(t) {
        List(cols) { col ->
            var longest = Markdown.plain(t.head[col]).length
            for (r in t.rows) longest = maxOf(longest, Markdown.plain(r[col]).lines().maxOf { it.length })
            (longest * 7 + 28).coerceIn(80, 260)
        }
    }
    Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, c.border)) {
        for (r in -1 until t.rows.size) {
            val cells = if (r < 0) t.head else t.rows[r]
            val rowBg = when {
                r < 0 -> c.codeBlock
                r % 2 == 1 -> c.stripe
                else -> Color.Transparent
            }
            Row(Modifier.height(IntrinsicSize.Min).background(rowBg)) {
                for (col in 0 until cols) {
                    Box(
                        Modifier.width(widths[col].dp).fillMaxHeight().border(0.5.dp, c.border)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            styled(cells[col], c, size),
                            fontSize = size,
                            lineHeight = size * 1.4f,
                            fontWeight = if (r < 0) FontWeight.Bold else null,
                            textAlign = when (t.align.getOrElse(col) { MdAlign.LEFT }) {
                                MdAlign.CENTER -> TextAlign.Center
                                MdAlign.RIGHT -> TextAlign.End
                                MdAlign.LEFT -> TextAlign.Start
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
