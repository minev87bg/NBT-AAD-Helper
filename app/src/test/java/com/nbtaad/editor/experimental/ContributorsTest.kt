package com.nbtaad.editor.experimental

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributorsTest {
    @Test
    fun theMainContributorsAreKnownWhateverTheCase() {
        assertTrue(Contributors.isMain("Sq00ky"))
        assertTrue(Contributors.isMain("sq00ky"))
        assertTrue(Contributors.isMain("mhmm-ok"))
        assertTrue(Contributors.isMain(" Tom-Pryor "))
    }

    @Test
    fun everybodyElseIsNot() {
        assertFalse(Contributors.isMain("dn00"))
        assertFalse(Contributors.isMain("tom-prior"))
        assertFalse(Contributors.isMain("mhmm"))
        assertFalse(Contributors.isMain(""))
        assertFalse(Contributors.isMain(null))
    }
}
