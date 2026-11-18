package com.nbtaad.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbtaad.editor.install.InstallController
import com.nbtaad.editor.install.Kind
import com.nbtaad.editor.install.Level
import com.nbtaad.editor.install.Names
import com.nbtaad.editor.install.PackageView
import com.nbtaad.editor.install.ReleaseInfo
import com.nbtaad.editor.install.ReleaseLogic
import com.nbtaad.editor.install.RunLine
import com.nbtaad.editor.install.RunKind
import com.nbtaad.editor.install.RunState
import com.nbtaad.editor.install.RunStatus
import com.nbtaad.editor.install.UnitStatus

// =========================================================================
// Page 2: what do you want to do?
// =========================================================================

@Composable
fun HubScreen(vm: MainViewModel) {
    BackHandler { vm.backToDetect() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text("What do you want to do?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            if (vm.detect.hu == null && vm.detect.vin == null) "No car connected: you can still look around"
            else "Car ${vm.detect.vin ?: "(VIN unknown)"}, head unit ${vm.detect.hu ?: "?"}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        HeroTile(
            title = "Install / update",
            subtitle = "NBT Android Auto",
            info = "Install or update the Android Auto daemon and plugin on the head unit, from Sq00ky's Releases page " +
                "or from a file on this phone. The previous version stays on the head unit and one button goes back to it.",
        ) { vm.chooseInstall() }
        Spacer(Modifier.height(12.dp))
        WideTile(
            icon = HubIcon.SETTINGS,
            tint = Color(0xFF5B93F0),
            title = "Change a setting",
            subtitle = "Edit nbt-aad.env",
            info = "Edit nbt-aad.env on the head unit: every setting of the daemon, what it does, and which are applied now.",
        ) { vm.chooseSettings() }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoonTile(
                icon = HubIcon.FLASK,
                tint = Color(0xFFE9A33A),
                title = "Experimental",
                info = "Browse the open issues and pull requests of the project, scan them for files and try new builds. " +
                    "Coming in the next version.",
                modifier = Modifier.weight(1f),
            )
            SoonTile(
                icon = HubIcon.HEART,
                tint = Color(0xFFE56B94),
                title = "Contribute",
                info = "Pick log files from the head unit, pack them into a zip and open the issue page in your browser " +
                    "to send them. Coming in a later version.",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.showLog = true }) { Text("app log", fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp))
    }

    if (vm.askDraft) DraftDialog(vm)
}

private val HeroText = Color(0xFF0C2200)
private val TileShape = RoundedCornerShape(26.dp)

/** The main choice: a big green tile. The (i) in its corner opens what it is for. */
@Composable
private fun HeroTile(title: String, subtitle: String, info: String, onClick: () -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(196.dp).clip(TileShape)
            .background(Brush.linearGradient(listOf(Color(0xFF84EE00), Color(0xFF4FB400))))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { HubIconView(HubIcon.INSTALL, HeroText, 34.dp) }
            Column {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HeroText)
                Text(subtitle, fontSize = 13.sp, color = HeroText.copy(alpha = 0.72f))
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            CompositionLocalProvider(LocalContentColor provides HeroText) { InfoDot { showInfo = true } }
            if (showInfo) InfoBalloon(title, info, mono = false) { showInfo = false }
        }
    }
}

/** A full-width tile: an icon, the name with one short line under it, and the (i) on the right. */
@Composable
private fun WideTile(icon: HubIcon, tint: Color, title: String, subtitle: String, info: String, onClick: () -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    Card(onClick = onClick, shape = TileShape, modifier = Modifier.fillMaxWidth().height(96.dp)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconSquare(icon, tint, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                InfoDot { showInfo = true }
                if (showInfo) InfoBalloon(title, info, mono = false) { showInfo = false }
            }
        }
    }
}

/** A half-width tile that is not ready yet: dimmed, with "soon" where the subtitle would be. The (i) still works. */
@Composable
private fun SoonTile(icon: HubIcon, tint: Color, title: String, info: String, modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }
    Box(
        modifier.height(150.dp).clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).alpha(0.62f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconSquare(icon, tint, 48.dp)
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("SOON", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp) }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            InfoDot { showInfo = true }
            if (showInfo) InfoBalloon(title, info, mono = false) { showInfo = false }
        }
    }
}

