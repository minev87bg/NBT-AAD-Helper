package com.nbtaad.editor.contribute

import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One file in the zip. */
class PackEntry(val name: String, val bytes: ByteArray, val note: String = "")

/** What the zip says about itself (about.txt): who made it, for which page, what was hidden. */
class PackInfo(
    val app: String,
    val phone: String,
    val packed: ZonedDateTime,
    val headUnit: String?,
    val forPage: String?,
    val passwords: Int,
    val vins: Int,
)

/** The zip with the logs. Pure (java.util.zip only), so it is tested. */
object LogPack {
    /** "nbt-aad-logs-20261119-2301.zip" */
    fun zipName(now: ZonedDateTime): String = "nbt-aad-logs-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip"

    /** Two files with the same name (one in memory, one on the stick) must not replace each other: "a.log", "a-2.log". */
    fun uniqueNames(wanted: List<String>): List<String> {
        val used = HashSet<String>()
        return wanted.map { n ->
            var name = n
            var k = 2
            while (!used.add(name.lowercase())) {
                val dot = n.lastIndexOf('.')
                name = if (dot > 0) n.substring(0, dot) + "-$k" + n.substring(dot) else "$n-$k"
                k++
            }
            name
        }
    }

    fun about(info: PackInfo, entries: List<PackEntry>): String = buildString {
        appendLine("NBT AAD Helper: logs for the project")
        appendLine("App: ${info.app}")
        appendLine("Phone: ${info.phone}")
        appendLine("Packed: ${info.packed.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
        info.headUnit?.let { appendLine("NBT-AAD on the head unit: $it") }
        info.forPage?.let { appendLine("For: $it") }
        appendLine(
            "Hidden before packing: ${info.passwords} WiFi password${if (info.passwords == 1) "" else "s"}, " +
                "${info.vins} VIN${if (info.vins == 1) "" else "s"} (VINs are kept as WBA…3076).",
        )
        appendLine()
        appendLine("Files:")
        for (e in entries) appendLine("- ${e.name} (${e.bytes.size} bytes)" + if (e.note.isNotBlank()) ", ${e.note}" else "")
    }

    /** The zip: about.txt first, then the files under [PackEntry.name] (made unique). */
    fun zip(entries: List<PackEntry>, info: PackInfo): ByteArray {
        val names = uniqueNames(entries.map { it.name })
        val named = entries.mapIndexed { i, e -> PackEntry(names[i], e.bytes, e.note) }
        val bos = ByteArrayOutputStream()
        val time = info.packed.toInstant().toEpochMilli()
        ZipOutputStream(bos).use { z ->
            z.setLevel(Deflater.BEST_COMPRESSION)
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name).apply { this.time = time })
                z.write(bytes)
                z.closeEntry()
            }
            put("about.txt", about(info, named).toByteArray(Charsets.UTF_8))
            for (e in named) put(e.name, e.bytes)
        }
        return bos.toByteArray()
    }
}
