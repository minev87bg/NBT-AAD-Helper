package com.nbtaad.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbtaad.editor.install.InstallController
import com.nbtaad.editor.install.Kind
import com.nbtaad.editor.install.Level
import com.nbtaad.editor.install.Names
import com.nbtaad.editor.install.PackageView
import com.nbtaad.editor.install.ReleaseInfo
import com.nbtaad.editor.install.ReleaseLogic
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
            "Car ${vm.detect.vin ?: "(VIN unknown)"}, head unit ${vm.detect.hu ?: "?"}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        HubCard(
            "CHANGE A SETTING",
            "Edit nbt-aad.env on the head unit: every setting of the daemon, what it does, and which are applied now.",
        ) { vm.chooseSettings() }
        HubCard(
            "INSTALL / UPDATE\nNBT ANDROID AUTO",
            "Install or update the Android Auto daemon and plugin on the head unit, from Sq00ky's Releases page or " +
                "from a file on this phone. The previous version stays on the head unit and one button goes back to it.",
            highlight = true,
        ) { vm.chooseInstall() }
        HubCard(
            "EXPERIMENTAL",
            "Browse the open issues and pull requests of the project, scan them for files and try new builds. " +
                "Coming in the next version.",
            enabled = false,
        ) {}
        HubCard(
            "CONTRIBUTE",
            "Pick log files from the head unit, pack them into a zip and open the issue page in your browser to send " +
                "them. Coming in a later version.",
            enabled = false,
        ) {}
        Spacer(Modifier.height(24.dp))
    }

    if (vm.askDraft) DraftDialog(vm)
}

@Composable
private fun HubCard(title: String, text: String, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = if (highlight) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 13.sp, color = if (highlight) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "Install a release or a build, then restart the head unit. Nothing is thrown away: the version " +
                        "that was on the head unit stays there, and one button goes back to it. Your settings " +
                        "(nbt-aad.env) are not touched.",
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
                    enabled = !inst.working && inst.unit?.hasPrevious != false,
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
    inst.job?.let { RunDialog(inst, it) }
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
                    enabled = pkg.installable && !inst.working,
                ) { Text("INSTALL ON THE HEAD UNIT") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { inst.clearChoice() }, enabled = !inst.working) { Text("Clear") }
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
    var stopped by remember { mutableStateOf(false) }
    var trusted by remember { mutableStateOf(false) }
    val pkg = v.pkg
    val ready = stopped && (v.fromProject || trusted)
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
                Spacer(Modifier.height(8.dp))
                pkg.payloads[Names.DAEMON]?.let {
                    Text(
                        "nbt-aad (${ReleaseLogic.mb(it.size.toLong())}) is replaced. The one on the head unit now is kept as nbt-aad.old.",
                        fontSize = 13.sp,
                    )
                }
                if (pkg.pluginFiles.isNotEmpty()) {
                    Text(
                        if (pkg.fullPlugin) {
                            "The plugin folder is replaced by the ${pkg.pluginFiles.size} new files. The current folder is kept as NbtAad.old."
                        } else {
                            "These plugin files are replaced: ${pkg.pluginFiles.joinToString(", ")}. The other plugin files stay " +
                                "as they are. The current folder is kept as NbtAad.old."
                        },
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (Names.FIREWALL in pkg.payloads) {
                    Text(
                        "pf_aa is not installed: the firewall rules are built into nbt-aad and are set up once from the iDrive menu.",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text("Your nbt-aad.env is not touched.", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                if (inst.unit?.hasPrevious == true) {
                    Text(
                        "A previous version is kept on the head unit now. It is replaced by the version that is on it now, " +
                            "so the app can only go back one step.",
                        fontSize = 12.sp,
                        color = Amber,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "The app stops Android Auto if it still runs, uploads each file under a temporary name and checks it " +
                        "arrived intact, and only then renames. Afterwards you restart the head unit.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!v.fromProject) {
                    Text(
                        "This file does not come from Sq00ky's Releases page. The app can check that it is a program for " +
                            "the head unit, but not that it is safe or what it does. A wrong file can stop Android Auto " +
                            "from working. The previous version stays on the head unit, so you can go back.",
                        fontSize = 12.sp,
                        color = Red,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    CheckLine(trusted, { trusted = it }, "I know where this file comes from and I trust it.")
                }
                CheckLine(
                    stopped, { stopped = it },
                    "Android Auto is stopped on the head unit (Media/Radio, Android Auto, Force Stop).",
                )
            }
        },
        confirmButton = { TextButton(onClick = { inst.install() }, enabled = ready) { Text("Install") } },
        dismissButton = { TextButton(onClick = { inst.askInstall = false }) { Text("Cancel") } },
    )
}

@Composable
private fun RevertDialog(inst: InstallController) {
    var stopped by remember { mutableStateOf(false) }
    val u = inst.unit
    val canD = u?.hasPreviousDaemon ?: true
    val canP = u?.hasOldPlugin ?: true
    val chosen = (inst.revertDaemon && canD) || (inst.revertPlugin && canP)
    AlertDialog(
        onDismissRequest = { inst.askRevert = false },
        title = { Text("Go back to the previous version?") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Choose what is swapped with the previous version that an install kept on the head unit. Doing it " +
                        "again goes forward again. Your nbt-aad.env is not touched.",
                    fontSize = 13.sp,
                )
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
                CheckLine(
                    stopped, { stopped = it },
                    "Android Auto is stopped on the head unit (Media/Radio, Android Auto, Force Stop).",
                )
            }
        },
        confirmButton = { TextButton(onClick = { inst.revert() }, enabled = stopped && chosen) { Text("Go back") } },
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

@Composable
private fun RunDialog(inst: InstallController, d: RunState) {
    val ok = d.status == RunStatus.OK
    val scroll = rememberScrollState()
    LaunchedEffect(d.lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successContainer = if (isDark) Color(0xFF1B3B20) else Color(0xFFE8F5E9)
    val what = if (d.kind == RunKind.INSTALL) "Install" else "Going back"

    AlertDialog(
        onDismissRequest = { if (d.status != RunStatus.RUNNING) inst.closeRun() },
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
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(scroll)) {
                d.lines.forEach {
                    Text(
                        it.text,
                        fontSize = 13.sp,
                        color = when (it.level) {
                            Level.INFO -> Color.Unspecified
                            Level.OK -> Green
                            Level.WARN -> Amber
                            Level.ERROR -> Red
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                if (ok) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Now restart the head unit, as Sq00ky's update guide says: hold the power button (the volume knob) " +
                            "for 30 seconds until it reboots. That loads the new plugin and puts the file system back " +
                            "to read only. Then open Media/Radio, Android Auto.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Green,
                    )
                }
                if (d.status == RunStatus.FAIL) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If a file was already replaced, \"Go back to the previous version\" undoes it.",
                        fontSize = 12.sp,
                    )
                }
                if (d.status == RunStatus.RUNNING) {
                    inst.liveLabel?.let { Text("Uploading $it…", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    val f = inst.liveFraction
                    if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            if (d.status != RunStatus.RUNNING) TextButton(onClick = { inst.closeRun() }) { Text("OK") }
        },
    )
}
