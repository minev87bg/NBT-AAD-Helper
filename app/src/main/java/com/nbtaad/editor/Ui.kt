package com.nbtaad.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nbtaad.editor.data.ThemeMode
import com.nbtaad.editor.env.EnvCheck
import com.nbtaad.editor.env.Opt

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)
private val Amber = Color(0xFFC77700)

@Composable
fun App(vm: MainViewModel) {
    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.fillMaxSize()) {
            when (vm.screen) {
                Screen.DETECT -> DetectScreen(vm)
                Screen.OPTIONS -> OptionsScreen(vm)
            }
            ThemeToggle(vm, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

/** Small corner control cycling Auto (follows the phone) -> Light -> Dark -> Auto. */
@Composable
private fun ThemeToggle(vm: MainViewModel, modifier: Modifier) {
    val label = when (vm.themeMode) {
        ThemeMode.SYSTEM -> "Auto"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
    TextButton(onClick = { vm.cycleTheme() }, modifier = modifier) {
        Text(label, fontSize = 12.sp)
    }
}

// =========================================================================
// Screen 1: DETECT CAR
// =========================================================================

@Composable
private fun DetectScreen(vm: MainViewModel) {
    val st = vm.detect
    val canNext = st.carFound && st.hu != null && st.ssh

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val h = maxHeight

        // button + feedback sit in the middle of the screen
        Button(
            onClick = { vm.onDetect() },
            enabled = !vm.busy,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = h * 0.5f - 20.dp),
        ) { Text("DETECT CAR") }

        Text(
            vm.feedback,
            fontSize = 12.sp,
            color = if (vm.feedbackError) Red else Color.Unspecified,
            textAlign = TextAlign.Center,
            maxLines = 4,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = h * 0.5f + 32.dp).fillMaxWidth(),
        )

        // information table at about 3/4 of the screen
        if (st.carFound) {
            InfoTable(
                st, vm,
                Modifier.align(Alignment.TopCenter).offset(y = h * 0.64f).fillMaxWidth(0.92f),
            )
        }

        // is the list of settings up to date? (needs internet once; afterwards it is kept on the phone)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(0.92f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                vm.syncMessage,
                fontSize = 12.sp,
                color = if (vm.syncOk) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (vm.syncOk) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
            UpdateBanner(vm)
            if ((!vm.syncBusy && !vm.syncOk) || vm.updatePhase == UpdatePhase.FAILED) {
                TextButton(onClick = { vm.checkAgain() }) { Text("check again", fontSize = 12.sp) }
            }
        }

        TextButton(
            onClick = { vm.manualDialog = true },
            modifier = Modifier.align(Alignment.BottomStart),
        ) { Text("manual IPs", fontSize = 12.sp) }

        Text(
            "v${vm.appVersion}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 16.dp),
        )

        if (canNext) {
            Button(
                onClick = { vm.onNext() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) { Text("NEXT") }
        }
    }

    if (vm.confirmReset) {
        AlertDialog(
            onDismissRequest = { vm.confirmReset = false },
            title = { Text("Reset head unit?") },
            text = {
                Text(
                    "Sends a reset command through the car gateway (no SSH needed). The head unit restarts, " +
                        "which takes a minute or two. The app keeps checking until SSH opens."
                )
            },
            confirmButton = { TextButton(onClick = { vm.resetHeadUnit() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { vm.confirmReset = false }) { Text("Cancel") } },
        )
    }

    if (vm.askDraft) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Unfinished session found") },
            text = {
                Text(
                    "There is an edited nbt-aad.env for this car (${st.vin ?: "unknown VIN"}) that was never " +
                        "uploaded. Use that file?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.answerDraft(true) }) { Text("Use it") } },
            dismissButton = { TextButton(onClick = { vm.answerDraft(false) }) { Text("Discard") } },
        )
    }

    if (vm.manualDialog) ManualDialog(vm)
}

/** Is there a newer version of this app on GitHub? One tap downloads it and hands it to Android's installer. */
@Composable
private fun UpdateBanner(vm: MainViewModel) {
    val offer = vm.updateOffer
    val installNote = vm.installMessage
    val msg = installNote ?: vm.updateMessage
    if (msg.isBlank() && offer == null) return
    val busy = vm.updatePhase == UpdatePhase.DOWNLOADING
    val color = when {
        installNote != null || vm.updatePhase == UpdatePhase.FAILED -> Amber
        vm.updatePhase == UpdatePhase.AVAILABLE || busy -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Spacer(Modifier.height(6.dp))
    Text(
        msg,
        fontSize = 12.sp,
        color = color,
        fontWeight = if (offer != null) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
    if (busy) {
        LinearProgressIndicator(
            progress = { vm.updateProgress },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
    if (offer != null && !busy) {
        if (offer.notes.isNotBlank()) {
            Text(
                offer.notes,
                fontSize = 11.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Button(onClick = { vm.startUpdate() }, modifier = Modifier.padding(top = 4.dp)) { Text("UPDATE") }
    }
}

@Composable
private fun InfoTable(st: DetectState, vm: MainViewModel, modifier: Modifier) {
    Column(modifier.border(1.dp, Color.Gray, RoundedCornerShape(6.dp)).padding(12.dp)) {
        InfoRow("ZGW", st.zgw ?: "not found")
        InfoRow("Head unit", st.hu ?: "not found")
        InfoRow("VIN", st.vin ?: "unknown")
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SSH Open", fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (!st.ssh && st.zgw != null && st.vin != null) {
                OutlinedButton(
                    onClick = { vm.confirmReset = true },
                    enabled = !vm.resetting && !vm.busy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) { Text(if (vm.resetting) "Resetting…" else "Reset head unit", fontSize = 12.sp) }
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.size(16.dp).background(if (st.ssh) Green else Red, CircleShape))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ManualDialog(vm: MainViewModel) {
    var zgw by remember { mutableStateOf(vm.detect.zgw ?: "") }
    var hu by remember { mutableStateOf(vm.detect.hu ?: "") }
    AlertDialog(
        onDismissRequest = { vm.manualDialog = false },
        title = { Text("Set addresses manually") },
        text = {
            Column {
                Text("Use this if detection fails. The gateway can stay empty.", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = zgw, onValueChange = { zgw = it }, singleLine = true,
                    label = { Text("ZGW address") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hu, onValueChange = { hu = it }, singleLine = true,
                    label = { Text("Head unit address") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.setManual(zgw, hu) }) { Text("Check") } },
        dismissButton = { TextButton(onClick = { vm.manualDialog = false }) { Text("Cancel") } },
    )
}

// =========================================================================
// Screen 2: options
// =========================================================================

@Composable
private fun OptionsScreen(vm: MainViewModel) {
    BackHandler { vm.backToDetect() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val visible = vm.visibleRows()
        val changeCount = vm.changes.size
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "__top") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(h * 0.2f))
                        Button(onClick = { vm.onReadClick() }, enabled = !vm.optBusy) { Text("READ OPTIONS") }
                        Text(
                            vm.optFeedback,
                            fontSize = 12.sp,
                            color = if (vm.optError) Red else Color.Unspecified,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        val used = vm.settingCount
                        Text(
                            "Settings switched on: $used of ${EnvCheck.MAX_SETTINGS} the daemon can use",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (used > EnvCheck.MAX_SETTINGS) Red else Color.Unspecified,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vm.search,
                            onValueChange = { vm.search = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            placeholder = { Text("Search settings") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = vm.onlyActive,
                                onClick = { vm.onlyActive = !vm.onlyActive },
                                label = { Text("Applied or changed only", fontSize = 12.sp) },
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${visible.size} of ${vm.rows.size}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "__none") {
                        Text("No setting matches.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
                    }
                }
                var lastGroup: String? = null
                for (opt in visible) {
                    if (opt.group != lastGroup) {
                        lastGroup = opt.group
                        val g = opt.group
                        item(key = "__group_$g") {
                            Text(
                                "$g (${visible.count { it.group == g }})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = opt.key) {
                        OptionRow(
                            opt, vm.values[opt.key], vm.isApplied(opt.key), vm.isChanged(opt.key), vm.orig[opt.key],
                            { vm.undo(opt.key) },
                        ) { vm.edit(opt.key, it) }
                        HorizontalDivider()
                    }
                }
            }
            Button(
                onClick = { vm.requestSave() },
                enabled = vm.fileKnown && vm.saveDialog == null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = if (vm.fileKnown) 8.dp else 2.dp),
            ) { Text(if (changeCount > 0) "SAVE ($changeCount change${if (changeCount == 1) "" else "s"})" else "SAVE") }
            if (!vm.fileKnown) {
                Text(
                    "SAVE unlocks after READ OPTIONS, so the settings already on the head unit are not overwritten.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }

    if (vm.confirmRead) {
        val n = vm.changes.size
        AlertDialog(
            onDismissRequest = { vm.confirmRead = false },
            title = { Text("Unsaved changes") },
            text = {
                Text(
                    "$n setting${if (n == 1) " is" else "s are"} changed and not saved. Reading the file from the " +
                        "head unit again will throw those changes away."
                )
            },
            confirmButton = { TextButton(onClick = { vm.readDiscardingChanges() }) { Text("Read anyway") } },
            dismissButton = { TextButton(onClick = { vm.confirmRead = false }) { Text("Keep editing") } },
        )
    }

    vm.review?.let { ReviewDialog(vm, it) }

    vm.saveDialog?.let { SaveDialog(vm, it) }
}

@Composable
private fun ReviewDialog(vm: MainViewModel, r: ReviewState) {
    val blocked = r.errors.isNotEmpty()
    AlertDialog(
        onDismissRequest = { vm.review = null },
        title = {
            Text(
                when {
                    blocked -> "This file cannot be uploaded"
                    r.changes.isEmpty() -> "No changes"
                    else -> "Review changes (${r.changes.size})"
                },
                color = if (blocked) Red else Color.Unspecified,
            )
        },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                r.errors.forEach {
                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Red, modifier = Modifier.padding(vertical = 4.dp))
                }
                r.warnings.forEach {
                    Text(it, fontSize = 12.sp, color = Amber, modifier = Modifier.padding(vertical = 4.dp))
                }
                if (r.changes.isEmpty() && !blocked) {
                    Text(
                        "No setting differs from the file on the car (comments or other lines may still " +
                            "differ). Upload it anyway?",
                        fontSize = 13.sp,
                    )
                }
                r.changes.forEach { c ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(c.key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("was:  ${c.old ?: "(not set)"}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Red)
                        Text("now:  ${c.new ?: "(not set)"}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Green)
                    }
                    HorizontalDivider()
                }
                Text(
                    "Settings switched on after this upload: ${r.settingCount} of ${EnvCheck.MAX_SETTINGS}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            if (!blocked) {
                TextButton(onClick = { vm.confirmSave() }) { Text(if (r.changes.isEmpty()) "Upload anyway" else "Upload") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.review = null }) { Text(if (blocked) "Back" else "Cancel") } },
    )
}

@Composable
private fun OptionRow(
    opt: Opt,
    value: String?,
    applied: Boolean,
    changed: Boolean,
    original: String?,
    onUndo: () -> Unit,
    onChange: (String?) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val hasPresets = opt.values.isNotEmpty()
    val isCustomValue = value != null && value !in opt.values
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(8.dp)

    // red = changed in this session, green = applied on the head unit right now
    val mark = when {
        changed -> Modifier.background(Red.copy(alpha = 0.12f), shape).border(1.5.dp, Red, shape)
        applied -> Modifier.background(Green.copy(alpha = 0.10f), shape).border(1.5.dp, Green, shape)
        else -> Modifier
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(mark).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(opt.key, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                if (opt.summary.isNotBlank()) {
                    Text(opt.summary, fontSize = 12.sp, color = soft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                opt.default?.let {
                    Text("Default: $it", fontSize = 11.sp, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                InfoDot { showInfo = true }
                if (showInfo) InfoBalloon(opt) { showInfo = false }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (!hasPresets) {
            val ph = when {
                opt.hint != null -> "(not set, e.g. ${opt.hint})"
                opt.default != null -> "(not set, default ${opt.default})"
                else -> "(not set)"
            }
            OutlinedTextField(
                value = value ?: "",
                onValueChange = { onChange(it) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                placeholder = { Text(ph, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    val label = when {
                        value == null -> "(not set)"
                        value.isBlank() -> "Custom…"
                        value in opt.values -> opt.choiceText(value)
                        else -> value
                    }
                    Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("▾")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("(not set)") },
                        onClick = { onChange(null); menu = false },
                    )
                    opt.values.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(opt.choiceText(v)) },
                            onClick = { onChange(v); menu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Custom…") },
                        onClick = { if (value == null || value in opt.values) onChange(""); menu = false },
                    )
                }
            }
            if (isCustomValue) {
                OutlinedTextField(
                    value = value ?: "",
                    onValueChange = { onChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp),
                    placeholder = { Text("Type a value") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }

        if (opt.closed && !value.isNullOrBlank() && value !in opt.values) {
            Text(
                "\"$value\" is not one of the documented values (${opt.values.joinToString(", ")}). " +
                    "The daemon may ignore it.",
                fontSize = 12.sp,
                color = Amber,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (changed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Changed. Was: ${original?.takeIf { it.isNotBlank() } ?: "(not set)"}",
                    fontSize = 12.sp,
                    color = Red,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Undo", fontSize = 12.sp)
                }
            }
        } else if (applied) {
            Text(
                "Currently applied on the head unit",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun InfoDot(onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).border(1.dp, Color.Gray, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("i", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun InfoBalloon(opt: Opt, onDismiss: () -> Unit) {
    val offsetY = with(LocalDensity.current) { 30.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.widthIn(max = 280.dp).heightIn(max = 220.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text(opt.key, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(opt.info, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SaveDialog(vm: MainViewModel, d: SaveDialogState) {
    val ok = d.status == SaveStatus.OK
    val scroll = rememberScrollState()
    LaunchedEffect(d.lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successContainer = if (isDark) Color(0xFF1B3B20) else Color(0xFFE8F5E9)

    AlertDialog(
        onDismissRequest = { if (d.status != SaveStatus.RUNNING) vm.closeSaveDialog() },
        containerColor = if (ok) successContainer else AlertDialogDefaults.containerColor,
        modifier = if (ok) Modifier.border(2.dp, Green, RoundedCornerShape(28.dp)) else Modifier,
        title = {
            Text(
                when (d.status) {
                    SaveStatus.RUNNING -> "Saving…"
                    SaveStatus.OK -> "Saved successfully"
                    SaveStatus.FAIL -> "Save failed"
                },
                color = when (d.status) {
                    SaveStatus.OK -> Green
                    SaveStatus.FAIL -> Red
                    SaveStatus.RUNNING -> Color.Unspecified
                },
            )
        },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(scroll)) {
                d.lines.forEach {
                    Text(
                        it.text,
                        fontSize = 13.sp,
                        color = when (it.kind) {
                            LineKind.INFO -> Color.Unspecified
                            LineKind.OK -> Green
                            LineKind.ERROR -> Red
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                if (ok) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Now restart the Android Auto Daemon from the head unit: " +
                            "Media/Radio, Android Auto, Force Stop, then open Android Auto again.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Green,
                    )
                }
                if (d.status == SaveStatus.RUNNING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            when (d.status) {
                SaveStatus.RUNNING -> {}
                SaveStatus.OK -> TextButton(onClick = { vm.closeSaveDialog() }) { Text("OK") }
                SaveStatus.FAIL -> Row {
                    TextButton(onClick = { vm.checkConnection() }) { Text("Check Connection") }
                    if (d.connectionOk) TextButton(onClick = { vm.save() }) { Text("Retry") }
                }
            }
        },
        dismissButton = {
            if (d.status == SaveStatus.FAIL) {
                TextButton(onClick = { vm.closeSaveDialog() }) { Text("Close") }
            }
        },
    )
}
