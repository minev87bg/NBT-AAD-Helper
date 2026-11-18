package com.nbtaad.editor.data

import android.content.Context
import com.nbtaad.editor.env.CustomOption

/** The names of the options the person added by hand (page 2, "+ Add an option"), kept on the phone. */
class CustomOptionStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("custom_options", Context.MODE_PRIVATE)

    fun load(): List<String> = CustomOption.decode(prefs.getString(KEY, null))

    fun save(keys: List<String>) {
        prefs.edit().putString(KEY, CustomOption.encode(keys)).apply()
    }

    companion object {
        private const val KEY = "keys"
    }
}
