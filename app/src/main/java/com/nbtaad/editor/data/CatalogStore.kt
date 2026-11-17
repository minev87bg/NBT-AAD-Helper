package com.nbtaad.editor.data

import android.content.Context
import com.nbtaad.editor.env.ConfigParser
import com.nbtaad.editor.env.Opt
import java.io.File

/**
 * Where the list of settings comes from. Three copies, newest first:
 *  1. the one downloaded from GitHub on a previous start (files/catalog/config-section.md),
 *  2. the one built into the app (assets/nbt-aad-config.md, the README's Configuration section as of the build).
 * So the list is always there, with or without internet.
 */
class CatalogStore(private val ctx: Context) {
    enum class Source { DOWNLOADED, BUNDLED }

    class Loaded(val options: List<Opt>, val source: Source, val savedAt: Long?)

    private val dir = File(ctx.filesDir, "catalog")
    private val cache = File(dir, "config-section.md")

    fun load(): Loaded {
        if (cache.exists()) {
            try {
                val parsed = ConfigParser.parse(cache.readText())
                if (parsed.size >= MIN_OPTIONS) return Loaded(parsed, Source.DOWNLOADED, cache.lastModified())
            } catch (_: Exception) {
            }
        }
        val bundled = ctx.assets.open(ASSET).bufferedReader().use { it.readText() }
        return Loaded(ConfigParser.parse(bundled), Source.BUNDLED, null)
    }

    fun saveDownloaded(section: String) {
        dir.mkdirs()
        val tmp = File(dir, "config-section.md.tmp")
        tmp.writeText(section)
        if (!tmp.renameTo(cache)) {
            cache.writeText(section)
            tmp.delete()
        }
    }

    companion object {
        const val ASSET = "nbt-aad-config.md"

        /** A list shorter than this means the README changed its layout; the old list is kept then. */
        const val MIN_OPTIONS = 60
    }
}
