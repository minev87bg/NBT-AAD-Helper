package com.nbtaad.editor.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persists the user's light/dark choice (or "follow system") across launches. */
class ThemePrefs(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun read(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(KEY, null) ?: return ThemeMode.SYSTEM)
    }.getOrDefault(ThemeMode.SYSTEM)

    fun write(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    companion object {
        private const val KEY = "mode"
    }
}
