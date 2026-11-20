package com.nbtaad.editor.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveFilesScriptTest {
    @Test
    fun theScriptNamesEveryFileAndKeepsTheShellVariables() {
        val script = RemoteScripts.removeFiles(listOf("/dev/shmem/nbt-aad.log", "/mnt/umass00100t11/nbt-aad.log"))
        assertTrue("/dev/shmem/nbt-aad.log" in script)
        assertTrue("/mnt/umass00100t11/nbt-aad.log" in script)
        assertTrue("rm -f \$f" in script)
        assertTrue("echo \"FAIL \$f\"" in script)
        assertFalse('¤' in script)
    }
}
