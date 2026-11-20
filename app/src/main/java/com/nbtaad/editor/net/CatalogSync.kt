package com.nbtaad.editor.net

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the project's README so the list of settings stays current (see [Internet] for how it gets out). */
object CatalogSync {
    const val README_URL =
        "https://raw.githubusercontent.com/Sq00ky/Supra-iDrive6-Android-Auto/main/milestone-7/readme.md"
    private const val MAX_BYTES = 3 * 1024 * 1024

    fun fetchReadme(ctx: Context): String = Internet.run(ctx) { open -> download(open) }

    private fun download(open: (URL) -> HttpURLConnection): String {
        val conn = open(URL(README_URL))
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "NBT-AAD-Tool")
            conn.setRequestProperty("Accept", "text/plain")
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("The README is unexpectedly large")
                }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
