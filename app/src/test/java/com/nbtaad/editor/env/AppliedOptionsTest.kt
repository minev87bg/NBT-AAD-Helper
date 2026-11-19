package com.nbtaad.editor.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppliedOptionsTest {
    private val catalog = listOf(
        Opt("NBT_RESOLUTION", values = listOf("1", "2"), labels = mapOf("1" to "800x480", "2" to "1280x720")),
        Opt("NBT_FORCE_X"),
    )

    @Test
    fun onlyTheSettingsThatAreSwitchedOnAreListedInFileOrder() {
        val env = "# my settings\nNBT_FORCE_X=1\n#NBT_OFF=1\nNBT_RESOLUTION=2\nNBT_EMPTY=\nNBT_UNKNOWN=hello world\n"
        val list = AppliedOptions.of(env, catalog)
        assertEquals(listOf("NBT_FORCE_X", "NBT_RESOLUTION", "NBT_UNKNOWN"), list.map { it.key })
        assertEquals(listOf("1", "2", "hello world"), list.map { it.value })
    }

    @Test
    fun aValueTheReadmeNamesShowsItsMeaning() {
        val list = AppliedOptions.of("NBT_RESOLUTION=2\nNBT_RESOLUTION_TYPO=2\nNBT_FORCE_X=1\n", catalog)
        assertEquals("2 (1280x720)", list[0].shown)
        assertEquals("1280x720", list[0].meaning)
        assertEquals("2", list[1].shown)          // not in the list at all
        assertNull(list[1].meaning)
        assertEquals("1", list[2].shown)          // in the list, but the README names no meaning for 1
    }

    @Test
    fun aSettingThatIsGivenTwiceCountsOnceWithItsLastValue() {
        val list = AppliedOptions.of("NBT_RESOLUTION=1\nNBT_FORCE_X=1\nNBT_RESOLUTION=2\n", catalog)
        assertEquals(listOf("NBT_RESOLUTION", "NBT_FORCE_X"), list.map { it.key })
        assertEquals("2", list[0].value)
    }

    @Test
    fun noFileOrNoSettingsGivesAnEmptyList() {
        assertEquals(0, AppliedOptions.of("", catalog).size)
        assertEquals(0, AppliedOptions.of("# only comments\n#NBT_A=1\n\n", catalog).size)
    }
}
