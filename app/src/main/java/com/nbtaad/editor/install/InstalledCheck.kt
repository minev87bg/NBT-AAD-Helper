package com.nbtaad.editor.install

import com.nbtaad.editor.TimeText
import java.time.Instant
import java.time.ZoneId

/** How the nbt-aad on the head unit relates to the releases on Sq00ky's page. */
enum class Origin {
    /** The very same file: its SHA-256 is the one GitHub lists for that release's nbt-aad. */
    OFFICIAL,

    /** A release has this version name; the file itself could not be compared (no checksum listed, or it was not read). */
    MATCHES_NAME,

    /** A release has this version name, but the file is not the one that release published. */
    CHANGED,

    /** Nothing on the Releases page has this version or this file. */
    UNLISTED,

    /** The Releases page could not be looked at, or the installed file could not be read. */
    UNKNOWN,
}

/** The verdict. [release] is the release the file belongs to; [newer] the latest release when [release] is not it. */
class InstalledVerdict(
    val origin: Origin,
    val release: ReleaseInfo? = null,
    val newer: ReleaseInfo? = null,
    /** For [Origin.UNKNOWN]: what is missing. For [Origin.MATCHES_NAME]: true when the file was not read. */
    val unread: Boolean = false,
    val why: String = "",
)

enum class Tone { GOOD, WARN, PLAIN }

/** The verdict in words: a short headline and one more sentence or two. */
class Described(val headline: String, val detail: String, val tone: Tone)

/**
 * Decides whether the nbt-aad on the head unit is an official release, by comparing what the app read back over SSH with
 * what GitHub says about the project's releases: first the SHA-256 (the same bytes: no doubt), then the version name
 * (which only says what the file calls itself). Kept free of Android classes so the build's unit tests can run it.
 */
object InstalledCheck {
    /** The loose nbt-aad file of a release (its checksum is what the installed file is compared with), if it has one. */
    private fun daemonAsset(r: ReleaseInfo): AssetInfo? = r.assets.firstOrNull { Names.canonical(it.name) == Names.DAEMON }

    /** A release is named like the version when its tag is, or when its title starts with it ("v1.9.111 - Gauge cluster type"). */
    fun sameName(r: ReleaseInfo, version: String): Boolean {
        val v = version.trim().lowercase()
        if (v.isEmpty()) return false
        return r.tag.trim().lowercase() == v || r.title.trim().lowercase().substringBefore(' ') == v
    }

    /**
     * [version] is what the installed daemon calls itself, [sha256] and [size] what was read back from the head unit
     * (null: the file could not be read), [releases] the project's releases, newest first (null: not loaded).
     */
    fun judge(version: String?, sha256: String?, size: Long?, releases: List<ReleaseInfo>?): InstalledVerdict {
        if (releases == null) return InstalledVerdict(Origin.UNKNOWN, why = "Sq00ky's Releases list is not loaded (no internet?).")
        if (version == null && sha256 == null) {
            return InstalledVerdict(Origin.UNKNOWN, unread = true, why = "The installed file could not be read.")
        }
        val latest = releases.firstOrNull { !it.prerelease }
        fun newerThan(r: ReleaseInfo): ReleaseInfo? =
            if (latest != null && releases.indexOf(r) > releases.indexOf(latest)) latest else null

        // the same bytes
        if (sha256 != null) {
            releases.firstOrNull { r -> daemonAsset(r)?.sha256 == sha256 }?.let {
                return InstalledVerdict(Origin.OFFICIAL, it, newerThan(it))
            }
        }
        // the same name
        if (version != null) {
            releases.firstOrNull { sameName(it, version) }?.let { named ->
                val a = daemonAsset(named)
                val differs = when {
                    sha256 != null && a?.sha256 != null -> sha256 != a?.sha256
                    size != null && size > 0 && a != null && a.size > 0 -> size != a.size
                    else -> false
                }
                return InstalledVerdict(
                    if (differs) Origin.CHANGED else Origin.MATCHES_NAME, named, newerThan(named), unread = sha256 == null,
                )
            }
        }
        return InstalledVerdict(Origin.UNLISTED)
    }

    private fun published(r: ReleaseInfo, zone: ZoneId, now: Instant): String = TimeText.full(r.published, zone, now)

    /** The words for a verdict. [version] is what the installed daemon calls itself (null: it names none). */
    fun describe(
        v: InstalledVerdict,
        version: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Described {
        val r = v.release
        val tail = when {
            r == null -> ""
            v.newer != null -> " A newer release is out: ${v.newer.tag}, ${published(v.newer, zone, now)}."
            else -> " It is the latest release."
        }
        return when (v.origin) {
            Origin.OFFICIAL -> Described(
                if (r!!.prerelease) "Official pre-release" else "Official release",
                "The same file as ${r.tag} on Sq00ky's Releases page, compared byte for byte. " +
                    "Published ${published(r, zone, now)}." + tail,
                if (r.prerelease) Tone.WARN else Tone.GOOD,
            )
            Origin.MATCHES_NAME -> Described(
                "Matches release ${r!!.tag}",
                "The version name is the one on Sq00ky's Releases page, published ${published(r, zone, now)}. " +
                    (if (v.unread) "The file could not be read back, so it was not compared byte for byte."
                    else "GitHub lists no checksum for that release's file, so it was not compared byte for byte.") + tail,
                Tone.GOOD,
            )
            Origin.CHANGED -> Described(
                "Not the official file",
                "The version says ${version ?: r!!.tag}, but the file is not the one Sq00ky published for ${r!!.tag}: " +
                    "a test build, or a changed file.",
                Tone.WARN,
            )
            Origin.UNLISTED -> Described(
                "Not official, or too old",
                "None of the newest releases on Sq00ky's Releases page has this version or this file. Probably a test " +
                    "build, for example one shared in an issue, or a release older than the ones the app looks at.",
                Tone.WARN,
            )
            Origin.UNKNOWN -> Described("Could not compare", v.why, Tone.PLAIN)
        }
    }
}
