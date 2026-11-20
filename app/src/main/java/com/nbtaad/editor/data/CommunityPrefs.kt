package com.nbtaad.editor.data

import android.content.Context
import com.nbtaad.editor.experimental.SortBy

/** What the community browser keeps on the phone: the pages the person follows (by number) and how the list is sorted. */
class CommunityPrefs(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("community_prefs", Context.MODE_PRIVATE)

    fun following(): Set<Int> =
        (prefs.getStringSet(FOLLOWING, null) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()

    fun saveFollowing(numbers: Set<Int>) {
        prefs.edit().putStringSet(FOLLOWING, numbers.map { it.toString() }.toSet()).apply()
    }

    fun sort(): SortBy = SortBy.entries.firstOrNull { it.name == prefs.getString(SORT, null) } ?: SortBy.ACTIVITY

    fun saveSort(by: SortBy) {
        prefs.edit().putString(SORT, by.name).apply()
    }

    companion object {
        private const val FOLLOWING = "following"
        private const val SORT = "sort"
    }
}
