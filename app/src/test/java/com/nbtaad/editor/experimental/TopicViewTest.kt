package com.nbtaad.editor.experimental

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicViewTest {
    private fun topic(
        n: Int,
        pr: Boolean = false,
        open: Boolean = true,
        created: String = "2026-11-01T10:00:00Z",
        updated: String = "2026-11-01T10:00:00Z",
    ) = Topic(
        number = n, isPr = pr, title = "t$n", author = "a", open = open, merged = false, draft = false, comments = 0,
        created = created, updated = updated, labels = emptyList(), body = "", url = "https://github.com/x/y/issues/$n",
    )

    private val list = listOf(
        topic(1, created = "2026-10-01T10:00:00Z", updated = "2026-11-18T10:00:00Z"),
        topic(2, created = "2026-11-10T10:00:00Z", updated = "2026-11-11T10:00:00Z"),
        topic(3, pr = true, created = "2026-11-05T10:00:00Z", updated = "2026-11-19T10:00:00Z"),
        topic(4, open = false, created = "2026-09-01T10:00:00Z", updated = "2026-11-17T10:00:00Z"),
        topic(5, pr = true, open = false, created = "2026-11-12T10:00:00Z", updated = "2026-11-12T10:00:00Z"),
    )

    private fun numbers(l: List<Topic>) = l.map { it.number }

    @Test fun recentActivityPutsTheLastChangedFirst() {
        assertEquals(listOf(1, 2), numbers(TopicView.shown(list, false, Show.OPEN, emptySet(), SortBy.ACTIVITY)))
    }

    @Test fun dateOpenedPutsTheNewestOpenedFirst() {
        assertEquals(listOf(2, 1), numbers(TopicView.shown(list, false, Show.OPEN, emptySet(), SortBy.OPENED)))
    }

    @Test fun theTabChoosesIssuesOrPullRequests() {
        assertEquals(listOf(3), numbers(TopicView.shown(list, true, Show.OPEN, emptySet(), SortBy.ACTIVITY)))
        assertEquals(listOf(5), numbers(TopicView.shown(list, true, Show.CLOSED, emptySet(), SortBy.ACTIVITY)))
        assertEquals(listOf(4), numbers(TopicView.shown(list, false, Show.CLOSED, emptySet(), SortBy.ACTIVITY)))
    }

    @Test fun followedPagesShowWhateverTheyAre() {
        // an issue, a pull request and a closed one together, whichever tab was chosen, in the chosen order
        val shown = TopicView.shown(list, false, Show.FOLLOWING, setOf(1, 3, 4), SortBy.ACTIVITY)
        assertEquals(listOf(3, 1, 4), numbers(shown))
        assertEquals(listOf(1, 3, 4), numbers(TopicView.shown(list, true, Show.FOLLOWING, setOf(1, 3, 4), SortBy.OPENED).sortedBy { it.number }))
    }

    @Test fun equalTimesFallBackToTheNumber() {
        val same = listOf(topic(7), topic(9), topic(8))
        assertEquals(listOf(9, 8, 7), numbers(TopicView.sorted(same, SortBy.ACTIVITY)))
    }

    @Test fun countsFollowTheSameRules() {
        assertEquals(2, TopicView.count(list, false, Show.OPEN, emptySet()))
        assertEquals(1, TopicView.count(list, true, Show.OPEN, emptySet()))
        assertEquals(2, TopicView.count(list, false, Show.FOLLOWING, setOf(2, 5)))
    }

    @Test fun followedPagesThatAreNotLoadedAreCounted() {
        assertEquals(2, TopicView.missing(list, setOf(1, 99, 100)))
        assertEquals(0, TopicView.missing(list, emptySet()))
    }

    @Test fun theApiKnowsTheOrdersByGitHubsNames() {
        assertEquals("updated", SortBy.ACTIVITY.api)
        assertEquals("created", SortBy.OPENED.api)
    }
}
