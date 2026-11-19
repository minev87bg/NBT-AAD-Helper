package com.nbtaad.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbtaad.editor.experimental.Comment
import com.nbtaad.editor.experimental.ExperimentalController
import com.nbtaad.editor.experimental.FileKind
import com.nbtaad.editor.experimental.FileLink
import com.nbtaad.editor.experimental.Mentions
import com.nbtaad.editor.experimental.Markdown
import com.nbtaad.editor.experimental.Topic
import com.nbtaad.editor.install.ReleaseLogic

/** The Experimental page: the project's issues and pull requests, and what people share in them. */
@Composable
fun ExperimentalScreen(vm: MainViewModel) {
    val exp = vm.exp
    val open = exp.selected
    BackHandler { if (open != null) exp.back() else vm.backToHub() }
    ExperimentalTheme { if (open == null) TopicList(vm) else TopicDetail(vm, open) }
}

/** The amber of the EXPERIMENTAL tile's icon on the choice page: the accent of this whole page. */
internal val ExperimentalAmber = Color(0xFFE9A33A)

/**
 * The page's buttons, chips, links and progress bars take the page's amber instead of the app's green, so it is
 * recognised as the page of the amber EXPERIMENTAL tile. On the light theme the text uses a deeper amber, which stays
 * readable on the cream background.
 */
