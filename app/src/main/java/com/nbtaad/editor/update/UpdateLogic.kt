package com.nbtaad.editor.update

import java.security.MessageDigest

/** What a GitHub release offers to the app. */
data class UpdateOffer(
    val tag: String,
    val build: Int,
    val title: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String?,
    val pageUrl: String,
)

/**
 * The decisions of the updater that need no Android: which release is newer, which file to take, how the
 * release notes are shortened, whether a download is intact. Kept free of Android classes so the build's unit
 * tests can run it.
 */
object UpdateLogic {
    const val OWNER = "minev87bg"
    const val REPO = "NBT-AAD-Helper"
    const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** Every CI build is released as v1.0.<run number>; the run number is also the app's versionCode. */
    fun buildOf(tag: String): Int? =
        Regex("""(\d+)\s*$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun isNewer(installedCode: Long, offerBuild: Int): Boolean = offerBuild.toLong() > installedCode

    /** The first .apk among the release's files. */
    fun pickApk(names: List<String>): Int? =
        names.indexOfFirst { it.endsWith(".apk", ignoreCase = true) }.takeIf { it >= 0 }

    /** "sha256:abcd…" as GitHub writes it, or a bare hex string; anything else gives null. */
    fun parseDigest(d: String?): String? {
        val hex = d?.trim()?.removePrefix("sha256:")?.lowercase() ?: return null
        return hex.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** The release notes without the blank lines and git trailers, at most [maxLines] lines. */
    fun shortNotes(body: String?, maxLines: Int = 4): String {
        val lines = (body ?: "").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Co-Authored-By:", true) && !it.startsWith("Claude-Session:", true) }
        val cut = lines.take(maxLines).joinToString("\n")
        return if (lines.size > maxLines) "$cut\n…" else cut
    }

    /** Is the downloaded file what the release announced? [sha256] is only known when GitHub provides it. */
    fun intact(size: Long, expectedSize: Long, actualSha256: String, expectedSha256: String?): String? = when {
        expectedSize > 0 && size != expectedSize -> "The download is incomplete ($size of $expectedSize bytes)."
        expectedSha256 != null && !expectedSha256.equals(actualSha256, ignoreCase = true) ->
            "The downloaded file does not match the checksum GitHub lists for it."
        else -> null
    }
}
