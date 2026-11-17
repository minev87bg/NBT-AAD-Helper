package com.nbtaad.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbtaad.editor.data.CatalogStore
import com.nbtaad.editor.data.Storage
import com.nbtaad.editor.data.ThemeMode
import com.nbtaad.editor.data.ThemePrefs
import com.nbtaad.editor.env.ConfigParser
import com.nbtaad.editor.env.EnvCheck
import com.nbtaad.editor.env.EnvDoc
import com.nbtaad.editor.env.Opt
import com.nbtaad.editor.net.CarDetector
import com.nbtaad.editor.net.CatalogSync
import com.nbtaad.editor.net.DetectResult
import com.nbtaad.editor.net.EnvRemote
import com.nbtaad.editor.net.Hsfz
import com.nbtaad.editor.net.NetworkBinder
import com.nbtaad.editor.net.friendlyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { DETECT, OPTIONS }
enum class SaveStatus { RUNNING, OK, FAIL }
enum class LineKind { INFO, OK, ERROR }

data class DetectState(
    val carFound: Boolean = false,
    val zgw: String? = null,
    val hu: String? = null,
    val vin: String? = null,
    val ssh: Boolean = false,
)

data class DialogLine(val text: String, val kind: LineKind = LineKind.INFO)

/** One option whose value differs from what was read from the car. null = not set. */
data class Change(val key: String, val old: String?, val new: String?)

/** What the SAVE preview shows: the changes and the problems the file would have on the head unit. */
data class ReviewState(
    val changes: List<Change>,
    val errors: List<String>,
    val warnings: List<String>,
    val settingCount: Int,
)

