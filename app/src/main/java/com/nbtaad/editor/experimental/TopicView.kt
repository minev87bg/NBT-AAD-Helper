package com.nbtaad.editor.experimental

/** How the list of pages is ordered, and the name GitHub knows that order by (for the request that fetches the list). */
enum class SortBy(val label: String, val api: String) {
    ACTIVITY("Recent activity", "updated"),
    OPENED("Date opened", "created"),
}

/** Which pages the list shows: the open ones, the closed ones, or the ones the person follows (open or closed). */
enum class Show { OPEN, CLOSED, FOLLOWING }

/** What the list of issues and pull requests shows for the choices on top of it. Free of Android classes, so it is tested. */
object TopicView {
    /** Newest first by the chosen time. GitHub writes its times as ISO 8601 in UTC, so the text order is the time order. */
    fun sorted(list: List<Topic>, by: SortBy): List<Topic> = when (by) {
        SortBy.ACTIVITY -> list.sortedWith(compareByDescending<Topic> { it.updated }.thenByDescending { it.number })
        SortBy.OPENED -> list.sortedWith(compareByDescending<Topic> { it.created }.thenByDescending { it.number })
    }

    private fun picked(list: List<Topic>, pullRequests: Boolean, show: Show, following: Set<Int>): List<Topic> = when (show) {
        Show.OPEN -> list.filter { it.isPr == pullRequests && it.open }
        Show.CLOSED -> list.filter { it.isPr == pullRequests && !it.open }
        // followed pages are shown whatever they are and whatever state they are in
        Show.FOLLOWING -> list.filter { it.number in following }
    }

    fun shown(list: List<Topic>, pullRequests: Boolean, show: Show, following: Set<Int>, by: SortBy): List<Topic> =
        sorted(picked(list, pullRequests, show, following), by)

    fun count(list: List<Topic>, pullRequests: Boolean, show: Show, following: Set<Int>): Int =
        picked(list, pullRequests, show, following).size

    /** How many of the followed pages are not in the loaded list (older than the newest ones the app loads). */
    fun missing(list: List<Topic>, following: Set<Int>): Int {
        val have = list.map { it.number }.toSet()
        return following.count { it !in have }
    }
}
