package com.nbtaad.editor.install

/** One file attached to a GitHub release. */
data class AssetInfo(val name: String, val size: Long, val url: String, val sha256: String?)

/** One release of the NBT-AAD project as GitHub lists it. */
data class ReleaseInfo(
    val tag: String,
    val title: String,
    val prerelease: Boolean,
    val published: String,
    val notes: String,
    val pageUrl: String,
    val assets: List<AssetInfo>,
)

/** Which of a release's files the installer needs. Kept free of Android classes so the build's tests can run it. */
object ReleaseLogic {
    const val OWNER = "Sq00ky"
    const val REPO = "Supra-iDrive6-Android-Auto"
    const val LIST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"
    const val PAGE_URL = "https://github.com/$OWNER/$REPO/releases"

    /** A release's files that can be part of a build: the build's own files and zip archives (not .nbtpkg or records). */
    fun wanted(r: ReleaseInfo): List<AssetInfo> =
        r.assets.filter { a ->
            Names.canonical(a.name) != null || Names.isContainer(a.name) || a.name.endsWith(".zip", ignoreCase = true)
        }

    /** True when the release has anything to install. */
    fun installable(r: ReleaseInfo): Boolean = wanted(r).isNotEmpty()

    /** Total bytes the app would download for the release. */
    fun downloadSize(r: ReleaseInfo): Long = wanted(r).sumOf { maxOf(it.size, 0L) }

    /** "2026-05-12T10:11:12Z" to "2026-05-12". */
    fun day(iso: String): String = iso.take(10)

    fun mb(bytes: Long): String =
        if (bytes < 1024 * 1024) "${maxOf(1L, bytes / 1024)} KB" else "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
