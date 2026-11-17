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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nbtaad.editor.env.Opt

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)

@Composable
fun App(vm: MainViewModel) {
    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (vm.screen) {
            Screen.DETECT -> DetectScreen(vm)
            Screen.OPTIONS -> OptionsScreen(vm)
        }
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

        TextButton(
            onClick = { vm.manualDialog = true },
            modifier = Modifier.align(Alignment.BottomStart),
        ) { Text("manual IPs", fontSize = 12.sp) }

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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(h * 0.2f))
            Button(onClick = { vm.readOptions() }, enabled = !vm.optBusy) { Text("READ OPTIONS") }
            Text(
                vm.optFeedback,
                fontSize = 12.sp,
                color = if (vm.optError) Red else Color.Unspecified,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(vm.rows, key = { it.key }) { opt ->
                    OptionRow(opt, vm.values[opt.key]) { vm.edit(opt.key, it) }
                    HorizontalDivider()
                }
                if (vm.rows.isNotEmpty()) {
                    item {
                        Button(
                            onClick = { vm.save() },
                            enabled = vm.saveDialog == null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        ) { Text("SAVE") }
                    }
                }
            }
        }
    }

    vm.saveDialog?.let { SaveDialog(vm, it) }
}

@Composable
private fun OptionRow(opt: Opt, value: String?, onChange: (String?) -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val hasPresets = opt.values.isNotEmpty()
    val isCustomValue = value != null && value !in opt.values

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(opt.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(opt.key, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
            }
            Box {
                InfoDot { showInfo = true }
                if (showInfo) InfoBalloon(opt) { showInfo = false }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (!hasPresets) {
            OutlinedTextField(
                value = value ?: "",
                onValueChange = { onChange(it) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                placeholder = { Text("(not set, daemon default)") },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    val label = when {
                        value == null -> "(not set)"
                        value.isBlank() -> "Custom…"
                        else -> value
                    }
                    Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▾")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("(not set)") },
                        onClick = { onChange(null); menu = false },
                    )
                    opt.values.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v) },
                            onClick = { onChange(v); menu = false },
                        )
                    }
                    if (isCustomValue && !opt.custom) {
                        DropdownMenuItem(text = { Text(value ?: "") }, onClick = { menu = false })
                    }
                    if (opt.custom) {
                        DropdownMenuItem(
                            text = { Text("Custom…") },
                            onClick = { if (value == null || value in opt.values) onChange(""); menu = false },
                        )
                    }
                }
            }
            if (opt.custom && isCustomValue) {
                OutlinedTextField(
                    value = value ?: "",
                    onValueChange = { onChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
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

    AlertDialog(
        onDismissRequest = { if (d.status != SaveStatus.RUNNING) vm.closeSaveDialog() },
        containerColor = if (ok) Color(0xFFE8F5E9) else AlertDialogDefaults.containerColor,
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