@Composable
private fun ExperimentalTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.background.luminance() < 0.5f
    val scheme = if (dark) {
        base.copy(
            primary = ExperimentalAmber, onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFF3B2B10), onPrimaryContainer = Color(0xFFFFE2A8),
            secondaryContainer = Color(0xFF3B2B10), onSecondaryContainer = Color(0xFFFFE2A8),
            surfaceTint = ExperimentalAmber,
        )
    } else {
        base.copy(
            primary = Color(0xFFA66300), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE7BD), onPrimaryContainer = Color(0xFF4A2E00),
            secondaryContainer = Color(0xFFFFE7BD), onSecondaryContainer = Color(0xFF4A2E00),
            surfaceTint = Color(0xFFA66300),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private const val LIST_INFO =
    "What people report, test and suggest in Sq00ky's project, read from GitHub without signing in (GitHub allows 60 " +
        "requests an hour that way; the line under the buttons says how many are left).\n\n" +
        "SCAN FOR FILES looks through the first posts and the newest comments for links to files, and marks the pages " +
        "that share some. A shared zip or build file can be taken to the installer from the page.\n\n" +
        "Files shared in a discussion come from other people. The app can check what a file is, not whether it is safe."

@Composable
private fun Pill(text: String, color: Color, solid: Boolean = false) {
    Box(
        Modifier.clip(CircleShape).background(if (solid) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (solid) Color(0xFF0D1117) else color,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun TopicList(vm: MainViewModel) {
    val exp = vm.exp
    var showInfo by remember { mutableStateOf(false) }
    val shown = exp.visible()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 24.dp),
    ) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Experimental", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box {
                        InfoDot { showInfo = true }
                        if (showInfo) InfoBalloon("Experimental", LIST_INFO, mono = false) { showInfo = false }
                    }
                }
                Text(
                    "Issues and pull requests of Sq00ky's project",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = exp.tab == ExperimentalController.Tab.ISSUES,
                        onClick = { exp.tab = ExperimentalController.Tab.ISSUES },
                        label = { Text("Issues ${exp.count(ExperimentalController.Tab.ISSUES, exp.showClosed)}", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = exp.tab == ExperimentalController.Tab.PRS,
                        onClick = { exp.tab = ExperimentalController.Tab.PRS },
                        label = { Text("Pull requests ${exp.count(ExperimentalController.Tab.PRS, exp.showClosed)}", fontSize = 12.sp) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !exp.showClosed,
                        onClick = { exp.showClosed = false },
                        label = { Text("Open", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = exp.showClosed,
                        onClick = { exp.showClosed = true },
                        label = { Text("Closed", fontSize = 12.sp) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { exp.scan() },
                        enabled = exp.topics != null && !exp.scanBusy && !exp.loading,
                    ) { Text("SCAN FOR FILES", fontSize = 12.sp) }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    TextButton(onClick = { exp.refresh() }, enabled = !exp.loading && !exp.scanBusy) {
                        Text("REFRESH", fontSize = 12.sp)
                    }
                }
                if (exp.loading || exp.scanBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                if (exp.message.isNotEmpty()) {
                    Text(exp.message, fontSize = 12.sp, color = if (exp.error) Red else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (exp.scanNote.isNotEmpty()) {
                    Text(exp.scanNote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (exp.topics != null && shown.isEmpty()) {
            item(key = "none") {
                Text("Nothing here.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(shown, key = { it.number }) { t -> TopicRow(exp, t) }
    }
}

@Composable
private fun TopicRow(exp: ExperimentalController, t: Topic) {
    val files = exp.scanned[t.number]
    val hasFiles = !files.isNullOrEmpty()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // a page that shares files stands out: a thick outline in the page's amber, a faint amber tint, a solid stamp
    OutlinedCard(
        onClick = { exp.select(t) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (hasFiles) {
            CardDefaults.outlinedCardColors(
                containerColor = ExperimentalAmber.copy(alpha = if (dark) 0.11f else 0.16f).compositeOver(MaterialTheme.colorScheme.surface),
            )
        } else {
            CardDefaults.outlinedCardColors()
        },
        border = if (hasFiles) BorderStroke(2.5.dp, ExperimentalAmber) else CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "#${t.number}  ${t.title}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(t.author)
                        append("  ·  ${t.comments} comment${if (t.comments == 1) "" else "s"}")
                        append("  ·  ${ReleaseLogic.day(t.updated)}")
                        if (t.merged) append("  ·  merged") else if (t.draft) append("  ·  draft")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!files.isNullOrEmpty()) {
                Spacer(Modifier.padding(horizontal = 4.dp))
                Pill("FILES ${files.size}", ExperimentalAmber, solid = true)
            }
        }
    }
}

internal fun openLink(handler: androidx.compose.ui.platform.UriHandler, url: String) {
    try {
        handler.openUri(url)
    } catch (_: Exception) {
        // no browser to open it
    }
}

@Composable
private fun TopicDetail(vm: MainViewModel, t: Topic) {
    val exp = vm.exp
    val uri = LocalUriHandler.current
    val comments = exp.comments
    val files = remember(t, comments) { com.nbtaad.editor.experimental.Digest.filesOf(t, comments ?: emptyList()) }
    val settings = remember(t, comments) {
        Mentions.settings(listOf(t.body) + (comments ?: emptyList()).map { it.body })
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 24.dp),
    ) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { exp.back() }) { Text("‹  Back to the list", fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (t.isPr) "PULL REQUEST" else "ISSUE", MaterialTheme.colorScheme.primary)
                    Pill(
                        when {
                            t.merged -> "MERGED"
                            t.open -> if (t.draft) "DRAFT" else "OPEN"
                            else -> "CLOSED"
                        },
                        if (t.open) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (l in t.labels.take(3)) Pill(l.uppercase(), Amber)
                }
                Spacer(Modifier.height(6.dp))
                Text("#${t.number}  ${t.title}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${t.author}  ·  opened ${ReleaseLogic.day(t.created)}  ·  updated ${ReleaseLogic.day(t.updated)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { openLink(uri, t.url) }) { Text("OPEN ON GITHUB", fontSize = 12.sp) }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(onClick = { exp.toggleSummary() }, enabled = !exp.commentsBusy) {
                        Text(if (exp.summary == null) "QUICK SUMMARY" else "HIDE SUMMARY", fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { exp.summarizeWithAi() }, enabled = !exp.aiBusy && !exp.commentsBusy) {
                        Text(
                            when {
                                exp.aiBusy -> "WRITING…"
                                exp.aiText != null -> "SUMMARIZE AGAIN"
                                else -> "SUMMARIZE WITH AI"
                            },
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    TextButton(onClick = { exp.openKeyDialog() }) {
                        Text(if (exp.aiKeySet) "AI key" else "Set AI key", fontSize = 12.sp)
                    }
                }
                if (exp.aiBusy) {
                    Text("Claude is reading the page…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                if (exp.aiMessage.isNotEmpty()) Text(exp.aiMessage, fontSize = 12.sp, color = Red)
                exp.aiText?.let { AiCard(it) { exp.hideAi() } }
                if (exp.commentsBusy) {
                    Text("Loading the comments…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                if (exp.commentsMessage.isNotEmpty()) Text(exp.commentsMessage, fontSize = 12.sp, color = Red)
                exp.summary?.let { s -> SummaryCard(s.lines, s.links) { openLink(uri, it) } }
                if (files.isNotEmpty()) FilesCard(exp, files) { openLink(uri, it) }
                if (settings.isNotEmpty()) SettingsCard(vm, settings)
                Spacer(Modifier.height(10.dp))
                val body = remember(t.body) { Markdown.parse(t.body) }
                if (body.isEmpty()) {
                    Text("(no text)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MarkdownView(body)
                }
                val bodyPics = remember(body) { Markdown.pictures(body) }
                if (bodyPics.isNotEmpty()) {
                    PictureGrid(bodyPics, Modifier.padding(top = 10.dp)) { i ->
                        exp.openGallery("First post  ·  ${t.author}", bodyPics, i)
                    }
                }
                if (!comments.isNullOrEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("${comments.size} comment${if (comments.size == 1) "" else "s"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        items(comments ?: emptyList()) { c -> CommentCard(exp, c) }
    }
    if (exp.aiKeyDialog) AiKeyDialog(exp)
    exp.textDoc?.let { doc -> TextFileDialog(doc) { exp.closeText() } }
    exp.gallery?.let { g -> GalleryDialog(g) { exp.closeGallery() } }
}

@Composable
private fun AiCard(text: String, onHide: () -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    val blocks = remember(text) { Markdown.parse(text) }
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI summary", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    InfoDot { showInfo = true }
                    if (showInfo) {
                        InfoBalloon(
                            "AI summary",
                            "Written by Claude (${com.nbtaad.editor.experimental.AiLogic.MODEL}, Anthropic) from the text of this " +
                                "page: the first post and the comments. It can make mistakes or leave things out, so read the page " +
                                "itself when it matters. The text of the page was sent to Anthropic with your key.",
                            mono = false,
                        ) { showInfo = false }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            MarkdownView(blocks)
            Text(
                "Written by Claude from the text of this page. It can make mistakes.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onHide, contentPadding = PaddingValues(0.dp)) { Text("Hide", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AiKeyDialog(exp: ExperimentalController) {
    var text by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { exp.closeKeyDialog() },
        title = { Text("AI summary") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "SUMMARIZE WITH AI sends the text of the open page (title, first post and comments, all public on " +
                        "GitHub) to Claude, an AI model from Anthropic, which writes a short summary. That needs your own " +
                        "Anthropic API key.",
                    fontSize = 13.sp,
                )
                Text(
                    "To get one: sign in at console.anthropic.com, add a little credit and create a key under API keys. " +
                        "Anthropic bills each summary to your account; a page usually costs about a cent or less, and the " +
                        "console shows your usage.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = { openLink(uri, "https://console.anthropic.com/settings/keys") },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Open console.anthropic.com", fontSize = 12.sp) }
                Text(
                    "The key stays on this phone, in the app's private storage, and goes only to api.anthropic.com. " +
                        "It is never written to the app log.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (exp.aiKeySet) "New key (a key is saved)" else "API key (sk-ant-…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                if (exp.aiKeyNote.isNotEmpty()) Text(exp.aiKeyNote, fontSize = 12.sp, color = Red, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { exp.saveKey(text) }) { Text("SAVE KEY") } },
        dismissButton = {
            Row {
                if (exp.aiKeySet) TextButton(onClick = { exp.removeKey() }) { Text("REMOVE KEY") }
                TextButton(onClick = { exp.closeKeyDialog() }) { Text("CANCEL") }
            }
        },
    )
}

@Composable
private fun SummaryCard(lines: List<String>, links: List<String>, onLink: (String) -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("In short", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    InfoDot { showInfo = true }
                    if (showInfo) {
                        InfoBalloon(
                            "In short",
                            "A digest made on the phone from the words of the page: who wrote it, what it says first, " +
                                "the latest comment, the settings and files it names, and its links. It is not an AI " +
                                "summary (for that, use SUMMARIZE WITH AI), so read the page itself when it matters.",
                            mono = false,
                        ) { showInfo = false }
                    }
                }
            }
            for (l in lines) Text(l, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (links.isNotEmpty()) {
                Text("Links", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                for (u in links) {
                    TextButton(onClick = { onLink(u) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                        Text(
                            u.removePrefix("https://"),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesCard(exp: ExperimentalController, files: List<FileLink>, onOpen: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp), border = BorderStroke(2.dp, ExperimentalAmber)) {
        Column(Modifier.padding(12.dp)) {
            Text("Files shared on this page", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "They come from other people. The app can check what a file is, not whether it is safe.",
                fontSize = 11.sp,
                color = Amber,
            )
            for (f in files) {
                Spacer(Modifier.height(6.dp))
                Text(f.name, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${f.by.ifBlank { "someone" }}, ${f.where}" +
                        when (f.kind) {
                            FileKind.BUILD -> ""
                            FileKind.OTHER -> if (f.isText) ", opens in a window, nothing is saved on the phone" else ", not a build"
                            FileKind.NEEDS_LOGIN -> ", GitHub shows it only to people who are signed in"
                        },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (f.isText) {
                        TextButton(onClick = { exp.viewText(f) }, enabled = !exp.textBusy) {
                            Text(if (exp.textBusy) "OPENING…" else "VIEW", fontSize = 12.sp)
                        }
                    }
                    if (f.installable) {
                        TextButton(onClick = { exp.download(f) }, enabled = !exp.downloadBusy) {
                            Text("GET FOR INSTALL", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { onOpen(f.url) }) { Text("OPEN IN BROWSER", fontSize = 12.sp) }
                }
            }
            if (exp.textBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            if (exp.textMessage.isNotEmpty()) Text(exp.textMessage, fontSize = 12.sp, color = Red)
            if (exp.downloadBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            if (exp.downloadMessage.isNotEmpty()) {
                Text(exp.downloadMessage, fontSize = 12.sp, color = if (exp.downloadError) Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(vm: MainViewModel, settings: List<String>) {
    val exp = vm.exp
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Settings mentioned", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            for (k in settings.take(12)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    if (exp.isKnown(k)) {
                        Text("in your list", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = { vm.addSettingFromPage(k) }) { Text("ADD", fontSize = 12.sp) }
                    }
                }
            }
            if (exp.settingNote.isNotEmpty()) Text(exp.settingNote, fontSize = 12.sp, color = Green)
        }
    }
}

@Composable
private fun CommentCard(exp: ExperimentalController, c: Comment) {
    var expanded by remember(c) { mutableStateOf(false) }
    val full = remember(c) { Markdown.parse(c.body) }
    val short = remember(full) { Markdown.shorten(full) }
    val pics = remember(full) { Markdown.pictures(full) }
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${c.author}  ·  ${ReleaseLogic.day(c.created)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (full.isEmpty()) {
                Text("(no text)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MarkdownView(if (expanded || !short.second) full else short.first)
            }
            if (pics.isNotEmpty()) {
                PictureGrid(pics, Modifier.padding(top = 8.dp)) { i ->
                    exp.openGallery("Comment  ·  ${c.author}  ·  ${ReleaseLogic.day(c.created)}", pics, i)
                }
            }
            if (short.second) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Show less" else "Show more", fontSize = 12.sp)
                }
            }
        }
    }
}