data class SaveDialogState(
    val lines: List<DialogLine>,
    val status: SaveStatus,
    val connectionOk: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val storage = Storage(ctx)
    private val themePrefs = ThemePrefs(ctx)

    var screen by mutableStateOf(Screen.DETECT)
    var themeMode by mutableStateOf(themePrefs.read())

    fun cycleTheme() {
        themeMode = when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        themePrefs.write(themeMode)
    }

    // ---------------- screen 1: detect ----------------
    var busy by mutableStateOf(false)
    var resetting by mutableStateOf(false)
    var feedback by mutableStateOf("")
    var feedbackError by mutableStateOf(false)
    var detect by mutableStateOf(DetectState())
    var confirmReset by mutableStateOf(false)
    var askDraft by mutableStateOf(false)
    var manualDialog by mutableStateOf(false)
    private var pollJob: Job? = null

    // ---------------- screen 2: options ----------------
    var optBusy by mutableStateOf(false)
    var optFeedback by mutableStateOf("")
    var optError by mutableStateOf(false)
    var rows by mutableStateOf<List<Opt>>(emptyList())
    val values = mutableStateMapOf<String, String?>()
    var saveDialog by mutableStateOf<SaveDialogState?>(null)
    private var baseDoc: EnvDoc? = null

    /** The values as they were on the car when last read (or last saved); changes are measured against this. */
    var orig by mutableStateOf<Map<String, String?>>(emptyMap())
    var confirmRead by mutableStateOf(false)
    var review by mutableStateOf<ReviewState?>(null)

    /** Narrowing the long list down. */
    var search by mutableStateOf("")
    var onlyActive by mutableStateOf(false)

    /** True once the file on the head unit was read (or a draft loaded): only then is SAVE safe. */
    val fileKnown: Boolean get() = baseDoc != null

    // ---------------- the list of settings (from the project's README) ----------------
    private val catalogStore = CatalogStore(ctx)
    private var catalog: List<Opt> = emptyList()
    private var catalogSource = CatalogStore.Source.BUNDLED
    private var catalogSavedAt: Long? = null
    var syncBusy by mutableStateOf(false)
    var syncMessage by mutableStateOf("")
    var syncOk by mutableStateOf(false)

    init {
        val loaded = catalogStore.load()
        catalog = loaded.options
        catalogSource = loaded.source
        catalogSavedAt = loaded.savedAt
        syncCatalog()
    }

    private fun catalogDescription(): String = when (catalogSource) {
        CatalogStore.Source.BUNDLED -> "built into the app"
        CatalogStore.Source.DOWNLOADED -> {
            val t = catalogSavedAt
            if (t == null) "saved earlier"
            else "saved on " + java.text.DateFormat.getDateInstance().format(java.util.Date(t))
        }
    }

    /** Looks for new settings on GitHub. Needs internet once; afterwards the list is kept on the phone. */
    fun syncCatalog() {
        if (syncBusy) return
        syncBusy = true
        syncOk = false
        syncMessage = "Checking for new settings…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val readme = CatalogSync.fetchReadme(ctx)
                val section = ConfigParser.extractSection(readme)
                val parsed = section?.let { ConfigParser.parse(it) }.orEmpty()
                if (section == null || parsed.size < CatalogStore.MIN_OPTIONS) {
                    syncMessage = "The settings list on GitHub could not be read (its layout may have changed). " +
                        "Using the list ${catalogDescription()}: ${catalog.size} settings."
                } else {
                    val before = catalog.map { it.key }.toSet()
                    catalogStore.saveDownloaded(section)
                    val fresh = parsed.count { it.key !in before }
                    catalog = parsed
                    catalogSource = CatalogStore.Source.DOWNLOADED
                    catalogSavedAt = System.currentTimeMillis()
                    syncOk = true
                    syncMessage = "Available settings are synchronized (${parsed.size} settings" +
                        (if (fresh > 0) ", $fresh new" else "") +
                        "). Internet is no longer needed, you can continue on the car's WiFi."
                }
            } catch (e: Exception) {
                syncMessage = "No internet, so no check for new settings. Using the list ${catalogDescription()} " +
                    "(${catalog.size} settings). Connect to the internet once to update it."
            } finally {
                syncBusy = false
            }
        }
    }

    /** Folder name for this car's files: the VIN when the ZGW told us, otherwise a fallback. */
    private val carId: String
        get() = detect.vin ?: ("unknown-" + (detect.hu ?: detect.zgw ?: "car"))

    // =====================================================================
    // Detect
    // =====================================================================

    fun onDetect() {
        if (busy) return
        pollJob?.cancel()
        resetting = false
        busy = true
        feedbackError = false
        feedback = "Starting…"
        detect = DetectState()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkBinder.bindToLocalNetwork(ctx)
                val r = CarDetector.detect { feedback = it }
                applyResult(r)
            } catch (e: Exception) {
                feedbackError = true
                feedback = "Detection failed: ${friendlyError(e)}"
            } finally {
                busy = false
            }
        }
    }

    private fun applyResult(r: DetectResult) {
        detect = DetectState(r.zgwIp != null, r.zgwIp, r.huIp, r.vin, r.sshOpen)
        feedbackError = false
        feedback = when {
            r.zgwIp == null -> {
                feedbackError = true
                r.problem ?: "No car detected."
            }
            r.huIp != null && r.sshOpen -> "Car found. SSH is open. Press NEXT."
            r.huIp != null -> "Car found, but SSH (port 22) is closed. Use the reset button next to the red dot."
            else -> "Car found, but no head unit with SSH was found. Use the reset button or set the IPs manually."
        }
    }

    fun setManual(zgwText: String, huText: String) {
        manualDialog = false
        pollJob?.cancel()
        resetting = false
        busy = true
        feedbackError = false
        feedback = "Checking the entered addresses…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkBinder.bindToLocalNetwork(ctx)
                val z = zgwText.trim().ifEmpty { null }
                val h = huText.trim().ifEmpty { null }
                val vin = z?.let { Hsfz.readVin(it) }
                val ssh = h?.let { CarDetector.sshOpen(it) } ?: false
                detect = DetectState(z != null || h != null, z, h, vin, ssh)
                feedback = when {
                    h != null && ssh -> "SSH is open on $h. Press NEXT."
                    h != null -> "SSH is closed on $h."
                    else -> "Gateway set. Enter the head unit address too."
                }
            } catch (e: Exception) {
                feedbackError = true
                feedback = "Check failed: ${friendlyError(e)}"
            } finally {
                busy = false
            }
        }
    }

    /** Reset the head unit through the ZGW (no SSH), then keep checking until SSH opens. */
    fun resetHeadUnit() {
        val zgw = detect.zgw ?: return
        if (detect.vin == null) return // only a gateway that gave a VIN is treated as a car
        confirmReset = false
        pollJob?.cancel()
        resetting = true
        feedbackError = false
        feedback = "Sending the reset command to the head unit…"
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkBinder.bindToLocalNetwork(ctx)
                val (ok, msg) = Hsfz.resetHeadUnit(zgw)
                if (!ok) {
                    feedbackError = true
                    feedback = msg
                    return@launch
                }
                detect = detect.copy(ssh = false)
                val end = System.currentTimeMillis() + 240_000
                var waited = 0
                while (isActive && System.currentTimeMillis() < end) {
                    feedback = "$msg Waiting for the head unit to restart… (${waited}s)"
                    delay(5000)
                    waited += 5
                    val (hu, open) = CarDetector.findHeadUnit(CarDetector.localNetworks(), zgw)
                    if (hu != null) detect = detect.copy(hu = hu, ssh = open)
                    if (open) {
                        feedback = "Head unit is back and SSH is open. Press NEXT."
                        return@launch
                    }
                }
                feedbackError = true
                feedback = "SSH did not open within 4 minutes. Check the head unit, then press DETECT CAR."
            } catch (e: Exception) {
                feedbackError = true
                feedback = "Reset failed: ${friendlyError(e)}"
            } finally {
                resetting = false
            }
        }
    }

    // =====================================================================
    // Navigation between the screens
    // =====================================================================

    fun onNext() {
        if (storage.hasDraft(carId)) askDraft = true else openOptions(useDraft = false)
    }

    fun answerDraft(use: Boolean) {
        askDraft = false
        if (!use) storage.clearDraft(carId)
        openOptions(use)
    }

    private fun openOptions(useDraft: Boolean) {
        // every documented setting is listed from the start; READ OPTIONS then marks the ones the car applies
        values.clear()
        orig = emptyMap()
        baseDoc = null
        rows = buildRows(null, null)
        search = ""
        onlyActive = false
        optError = false
        optFeedback = "Press READ OPTIONS to see which settings the head unit applies right now."
        if (useDraft) {
            storage.readDraft(carId)?.let {
                val draft = EnvDoc.parse(it)
                // changes are shown against the file as it was last read from the car, when we still have it
                applyDoc(draft, storage.readFromCar(carId)?.let { f -> EnvDoc.parse(f) } ?: draft)
                optFeedback = "Loaded the unfinished file from the last session. Review it and press SAVE, " +
                    "or press READ OPTIONS to start over from the car."
            }
        }
        screen = Screen.OPTIONS
    }

    fun backToDetect() {
        screen = Screen.DETECT
    }

    // =====================================================================
    // Options: read, edit, save
    // =====================================================================

    private fun norm(v: String?): String? = v?.takeIf { it.isNotBlank() }

    /**
     * The list shows every documented setting, in README order. Settings that are in the file but that the README
     * does not name (a typo, or something newer than the list) follow at the end, so nothing in the file is hidden.
     */
    private fun buildRows(doc: EnvDoc?, baseline: EnvDoc?): List<Opt> {
        val known = catalog.map { it.key }.toSet()
        val extra = LinkedHashSet<String>()
        for (d in listOfNotNull(doc, baseline)) {
            for (k in d.keys()) if (k !in known && d.get(k) != null) extra += k
        }
        val extraRows = extra.map { k ->
            Opt(
                key = k,
                group = ConfigParser.GROUP_FILE_ONLY,
                summary = "Found in your file, but the README's list of settings does not name it.",
                info = "This setting is in your nbt-aad.env but not in the README's list of settings. It may be " +
                    "misspelled, or newer or older than the list. The app keeps it as it is.",
            )
        }
        return catalog + extraRows
    }

    private fun applyDoc(doc: EnvDoc, baseline: EnvDoc = doc) {
        baseDoc = doc
        rows = buildRows(doc, baseline)
        values.clear()
        rows.forEach { values[it.key] = doc.get(it.key) }
        orig = rows.associate { it.key to baseline.get(it.key) }
    }

    /** True when the car's file (as last read or saved) has this setting switched on. */
    fun isApplied(key: String): Boolean = norm(orig[key]) != null

    /** The rows that pass the search box and the "applied or changed only" switch. */
    fun visibleRows(): List<Opt> {
        val q = search.trim()
        return rows.filter { r ->
            (!onlyActive || isApplied(r.key) || norm(values[r.key]) != null || isChanged(r.key)) &&
                (q.isEmpty() || r.key.contains(q, ignoreCase = true) || r.summary.contains(q, ignoreCase = true) ||
                    r.info.contains(q, ignoreCase = true))
        }
    }

    /** How many settings the file would have switched on right now (the daemon only uses 15). */
    val settingCount: Int
        get() = EnvCheck.analyze(renderCurrent() ?: values.entries.joinToString("\n") { (k, v) ->
            if (norm(v) != null) "$k=$v" else ""
        }).count

    fun isChanged(key: String): Boolean = norm(values[key]) != norm(orig[key])

    val changes: List<Change>
        get() = rows.filter { isChanged(it.key) }.map { Change(it.key, norm(orig[it.key]), norm(values[it.key])) }

    fun undo(key: String) = edit(key, orig[key])

    /** READ OPTIONS: asks first when that would throw away edits. */
    fun onReadClick() {
        if (optBusy) return
        if (changes.isNotEmpty()) confirmRead = true else readOptions()
    }

    fun readDiscardingChanges() {
        confirmRead = false
        readOptions()
    }

    /** SAVE: shows what is about to change; the upload starts from the preview. */
    fun requestSave() {
        val text = renderCurrent() ?: return
        val check = EnvCheck.analyze(text)
        review = ReviewState(changes, check.errors, check.warnings, check.count)
    }

    fun confirmSave() {
        val r = review ?: return
        if (r.errors.isNotEmpty()) return
        review = null
        save()
    }

    fun readOptions() {
        val hu = detect.hu ?: return
        if (optBusy) return
        optBusy = true
        optError = false
        optFeedback = "Connecting…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkBinder.bindToLocalNetwork(ctx)
                when (val res = EnvRemote.read(hu) { optFeedback = it }) {
                    is EnvRemote.ReadResult.Found -> {
                        storage.saveFromCar(carId, res.text)
                        storage.clearDraft(carId)
                        applyDoc(EnvDoc.parse(res.text))
                        val applied = rows.count { isApplied(it.key) }
                        optFeedback = "Read ${res.text.length} bytes from ${res.dir}/${EnvRemote.FILE}: " +
                            "$applied setting${if (applied == 1) " is" else "s are"} currently applied (marked green)."
                    }
                    is EnvRemote.ReadResult.NoFile -> {
                        storage.clearDraft(carId)
                        applyDoc(EnvDoc.parse(""))
                        optFeedback = "Found ${res.dir} but no nbt-aad.env in it yet (the file is optional), so every " +
                            "setting runs at its default. SAVE will create the file."
                    }
                    is EnvRemote.ReadResult.NoFolder -> {
                        optError = true
                        optFeedback = "Folder /opt/aa not found on the head unit. Is NBT-AAD installed?\n" +
                            "What the SSH shell can see:\n" + res.detail
                    }
                }
            } catch (e: Exception) {
                optError = true
                optFeedback = "Read failed: ${friendlyError(e)}"
            } finally {
                optBusy = false
            }
        }
    }

    fun edit(key: String, value: String?) {
        values[key] = value
        renderCurrent()?.let { storage.saveDraft(carId, it) } // keep the unfinished session on the phone
    }

    /** The file as it would be uploaded now: original lines kept, only changed keys touched. */
    private fun renderCurrent(): String? {
        val doc = baseDoc?.copyDoc() ?: return null
        for ((k, v) in values) {
            val nv = v?.takeIf { it.isNotBlank() }
            if (doc.get(k) != nv) doc.set(k, nv)
        }
        return doc.render()
    }

    private fun addLine(l: DialogLine) {
        saveDialog = saveDialog?.let { it.copy(lines = it.lines + l) }
    }

    private fun setStatus(s: SaveStatus, connectionOk: Boolean = false) {
        saveDialog = saveDialog?.copy(status = s, connectionOk = connectionOk)
    }

    fun save() {
        val hu = detect.hu ?: return
        val text = renderCurrent() ?: return
        storage.saveDraft(carId, text)
        saveDialog = SaveDialogState(listOf(DialogLine("Preparing ${text.length} bytes…")), SaveStatus.RUNNING)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkBinder.bindToLocalNetwork(ctx)
                EnvRemote.write(hu, text.toByteArray(Charsets.UTF_8)) { addLine(DialogLine(it)) }
                storage.saveUploaded(carId, text)
                storage.clearDraft(carId)
                storage.saveFromCar(carId, text)
                applyDoc(EnvDoc.parse(text)) // what is on the car now becomes the new baseline, nothing is "changed"
                addLine(DialogLine("File uploaded and verified.", LineKind.OK))
                setStatus(SaveStatus.OK)
            } catch (e: Exception) {
                addLine(DialogLine(friendlyError(e), LineKind.ERROR))
                setStatus(SaveStatus.FAIL)
            }
        }
    }

    /** Same detection as the first screen. */
    fun checkConnection() {
        val d = saveDialog ?: return
        if (d.status == SaveStatus.RUNNING) return
        saveDialog = d.copy(
            status = SaveStatus.RUNNING,
            connectionOk = false,
            lines = d.lines + DialogLine("Checking the connection…"),
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkBinder.bindToLocalNetwork(ctx)
                val r = CarDetector.detect { addLine(DialogLine(it)) }
                applyResult(r)
                if (r.zgwIp != null && r.huIp != null && r.sshOpen) {
                    addLine(DialogLine("Connection OK (head unit ${r.huIp}). Press Retry to upload again.", LineKind.OK))
                    setStatus(SaveStatus.FAIL, connectionOk = true)
                } else {
                    addLine(
                        DialogLine(
                            r.problem ?: "SSH on the head unit is still not reachable. " +
                                "Go back to the first screen to reset the head unit.",
                            LineKind.ERROR,
                        )
                    )
                    setStatus(SaveStatus.FAIL)
                }
            } catch (e: Exception) {
                addLine(DialogLine(friendlyError(e), LineKind.ERROR))
                setStatus(SaveStatus.FAIL)
            }
        }
    }

    fun closeSaveDialog() {
        saveDialog = null
    }
}