@Composable
private fun IconSquare(icon: HubIcon, tint: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.17f)),
        contentAlignment = Alignment.Center,
    ) { HubIconView(icon, tint, size * 0.55f) }
}

/** The icons of page 2, drawn here (24 x 24 grid, round line ends), so no icon library is needed. */
private enum class HubIcon { INSTALL, SETTINGS, FLASK, HEART }

@Composable
private fun HubIconView(icon: HubIcon, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, o(x1, y1), o(x2, y2), strokeWidth = 2f * k, cap = StrokeCap.Round)
        when (icon) {
            HubIcon.INSTALL -> {
                line(12f, 4f, 12f, 14f)
                line(12f, 14f, 8f, 10f)
                line(12f, 14f, 16f, 10f)
                line(5f, 19f, 19f, 19f)
            }
            HubIcon.SETTINGS -> {
                line(4f, 7f, 12f, 7f)
                line(18f, 7f, 20f, 7f)
                line(4f, 17f, 6f, 17f)
                line(12f, 17f, 20f, 17f)
                drawCircle(color, radius = 2.4f * k, center = o(15f, 7f), style = stroke)
                drawCircle(color, radius = 2.4f * k, center = o(9f, 17f), style = stroke)
            }
            HubIcon.FLASK -> {
                line(9.5f, 3f, 14.5f, 3f)
                line(7.8f, 14.5f, 16.2f, 14.5f)
                val path = Path().apply {
                    moveTo(10.5f * k, 3f * k)
                    lineTo(10.5f * k, 9f * k)
                    lineTo(5.3f * k, 17.6f * k)
                    quadraticBezierTo(4.3f * k, 20.5f * k, 7f * k, 20.5f * k)
                    lineTo(17f * k, 20.5f * k)
                    quadraticBezierTo(19.7f * k, 20.5f * k, 18.7f * k, 17.6f * k)
                    lineTo(13.5f * k, 9f * k)
                    lineTo(13.5f * k, 3f * k)
                }
                drawPath(path, color, style = stroke)
            }
            HubIcon.HEART -> {
                val path = Path().apply {
                    moveTo(12f * k, 20.5f * k)
                    cubicTo(5f * k, 15.5f * k, 3.5f * k, 11.5f * k, 3.5f * k, 8.8f * k)
                    cubicTo(3.5f * k, 6.4f * k, 5.4f * k, 4.6f * k, 7.6f * k, 4.6f * k)
                    cubicTo(9.5f * k, 4.6f * k, 11.2f * k, 5.6f * k, 12f * k, 7.4f * k)
                    cubicTo(12.8f * k, 5.6f * k, 14.5f * k, 4.6f * k, 16.4f * k, 4.6f * k)
                    cubicTo(18.6f * k, 4.6f * k, 20.5f * k, 6.4f * k, 20.5f * k, 8.8f * k)
                    cubicTo(20.5f * k, 11.5f * k, 19f * k, 15.5f * k, 12f * k, 20.5f * k)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}

@Composable
private fun DraftDialog(vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Unfinished session found") },
        text = {
            Text(
                "There is an edited nbt-aad.env for this car (${vm.detect.vin ?: "unknown VIN"}) that was never " +
                    "uploaded. Use that file?"
            )
        },
        confirmButton = { TextButton(onClick = { vm.answerDraft(true) }) { Text("Use it") } },
        dismissButton = { TextButton(onClick = { vm.answerDraft(false) }) { Text("Discard") } },
    )
}

// =========================================================================
// Install / update NBT Android Auto
// =========================================================================

@Composable
fun InstallScreen(vm: MainViewModel) {
    val inst = vm.inst
    BackHandler { vm.backToHub() }
    val list = rememberLazyListState()
    LaunchedEffect(inst.view) { if (inst.view != null) list.animateScrollToItem(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        inst.usePhoneFiles(uris)
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = list) {
        item(key = "__head") {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(52.dp))
                Text("Install / update NBT Android Auto", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Install a release or a build. Nothing is thrown away: the version that was on the head unit " +
                        "stays there, and one button goes back to it. Your settings (nbt-aad.env) are not touched.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UnitCard(inst)
            }
        }
        if (inst.prepBusy || inst.prepMessage.isNotEmpty()) {
            item(key = "__prep") { PrepStatus(inst) }
        }
        inst.view?.let { v -> item(key = "__package") { PackageCard(inst, v) } }

        item(key = "__choose") {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Text("Choose what to install", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !inst.working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("A file from this phone…") }
                Text(
                    "Pick a zip, or the loose files (nbt-aad, Releases.zip, libNbtAad.so …). The app looks inside zip " +
                        "files and tells you if it is a build.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                Text("Releases on Sq00ky's page", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (inst.releasesBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                if (inst.releasesMessage.isNotEmpty()) {
                    Text(inst.releasesMessage, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
                if (!inst.releasesBusy && (inst.releases == null || inst.releasesMessage.isNotEmpty())) {
                    TextButton(onClick = { inst.loadReleases() }) { Text("Load the releases again", fontSize = 12.sp) }
                }
            }
        }
        items(inst.releases.orEmpty(), key = { it.tag + it.published }) { r -> ReleaseRow(inst, r) }

        item(key = "__back") {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp)) {
                Text("The way back", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every install keeps the previous nbt-aad and plugin on the head unit. This swaps them back; " +
                        "press it again to go forward again. To get an official version instead, choose it from the " +
                        "releases above.",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { inst.openRevert() },
                    enabled = !inst.working && inst.hasHost && inst.unit?.hasPrevious != false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Go back to the previous version") }
                if (inst.unit?.hasPrevious == false) {
                    Text(
                        "There is no previous version on the head unit yet.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    inst.view?.takeIf { inst.askInstall }?.let { PlanDialog(inst, it) }
    if (inst.askRevert) RevertDialog(inst)
    inst.job?.let { RunDialog(vm, inst, it) }
    inst.diagText?.let { DiagDialog(inst, it) }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun UnitCard(inst: InstallController) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("On the head unit now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val u = inst.unit
            when {
                inst.unitBusy -> {
                    Text("Looking at the head unit…", fontSize = 12.sp)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                inst.unitError -> Text(inst.unitMessage, fontSize = 12.sp, color = Red)
                u != null -> UnitLines(u)
                else -> Text("Not read yet.", fontSize = 12.sp)
            }
            Row {
                TextButton(onClick = { inst.readUnit() }, enabled = !inst.working) { Text("CHECK AGAIN", fontSize = 12.sp) }
                TextButton(onClick = { inst.diagnose() }, enabled = !inst.working && !inst.diagBusy) {
                    Text("TOOLS CHECK", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun UnitLines(u: UnitStatus) {
    val d = u.files[Names.DAEMON]
    Text("Folder: ${u.aaDir}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    Text(
        if (d == null) "Daemon: nbt-aad not found in that folder"
        else "Daemon: nbt-aad" + (if (d.size >= 0) ", ${ReleaseLogic.mb(d.size)}" else "") +
            (u.version?.let { ", $it" } ?: "") + (if (d.executable) "" else ", NOT executable"),
        fontSize = 12.sp,
        color = if (d != null && !d.executable) Red else Color.Unspecified,
    )
    if (u.daemonBroken) {
        Text(
            "Android Auto cannot start with this nbt-aad: the head unit cannot run it (an install that stopped halfway?)." +
                if (u.hasPreviousDaemon) {
                    " The previous daemon is kept as nbt-aad.old: use \"Go back to the previous version\" and tick only the daemon."
                } else {
                    " Install a release to repair it."
                },
            fontSize = 12.sp,
            color = Red,
        )
    }
    Text(
        if (u.pluginDir != null) "Plugin: ${u.pluginFiles.size} file${if (u.pluginFiles.size == 1) "" else "s"} in its folder"
        else "Plugin folder: not found",
        fontSize = 12.sp,
    )
    Text(
        "Previous version kept: daemon ${if (u.hasPreviousDaemon) "yes" else "no"}, plugin ${if (u.hasOldPlugin) "yes" else "no"}",
        fontSize = 12.sp,
    )
}

@Composable
private fun PrepStatus(inst: InstallController) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (inst.prepMessage.isNotEmpty()) {
            Text(inst.prepMessage, fontSize = 12.sp, color = if (inst.prepError) Red else Color.Unspecified)
        }
        if (inst.prepBusy) {
            val f = inst.prepFraction
            if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
private fun ReleaseRow(inst: InstallController, r: ReleaseInfo) {
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.tag, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    if (r.prerelease) {
                        Text("  pre-release", fontSize = 11.sp, color = Amber, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "${ReleaseLogic.day(r.published)}, ${ReleaseLogic.mb(ReleaseLogic.downloadSize(r))}" +
                        (if (r.title.isNotBlank() && r.title != r.tag) ", ${r.title}" else ""),
                    fontSize = 12.sp,
                    maxLines = 2,
                )
                Text(
                    ReleaseLogic.wanted(r).joinToString(", ") { it.name },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { inst.useRelease(r) }, enabled = !inst.working) { Text("USE") }
        }
    }
}

@Composable
private fun PackageCard(inst: InstallController, v: PackageView) {
    val pkg = v.pkg
    val verdictColor = when (pkg.kind) {
        Kind.BUILD -> Green
        Kind.NOTHING -> Red
        else -> Amber
    }
    val verdict = when (pkg.kind) {
        Kind.BUILD -> "This is a build: ${pkg.percent}% of the files of an NBT-AAD build are here."
        Kind.DAEMON_ONLY -> "Only the daemon (nbt-aad): a test build of the daemon."
        Kind.PLUGIN_ONLY -> "Only plugin files (${pkg.pluginFiles.size} of ${Names.PLUGIN.size})."
        Kind.SOME_FILES -> "Some files of a build (${pkg.percent}%)."
        Kind.NOTHING -> "No file of an NBT-AAD build was found."
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Chosen: ${v.title}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (v.fromProject) {
                Text(
                    "From Sq00ky's Releases page" + if (v.prerelease) " (pre-release: a test build)" else "",
                    fontSize = 12.sp,
                    color = if (v.prerelease) Amber else Green,
                )
            } else {
                Text("From a file on this phone: nobody has checked it.", fontSize = 12.sp, color = Amber)
            }
            Spacer(Modifier.height(6.dp))
            Text(verdict, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = verdictColor)
            pkg.version?.let { Text("Version inside the daemon: $it", fontSize = 12.sp) }
            Spacer(Modifier.height(6.dp))
            for (n in Names.ALL) {
                val p = pkg.payloads[n]
                Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (p != null) Green else Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(n, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(176.dp))
                    Text(
                        when {
                            p == null -> "not included"
                            n == Names.FIREWALL -> "${ReleaseLogic.mb(p.size.toLong())}, not installed"
                            else -> ReleaseLogic.mb(p.size.toLong())
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            pkg.payloads[Names.DAEMON]?.let {
                Text(
                    "nbt-aad sha256 ${it.sha256.take(16)}…",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            for (m in pkg.problems) Text(m, fontSize = 12.sp, color = Red, modifier = Modifier.padding(top = 4.dp))
            for (m in pkg.warnings) Text(m, fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
            for (m in pkg.inspection.notes.take(4)) {
                Text(m, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { inst.askInstall = true },
                    enabled = pkg.installable && !inst.working && inst.hasHost,
                ) { Text("INSTALL ON THE HEAD UNIT") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { inst.clearChoice() }, enabled = !inst.working) { Text("Clear") }
            }
            if (!inst.hasHost) {
                Text(
                    "Needs a connected head unit: press DETECT CAR on the first screen.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun CheckLine(checked: Boolean, onChange: (Boolean) -> Unit, text: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(
            text,
            fontSize = 13.sp,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PlanDialog(inst: InstallController, v: PackageView) {
    var trusted by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val pkg = v.pkg
    val ready = v.fromProject || trusted
    val replaced = listOfNotNull(
        pkg.payloads[Names.DAEMON]?.let { "nbt-aad (${ReleaseLogic.mb(it.size.toLong())})" },
        if (pkg.pluginFiles.isEmpty()) null
        else if (pkg.fullPlugin) "the plugin"
        else "${pkg.pluginFiles.size} plugin file${if (pkg.pluginFiles.size == 1) "" else "s"}",
    ).joinToString(" and ")
    // what does not change the decision is behind the (i)
    val details = buildString {
        append("The version on the head unit now is kept (nbt-aad.old")
        if (pkg.pluginFiles.isNotEmpty()) append(", and the plugin folder as NbtAad.old")
        append("), so one button goes back to it. Your nbt-aad.env is not touched.")
        if (pkg.pluginFiles.isNotEmpty() && !pkg.fullPlugin) {
            append("\n\nThese plugin files are replaced: ${pkg.pluginFiles.joinToString(", ")}. The other plugin files stay as they are.")
        }
        if (Names.FIREWALL in pkg.payloads) {
            append("\n\npf_aa is not installed: the firewall rules are built into nbt-aad and are set up once from the iDrive menu.")
        }
        append("\n\nThe app stops Android Auto if it still runs, uploads the files under temporary names, checks that they ")
        append("arrived complete, and only then renames them.")
    }
    AlertDialog(
        onDismissRequest = { inst.askInstall = false },
        title = { Text("Install on the head unit?") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    (if (v.fromProject) "From Sq00ky's Releases page: " else "From your phone: ") + v.title +
                        (pkg.version?.let { " ($it)" } ?: ""),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Replaces $replaced.", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Box {
                        InfoDot { showInfo = true }
                        if (showInfo) InfoBalloon("What happens", details, mono = false) { showInfo = false }
                    }
                }
                if (inst.unit?.hasPrevious == true) {
                    Text(
                        "The older version kept on the head unit is replaced, so the app can go back only one step.",
                        fontSize = 12.sp,
                        color = Amber,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (!v.fromProject) {
                    Text(
                        "This file is not from Sq00ky's Releases page. The app can check that it is a program for the " +
                            "head unit, but not that it is safe.",
                        fontSize = 12.sp,
                        color = Red,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    CheckLine(trusted, { trusted = it }, "I know where this file comes from and I trust it.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { inst.install() }, enabled = ready) { Text("Install") } },
        dismissButton = { TextButton(onClick = { inst.askInstall = false }) { Text("Cancel") } },
    )
}

@Composable
private fun RevertDialog(inst: InstallController) {
    var showInfo by remember { mutableStateOf(false) }
    val u = inst.unit
    val canD = u?.hasPreviousDaemon ?: true
    val canP = u?.hasOldPlugin ?: true
    val chosen = (inst.revertDaemon && canD) || (inst.revertPlugin && canP)
    AlertDialog(
        onDismissRequest = { inst.askRevert = false },
        title = { Text("Go back to the previous version?") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Swap with the previous version that an install kept on the head unit:",
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        InfoDot { showInfo = true }
                        if (showInfo) {
                            InfoBalloon(
                                "What happens",
                                "Doing it again goes forward again. Your nbt-aad.env is not touched. The app stops " +
                                    "Android Auto if it still runs.",
                                mono = false,
                            ) { showInfo = false }
                        }
                    }
                }
                if (u?.daemonBroken == true) {
                    Text(
                        "The nbt-aad on the head unit now cannot be run: swapping the daemon back repairs Android Auto.",
                        fontSize = 12.sp,
                        color = Red,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                CheckLine(
                    inst.revertDaemon && canD, { inst.revertDaemon = it },
                    "The daemon: nbt-aad is swapped with nbt-aad.old" + if (canD) "" else " (there is no nbt-aad.old)",
                    enabled = canD,
                )
                CheckLine(
                    inst.revertPlugin && canP, { inst.revertPlugin = it },
                    "The plugin: the plugin folder is swapped with NbtAad.old" + if (canP) "" else " (there is no NbtAad.old)",
                    enabled = canP,
                )
            }
        },
        confirmButton = { TextButton(onClick = { inst.revert() }, enabled = chosen) { Text("Go back") } },
        dismissButton = { TextButton(onClick = { inst.askRevert = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DiagDialog(inst: InstallController, text: String) {
    val clip = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { if (!inst.diagBusy) inst.diagText = null },
        title = { Text("What the head unit's SSH login has") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (inst.diagBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { inst.diagText = null }, enabled = !inst.diagBusy) { Text("Close") } },
        dismissButton = { TextButton(onClick = { clip.setText(AnnotatedString(text)) }) { Text("Copy") } },
    )
}

/**
 * "Restarting the head unit is recommended" goes with a button that does it: the same reset as on the first screen, and
 * its progress below. Nothing is shown when the app does not know the car's gateway (then there is nothing to send to).
 */
@Composable
internal fun ResetButton(vm: MainViewModel, modifier: Modifier = Modifier) {
    val note = vm.resetNote
    val canReset = vm.detect.zgw != null && vm.detect.vin != null
    val failed = note != null && vm.feedbackError && !vm.resetting
    if (note != null) {
        Column(modifier.fillMaxWidth()) {
            Text(note, fontSize = 12.sp, color = if (failed) Red else Color.Unspecified)
            if (vm.resetting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            if (failed && canReset) {
                OutlinedButton(
                    onClick = { vm.resetHeadUnit(fromWindow = true) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Try again") }
            }
        }
    } else if (canReset) {
        OutlinedButton(
            onClick = { vm.resetHeadUnit(fromWindow = true) },
            enabled = !vm.resetting && !vm.busy,
            modifier = modifier.fillMaxWidth(),
        ) { Text("Reset HeadUnit") }
    }
}

/** The log as a small console: dark, monospace, scrolled to the newest line. */
@Composable
private fun LogConsole(lines: List<RunLine>, maxHeight: Dp) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .background(Color(0xFF0E120E), RoundedCornerShape(6.dp))
            .verticalScroll(scroll)
            .padding(8.dp),
    ) {
        lines.forEach {
            Text(
                it.text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = when (it.level) {
                    Level.INFO -> Color(0xFFD0D4D0)
                    Level.OK -> Color(0xFF7CD68A)
                    Level.WARN -> Color(0xFFFFB74D)
                    Level.ERROR -> Color(0xFFFF8A80)
                },
            )
        }
    }
}

@Composable
private fun RunDialog(vm: MainViewModel, inst: InstallController, d: RunState) {
    val ok = d.status == RunStatus.OK
    var showLog by remember { mutableStateOf(false) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val bodyMax = screenH * 0.7f - 150.dp          // the whole window stays within 70% of the screen
    val logMax = (bodyMax - 170.dp).coerceAtLeast(120.dp)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successContainer = if (isDark) Color(0xFF1B3B20) else Color(0xFFE8F5E9)
    val what = if (d.kind == RunKind.INSTALL) "Install" else "Going back"
    val close = {
        inst.closeRun(readAgain = !vm.resetting)
        vm.resetNote = null
    }

    AlertDialog(
        onDismissRequest = { if (d.status != RunStatus.RUNNING) close() },
        containerColor = if (ok) successContainer else AlertDialogDefaults.containerColor,
        modifier = if (ok) Modifier.border(2.dp, Green, RoundedCornerShape(28.dp)) else Modifier,
        title = {
            Text(
                when (d.status) {
                    RunStatus.RUNNING -> "$what…"
                    RunStatus.OK -> "$what done"
                    RunStatus.FAIL -> "$what failed"
                },
                color = when (d.status) {
                    RunStatus.OK -> Green
                    RunStatus.FAIL -> Red
                    RunStatus.RUNNING -> Color.Unspecified
                },
            )
        },
        text = {
            Column(Modifier.heightIn(max = bodyMax).verticalScroll(rememberScrollState())) {
                when (d.status) {
                    RunStatus.RUNNING -> {
                        Text(d.lines.lastOrNull()?.text ?: "Starting…", fontSize = 13.sp)
                        inst.liveLabel?.let { Text("Uploading $it…", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        val f = inst.liveFraction
                        if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                    RunStatus.OK -> {
                        Text(d.headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Green)
                        for (m in d.notes) Text(m, fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            if (d.pluginChanged) "Restart the head unit to load the new plugin." else "Restarting the head unit is recommended.",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    RunStatus.FAIL -> {
                        Text(d.headline, fontSize = 13.sp, color = Red)
                        if (d.kind == RunKind.INSTALL) {
                            Text(
                                "If a file was already replaced, \"Go back to the previous version\" undoes it.",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showLog = !showLog },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) { Text(if (showLog) "Hide log" else "Show log", fontSize = 12.sp) }
                if (showLog) LogConsole(d.lines, logMax)
                if (ok) ResetButton(vm, Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            if (d.status != RunStatus.RUNNING) TextButton(onClick = { close() }) { Text("OK") }
        },
    )
}
