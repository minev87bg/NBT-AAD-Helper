package com.nbtaad.editor.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The parser is checked against the README section that ships inside the app. */
class ConfigParserTest {
    private val section: String = File("src/main/assets/nbt-aad-config.md").readText()
    private val opts: List<Opt> by lazy { ConfigParser.parse(section) }
    private fun opt(key: String): Opt = opts.firstOrNull { it.key == key } ?: throw AssertionError("$key is missing")

    @Test
    fun readsEveryDocumentedSetting() {
        assertTrue("only ${opts.size} settings", opts.size >= 190)
        assertEquals("keys must be unique", opts.size, opts.map { it.key }.toSet().size)
        for (o in opts) {
            assertTrue(o.key, Regex("^NBT_[A-Z0-9_]*[A-Z0-9]$").matches(o.key))
            assertTrue("${o.key} has no description", o.info.isNotBlank())
        }
    }

    @Test
    fun theThreeTablesKeepTheirGroups() {
        assertEquals("Common settings", opt("NBT_LOG_FILE").group)
        assertTrue(opt("NBT_MIC").group.startsWith("Features you can turn off"))
        assertEquals("Advanced / experimental", opt("NBT_VID_RES").group)
    }

    @Test
    fun settingsSharingARowAreSplit() {
        // `NBT_TOUCH_SX/SY/OX/OY`, `NBT_CAR_MAKE/MODEL/YEAR`, comma lists, and a template
        for (k in listOf("NBT_TOUCH_SX", "NBT_TOUCH_SY", "NBT_TOUCH_OX", "NBT_TOUCH_OY", "NBT_TOUCH_W", "NBT_TOUCH_H")) opt(k)
        for (k in listOf("NBT_CAR_MAKE", "NBT_CAR_MODEL", "NBT_CAR_YEAR", "NBT_AP_CFG", "NBT_AP_BSSID", "NBT_DUCK_DB", "NBT_SYSTEM_GAIN_DB")) opt(k)
        for (slot in listOf("MENU", "BACK", "OPTIONS", "COM", "MEDIA", "NAV", "MAP")) opt("NBT_KEY_$slot")
        assertTrue(opts.none { it.key.contains("<") || it.key.contains("/") })
    }

    @Test
    fun keyCodesBecomeADropdownWithTheDefaultOfEachSlot() {
        assertEquals("3 (HOME)", opt("NBT_KEY_MENU").default)
        assertEquals("65537 (MEDIA)", opt("NBT_KEY_MEDIA").default)
        assertTrue("0" in opt("NBT_KEY_OPTIONS").values)
        assertEquals("ignore", opt("NBT_KEY_OPTIONS").labels["0"])
    }

    @Test
    fun valuesNamedInTheTextBecomeChoices() {
        assertEquals(listOf("1", "2", "3"), opt("NBT_VID_RES").values)
        assertEquals("1280x720", opt("NBT_VID_RES").labels["2"])
        assertEquals(listOf("home", "media", "off"), opt("NBT_MENU_RETURN").values)
        assertEquals(listOf("toyota", "bmw", "mini", "rollsroyce"), opt("NBT_VEHICLE").values)
        assertEquals(listOf("CARPLAY", "DIRECT"), opt("NBT_ALT_WIFI").values)
        assertEquals(listOf("bt", "browser", "a2i"), opt("NBT_AUDIO_MODE").values)
        assertEquals(listOf("0", "1", "2"), opt("NBT_WPP_MODE").values)
        assertEquals("0xb8", opt("NBT_BROWSER_CONN").default)
    }

    @Test
    fun featureSwitchesAreOnOffDropdowns() {
        val mic = opt("NBT_MIC")
        assertEquals(listOf("1", "0"), mic.values)
        assertEquals("on", mic.default)
        assertEquals(listOf("1", "0"), opt("NBT_KNOB_INVERT").values)
    }

