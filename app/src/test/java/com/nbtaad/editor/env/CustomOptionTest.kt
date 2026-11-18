package com.nbtaad.editor.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomOptionTest {
    private fun ok(name: String, value: String = ""): CustomOption.Parsed =
        CustomOption.parse(name, value) as? CustomOption.Parsed ?: throw AssertionError("'$name' was refused")

    private fun why(name: String, value: String = ""): String =
        (CustomOption.parse(name, value) as? CustomOption.Bad)?.why ?: throw AssertionError("'$name' was accepted")

    @Test
    fun aNameAndAValueAreAccepted() {
        assertEquals(CustomOption.Parsed("NBT_FOO", "1"), CustomOption.parse("NBT_FOO", "1"))
        assertEquals("", ok("NBT_FOO").value)
        assertEquals("NBT_FOO", ok("  NBT_FOO  ").key)
        assertEquals("_OTHER_1", ok("_OTHER_1").key)   // not only NBT_ names: the loader takes any NAME=value
    }

    @Test
    fun nbtNamesAreWrittenInCapitals() {
        assertEquals("NBT_FOO_BAR", ok("nbt_foo_bar").key)
        assertEquals("NBT_FOO", ok("Nbt_Foo").key)
        assertEquals("other_name", ok("other_name").key)   // anything else is kept as typed
    }

    @Test
    fun aPastedLineIsSplitAtTheEqualsSign() {
        assertEquals(CustomOption.Parsed("NBT_FOO", "bar"), CustomOption.parse("NBT_FOO=bar", ""))
        assertEquals(CustomOption.Parsed("NBT_FOO", "baz"), CustomOption.parse("NBT_FOO=bar", "baz"))   // the value field wins
        assertEquals(CustomOption.Parsed("NBT_FOO", "a=b"), CustomOption.parse("NBT_FOO=a=b", ""))
    }

    @Test
    fun aValueIsOneTrimmedLine() {
        assertEquals("a b", ok("NBT_X", "a\nb").value)
        assertEquals("a b", ok("NBT_X", "  a\r\nb  ").value)
    }

    @Test
    fun badNamesAreRefusedWithAReason() {
        assertTrue(why("").contains("Type the name"))
        assertTrue(why("   ").contains("Type the name"))
        assertTrue(why("NBT_").contains("after NBT_"))
        assertTrue(why("NBT FOO").contains("no spaces"))
        assertTrue(why("1ABC").contains("letters"))
        assertTrue(why("NBT_A-B").contains("letters"))
        assertTrue(why("N".repeat(65)).contains("too long"))
        assertTrue(why("=1").contains("Type the name"))
    }

    @Test
    fun aLineThatTheHeadUnitWouldCutIsRefused() {
        assertEquals("a".repeat(153), ok("NBT_X", "a".repeat(153)).value)   // 5 + 1 + 153 = 159
        assertTrue(why("NBT_X", "a".repeat(154)).contains("${EnvCheck.MAX_LINE}"))
    }

    @Test
    fun theNamesSurviveStoringAndLoading() {
        val keys = listOf("NBT_A", "NBT_B", "_C")
        assertEquals(keys, CustomOption.decode(CustomOption.encode(keys)))
        assertEquals(listOf("NBT_A", "NBT_B"), CustomOption.decode("NBT_A\nbad name\n\nNBT_B\nNBT_A\n=x"))
        assertEquals(emptyList<String>(), CustomOption.decode(null))
        assertEquals(emptyList<String>(), CustomOption.decode(""))
        assertTrue(CustomOption.isName("NBT_FOO"))
        assertTrue(!CustomOption.isName("NBT FOO"))
    }
}
