package com.nbtaad.editor.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Runs the uninstall script with this machine's own sh on a folder made like a head unit with a hand install. */
class UninstallScriptTest {
    private class Fake(val root: File) {
        val aa = File(root, "opt/aa")
        val plugin = File(root, "opt/hmi/ID5/data/ro/Plugins/NbtAad")
        val va = File(root, "var/opt/aa")
        fun at(dir: File, name: String) = File(dir, name)
    }

    private fun fake(body: (Fake) -> Unit) {
        assumeTrue(File("/bin/sh").canExecute())
        val root = File.createTempFile("nbt-aad-uninstall", "").also { it.delete() }
        val f = Fake(root)
        try {
            f.aa.mkdirs(); f.plugin.mkdirs(); f.va.mkdirs()
            for (n in listOf("nbt-aad", "nbt-aad.old", "nbt-aad.new", "nbt-aad.broken", "pf_aa", "nbt-aad.env", "nbt-aad.env.orig", "nbt-aad.env.bak", "nbt-aad.env.bak2", "nbt-aad.env.bak3", "nbt-aad.env.new")) {
                File(f.aa, n).writeText(n)
            }
            File(f.aa, "NbtAad.new").mkdirs(); File(f.aa, "NbtAad.new/libNbtAad.so").writeText("staging")
            File(f.aa, "my-notes.txt").writeText("not ours")
            for (n in listOf("libNbtAad.so", "NbtAad.manifest", "NbtAad.xml")) File(f.plugin, n).writeText(n)
            File(f.va, "enable_log").writeText("1")
            File(f.va, "usb_logs").writeText("1")
            File(f.va, "subfolder").mkdirs(); File(f.va, "subfolder/x").writeText("x")
            body(f)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun run(script: String): String {
        val p = ProcessBuilder("/bin/sh", "-c", script).redirectError(File("/dev/null")).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        p.waitFor()
        return out
    }

    private fun script(f: Fake, keepEnv: Boolean, apply: Boolean): String {
        RemoteScripts.pathFix = ""
        return RemoteScripts.uninstall(f.aa.path, f.plugin.path, f.va.path, keepEnv, apply)
    }

    private fun snapshot(root: File): List<String> = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path + ":" + it.readText() }.sorted().toList()

    @Test
    fun aDryRunSaysWhatItWouldDoAndChangesNothing() = fake { f ->
        val before = snapshot(f.root)
        val plan = RemoteScripts.parseUninstall(run(script(f, keepEnv = false, apply = false)))
        assertEquals(before, snapshot(f.root))
        assertTrue(plan.done)
        assertFalse(plan.nothing)
        assertEquals(listOf(f.plugin.path to File(f.aa, "NbtAad.old").path), plan.moves)
        assertTrue(File(f.aa, "nbt-aad").path in plan.removes)
        assertTrue(File(f.aa, "NbtAad.new").path in plan.removes)
        assertTrue(File(f.va, "enable_log").path in plan.removes)
        assertEquals(6, plan.envFiles.size)
        assertTrue(plan.kept.isEmpty())
        assertTrue(File(f.aa, "my-notes.txt").path in plan.left)
        assertTrue(File(f.va, "subfolder").path in plan.left)
    }

    @Test
    fun applyingRemovesWhatItNamedParksThePluginAndLeavesTheRest() = fake { f ->
        val said = RemoteScripts.parseUninstall(run(script(f, keepEnv = false, apply = false)))
        val did = RemoteScripts.parseUninstall(run(script(f, keepEnv = false, apply = true)))
        assertTrue(did.done)
        assertEquals(null, did.failed)
        // it does what it said it would
        assertEquals(said.moves, did.moves)
        assertEquals(said.removes.sorted(), did.removes.sorted())
        assertEquals(said.envFiles.sorted(), did.envFiles.sorted())
        assertFalse(File(f.aa, "nbt-aad").exists())
        assertFalse(File(f.aa, "nbt-aad.old").exists())
        assertFalse(File(f.aa, "NbtAad.new").exists())
        assertFalse(File(f.aa, "nbt-aad.env").exists())
        assertFalse(File(f.aa, "nbt-aad.env.bak3").exists())
        assertFalse(File(f.va, "enable_log").exists())
        // the plugin is parked as a whole, not deleted
        assertFalse(f.plugin.exists())
        assertEquals("libNbtAad.so", File(f.aa, "NbtAad.old/libNbtAad.so").readText())
        assertTrue(File(f.aa, "NbtAad.old/NbtAad.xml").exists())
        // what is not NBT-AAD's stays
        assertEquals("not ours", File(f.aa, "my-notes.txt").readText())
        assertEquals("x", File(f.va, "subfolder/x").readText())
        // a second run finds nothing of it
        val again = RemoteScripts.parseUninstall(run(script(f, keepEnv = false, apply = false)))
        assertTrue(again.moves.isEmpty())
        assertTrue(again.envFiles.isEmpty())
        assertEquals(listOf(File(f.aa, "my-notes.txt").path, File(f.va, "subfolder").path).sorted(), again.left.sorted())
        assertTrue(again.removes.isEmpty())
    }

    @Test
    fun keepingTheSettingsLeavesTheEnvFileAndItsBackups() = fake { f ->
        val did = RemoteScripts.parseUninstall(run(script(f, keepEnv = true, apply = true)))
        assertTrue(did.done)
        assertTrue(did.envFiles.isEmpty())
        assertEquals(6, did.kept.size)
        assertEquals("nbt-aad.env", File(f.aa, "nbt-aad.env").readText())
        assertTrue(File(f.aa, "nbt-aad.env.orig").exists())
        assertFalse(File(f.aa, "nbt-aad").exists())
    }

    @Test
    fun aFolderParkedByAnEarlierUninstallStaysUntilAnotherIsParkedOverIt() = fake { f ->
        run(script(f, keepEnv = false, apply = true))
        assertTrue(File(f.aa, "NbtAad.old").isDirectory)
        // nothing new to park: the parked one is neither listed nor removed
        val plan = RemoteScripts.parseUninstall(run(script(f, keepEnv = false, apply = true)))
        assertTrue(File(f.aa, "NbtAad.old/libNbtAad.so").exists())
        assertTrue(plan.moves.isEmpty())
        assertFalse(File(f.aa, "NbtAad.old").path in plan.removes)
        // a plugin put there again replaces it
        f.plugin.mkdirs(); File(f.plugin, "libNbtAad.so").writeText("newer")
        val again = RemoteScripts.parseUninstall(run(script(f, keepEnv = false, apply = true)))
        assertEquals(1, again.moves.size)
        assertEquals("newer", File(f.aa, "NbtAad.old/libNbtAad.so").readText())
    }

    @Test
    fun withoutAPluginOrAVarFolderItOnlyTouchesTheDaemonFiles() = fake { f ->
        RemoteScripts.pathFix = ""
        val out = run(RemoteScripts.uninstall(f.aa.path, null, null, keepEnv = false, apply = true))
        val plan = RemoteScripts.parseUninstall(out)
        assertTrue(plan.done)
        assertTrue(plan.moves.isEmpty())
        assertTrue(f.plugin.exists())
        assertTrue(File(f.va, "enable_log").exists())
        assertFalse(File(f.aa, "nbt-aad").exists())
    }

    @Test
    fun anEmptyFolderHasNothingToRemove() {
        assumeTrue(File("/bin/sh").canExecute())
        val root = File.createTempFile("nbt-aad-uninstall", "").also { it.delete() }
        try {
            val aa = File(root, "opt/aa").also { it.mkdirs() }
            RemoteScripts.pathFix = ""
            val plan = RemoteScripts.parseUninstall(run(RemoteScripts.uninstall(aa.path, null, null, keepEnv = false, apply = false)))
            assertTrue(plan.done)
            assertTrue(plan.nothing)
            assertTrue(plan.left.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun itRefusesFoldersItWasNotMadeFor() {
        for (bad in listOf("/", "/opt", "/home/user", "/opt/aa/..", "")) {
            val e = runCatching { RemoteScripts.uninstall(bad, null, null, keepEnv = false, apply = true) }.exceptionOrNull()
            assertTrue("accepted '$bad'", e is IllegalArgumentException)
        }
        assertTrue(runCatching { RemoteScripts.uninstall("/opt/aa", "/opt/hmi", null, false, true) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { RemoteScripts.uninstall("/opt/aa", null, "/var/opt", false, true) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun theScriptKeepsTheShellVariables() = fake { f ->
        val s = script(f, keepEnv = false, apply = true)
        assertFalse('¤' in s)
        assertFalse("@AA@" in s || "@PL@" in s || "@VA@" in s)
        assertTrue("rm -rf \$f" in s)
    }

    @Test
    fun theLocatorReportsTheSettingsFolderOfTheSamePrefix() {
        val out = "P /net/hu-omap\nAA /net/hu-omap/opt/aa\nPL /net/hu-omap/opt/hmi/ID5/data/ro/Plugins/NbtAad\nVA /net/hu-omap/var/opt/aa\n"
        val loc = RemoteScripts.parseLocated(out)
        assertEquals("/net/hu-omap/opt/aa", loc.aa)
        assertEquals("/net/hu-omap/var/opt/aa", loc.va)
        assertEquals(null, RemoteScripts.parseLocated("P \nAA /opt/aa\n").va)
    }

    @Test
    fun theOutputIsReadBackLineByLine() {
        val p = RemoteScripts.parseUninstall("REMOUNT failed\nMV /a /b\nRM /x\nRMENV /e\nKEEP /k\nLEFT /l\nDONE\n")
        assertTrue(p.remountFailed && p.done)
        assertEquals(listOf("/a" to "/b"), p.moves)
        assertEquals(listOf("/x"), p.removes)
        assertEquals(listOf("/e"), p.envFiles)
        assertEquals(listOf("/k"), p.kept)
        assertEquals(listOf("/l"), p.left)
        assertEquals("plugin", RemoteScripts.parseUninstall("FAIL plugin\n").failed)
        assertFalse(RemoteScripts.parseUninstall("").done)
    }
}
