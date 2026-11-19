package com.nbtaad.editor

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbtaad.editor.experimental.Comment
import com.nbtaad.editor.experimental.ExperimentalController
import com.nbtaad.editor.experimental.FileKind
import com.nbtaad.editor.experimental.FileLink
import com.nbtaad.editor.experimental.Mentions
import com.nbtaad.editor.experimental.PageText
import com.nbtaad.editor.experimental.Topic
import com.nbtaad.editor.install.ReleaseLogic

/** The Experimental page: the project's issues and pull requests, and what people share in them. */
@Composable
fun ExperimentalScreen(vm: MainViewModel) {
    val exp = vm.exp
    val open = exp.selected
    BackHandler { if (open != null) exp.back() else vm.backToHub() }
    if (open == null) TopicList(vm) else TopicDetail(vm, open)
}

private const val LIST_INFO =
    "What people report, test and suggest in Sq00ky's project, read from GitHub without signing in (GitHub allows 60 " +
        "requests an hour that way; the line under the buttons says how many are left).\n\n" +
        "SCAN FOR FILES looks through the first posts and the newest comments for links to files, and marks the pages " +
        "that share some. A shared zip or build file can be taken to the installer from the page.\n\n" +
        "Files shared in a discussion come from other people. The app can check what a file is, not whether it is safe."

@Composable
private fun Pill(text: String, color: Color) {
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color, letterSpacing = 0.5.sp)
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
    OutlinedCard(onClick = { exp.select(t) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                Pill("FILES ${files.size}", Green)
            }
        }
    }
}

private fun openLink(handler: androidx.compose.ui.platform.UriHandler, url: String) {
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
                        Text(if (exp.summary == null) "SUMMARIZE" else "HIDE SUMMARY", fontSize = 12.sp)
                    }
                }
                if (exp.commentsBusy) {
                    Text("Loading the comments…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                if (exp.commentsMessage.isNotEmpty()) Text(exp.commentsMessage, fontSize = 12.sp, color = Red)
                exp.summary?.let { s -> SummaryCard(s.lines, s.links) { openLink(uri, it) } }
                if (files.isNotEmpty()) FilesCard(exp, files) { openLink(uri, it) }
                if (settings.isNotEmpty()) SettingsCard(vm, settings)
                Spacer(Modifier.height(10.dp))
                val body = PageText.clean(t.body)
                Text(
                    if (body.isEmpty()) "(no text)" else body,
                    fontSize = 13.sp,
                    color = if (body.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                if (!comments.isNullOrEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("${comments.size} comment${if (comments.size == 1) "" else "s"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        items(comments ?: emptyList()) { c -> CommentCard(c) }
    }
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
                                "summary, so read the page itself when it matters.",
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
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
                            FileKind.OTHER -> ", not a build"
                            FileKind.NEEDS_LOGIN -> ", GitHub shows it only to people who are signed in"
                        },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (f.installable) {
                        TextButton(onClick = { exp.download(f) }, enabled = !exp.downloadBusy) {
                            Text("GET FOR INSTALL", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { onOpen(f.url) }) { Text("OPEN IN BROWSER", fontSize = 12.sp) }
                }
            }
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
private fun CommentCard(c: Comment) {
    var expanded by remember(c) { mutableStateOf(false) }
    val text = remember(c) { PageText.clean(c.body) }
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${c.author}  ·  ${ReleaseLogic.day(c.created)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (text.isEmpty()) "(no text)" else text,
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (text.length > 400 || text.count { it == '\n' } > 7) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Show less" else "Show more", fontSize = 12.sp)
                }
            }
        }
    }
}
