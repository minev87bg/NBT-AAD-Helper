package com.nbtaad.editor.contribute

/** A log file found on the head unit. [size] is -1 when the listing did not say. */
class HeadLog(val path: String, val size: Long, val stamp: String = "") {
    val name: String get() = path.substringAfterLast('/')

    /** Where it lives, in words: the RAM logs are gone at the next reboot, which is why the page is worth opening first. */
    val place: String
        get() = when {
            "/dev/shmem" in path -> "in memory, gone after a reboot"
            "/umass" in path -> "on the USB stick"
            else -> ""
        }
}

/**
 * Reads the listing of RemoteScripts.findLogs() (`ls -l` of the daemon's log files, in the places the project's guides
 * name). `ls -l` is the only tool on the head unit that says how big a file is (there is no wc). Pure, so it is tested.
 */
object LogFinder {
    private val MONTHS = setOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * The files of the listing, `nbt-aad.log` first and then the app's. The same file seen by two ways in (as
     * /dev/shmem and as /net/hu-omap/dev/shmem) is listed once.
     */
    fun parse(out: String): List<HeadLog> {
        val seen = HashSet<String>()
        val found = ArrayList<HeadLog>()
        for (raw in out.lines()) {
            val t = raw.trim().split(Regex("\\s+"))
            if (t.size < 2 || !t[0].startsWith("-")) continue          // regular files only: not directories or links
            val path = t.last()
            if (!path.startsWith("/") || !path.endsWith(".log")) continue
            val m = (1 until t.size - 1).firstOrNull { t[it] in MONTHS && t[it - 1].toLongOrNull() != null }
            val size = m?.let { t[it - 1].toLong() } ?: -1L
            val stamp = m?.let { t.subList(it, minOf(it + 3, t.size - 1)).joinToString(" ") } ?: ""
            if (!seen.add("${path.substringAfterLast('/')}|$size|$stamp")) continue
            found += HeadLog(path, size, stamp)
        }
        return found.sortedWith(compareBy({ rank(it.name) }, { it.name }))
    }

    private fun rank(name: String): Int = when (name) {
        "nbt-aad.log" -> 0
        "nbt-aad-app.log" -> 1
        else -> 2
    }

    /** "1.2 MB", "340 KB", "unknown size" */
    fun sizeText(size: Long): String = when {
        size < 0 -> "size unknown"
        size >= 1_000_000 -> "%.1f MB".format(java.util.Locale.US, size / 1_000_000.0)
        size >= 1_000 -> "${(size + 500) / 1_000} KB"
        else -> "$size B"
    }
}