    @Test
    fun numbersStayTextFieldsWithTheirDefault() {
        val q = opt("NBT_VIDEO_QUEUE")
        assertTrue(q.values.isEmpty())
        assertEquals("15", q.default)
        assertEquals("1 to 60", q.hint)
        // a default of 1 is a number of miles here, not an on switch
        assertEquals("1", opt("NBT_KOMBI_NAV_APPROACH_MI").default)
        assertTrue(opt("NBT_KOMBI_NAV_APPROACH_MI").labels["1"] != "on")
    }

    @Test
    fun theClusterSettingKnowsOnlyWhatTheDaemonAccepts() {
        val c = opt("NBT_CLUSTER")
        assertEquals(listOf("j29", "6wa"), c.values)
        assertTrue(c.closed)
    }

    @Test
    fun settingsNamedOnlyInDescriptionsAreListed() {
        assertEquals(ConfigParser.GROUP_MENTIONED, opt("NBT_USB_MANUFACTURER").group)
        assertTrue(opts.none { it.key == "NBT_CCS_DEBUG" })
    }

    @Test
    fun aWholeReadmeIsReducedToItsConfigurationSection() {
        val readme = "# Title\n\n## Intro\ntext\n\n$section\n## Logs\nmore\n"
        val extracted = ConfigParser.extractSection(readme)
        assertNotNull(extracted)
        assertEquals(opts.size, ConfigParser.parse(extracted!!).size)
        assertNull(ConfigParser.extractSection("# nothing here"))
    }

    @Test
    fun aNewRowInTheReadmeShowsUpWithoutAnAppUpdate() {
        val extra = "\n### Advanced / experimental\n\n| Setting | Effect |\n|---|---|\n" +
            "| `NBT_BRAND_NEW` | Does something new (default `7`, `1`-`9`) |\n"
        val all = ConfigParser.parse(section + extra)
        val n = all.first { it.key == "NBT_BRAND_NEW" }
        assertEquals("7", n.default)
        assertEquals("1 to 9", n.hint)
        assertEquals(opts.size + 1, all.size)
    }

    @Test
    fun theUsersFileKeepsEveryLineAndOnlyTheChangedOneMoves() {
        val file = "NBT_KEEP_PF=1\nNBT_CLUSTER=6WB\nNBT_BACK_HOLD_MS=1000\nNBT_VEHICLE=bmw\n"
        val doc = EnvDoc.parse(file)
        assertEquals(file, doc.render())
        assertEquals("6WB", doc.get("NBT_CLUSTER"))
        val edit = doc.copyDoc()
        edit.set("NBT_CLUSTER", "6wa")
        assertEquals("NBT_KEEP_PF=1\nNBT_CLUSTER=6wa\nNBT_BACK_HOLD_MS=1000\nNBT_VEHICLE=bmw\n", edit.render())
        edit.set("NBT_VEHICLE", null)
        assertEquals("#NBT_VEHICLE=bmw\n", edit.render().lines()[3] + "\n")
        assertNull(edit.get("NBT_VEHICLE"))
        edit.set("NBT_MIC", "0")
        assertTrue(edit.render().endsWith("NBT_MIC=0\n"))
    }

    @Test
    fun theLoaderRulesOfTheHeadUnitAreChecked() {
        val ok = EnvCheck.analyze("# comment\n\nNBT_A=1\nNBT_B=2\n")
        assertEquals(2, ok.count)
        assertTrue(ok.errors.isEmpty())

        val many = (1..16).joinToString("\n") { "NBT_S$it=1" }
        val r = EnvCheck.analyze(many)
        assertEquals(16, r.count)
        assertTrue(r.errors.single().contains("first 15"))
        assertTrue(EnvCheck.analyze((1..15).joinToString("\n") { "NBT_S$it=1" }).errors.isEmpty())

        val long = EnvCheck.analyze("NBT_X=" + "a".repeat(160))
        assertTrue(long.errors.single().contains("159"))
        assertTrue(EnvCheck.analyze("NBT_X=" + "a".repeat(153)).errors.isEmpty())

        val warn = EnvCheck.analyze("export NBT_A=1\nNBT_B=2 # note\n  NBT_C=3\n")
        assertEquals(3, warn.warnings.size)
        assertFalse(warn.errors.isNotEmpty())
    }
}
