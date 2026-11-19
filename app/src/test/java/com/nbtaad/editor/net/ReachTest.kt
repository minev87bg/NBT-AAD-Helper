package com.nbtaad.editor.net

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReachTest {
    @Test
    fun aMissingConnectionIsToldFromARefusedLogin() {
        assertTrue(Reach.looksUnreachable(java.net.SocketTimeoutException("connect timed out")))
        assertTrue(Reach.looksUnreachable(java.net.ConnectException("Connection refused")))
        assertTrue(Reach.looksUnreachable(Exception("java.net.NoRouteToHostException: No route to host")))
        assertTrue(Reach.looksUnreachable(Exception("timeout: socket is not established")))
        assertTrue(Reach.looksUnreachable(Exception("End of IO Stream Read")))
        assertTrue(Reach.looksUnreachable(Exception("failed", java.net.UnknownHostException("10.0.0.1"))))
        // the connection was made, so looking for the car again would not help
        assertFalse(Reach.looksUnreachable(Exception("Auth fail")))
        assertFalse(Reach.looksUnreachable(Exception("Algorithm negotiation fail: no matching cipher found")))
        assertFalse(Reach.looksUnreachable(Exception("something else")))
    }

    @Test
    fun whenTheFirstTryWorksNoOneLooksForTheCarAgain() {
        var scans = 0
        val got = runOnHeadUnit({ "10.0.0.1" }, { scans++; "10.0.0.2" }) { host -> "ok on $host" }
        assertEquals("ok on 10.0.0.1", got)
        assertEquals(0, scans)
    }

    @Test
    fun noConnectionLooksForTheCarAndTriesOnceMoreAtTheNewAddress() {
        val tried = mutableListOf<String>()
        var scans = 0
        var told = 0
        val got = runOnHeadUnit({ "10.0.0.1" }, { scans++; "10.0.0.2" }, { told++ }) { host ->
            tried += host
            if (host == "10.0.0.1") throw SshUnreachable("timed out")
            "done on $host"
        }
        assertEquals("done on 10.0.0.2", got)
        assertEquals(listOf("10.0.0.1", "10.0.0.2"), tried)
        assertEquals(1, scans)
        assertEquals(1, told)
    }

    @Test
    fun whenTheCarIsNotFoundTheFirstFailureIsTheAnswer() {
        try {
            runOnHeadUnit<String>({ "10.0.0.1" }, { null }) { throw SshUnreachable("timed out") }
            fail("should have failed")
        } catch (e: SshUnreachable) {
            assertEquals("timed out", e.message)
        }
    }

    @Test
    fun aFailureAfterTheConnectionWasMadeIsNotRetried() {
        var runs = 0
        var scans = 0
        try {
            runOnHeadUnit<String>({ "10.0.0.1" }, { scans++; "10.0.0.2" }) { runs++; throw IOException("upload failed") }
            fail("should have failed")
        } catch (e: IOException) {
            assertEquals("upload failed", e.message)
        }
        assertEquals(1, runs)
        assertEquals(0, scans)
    }

    @Test
    fun theSecondTryIsTheLastOne() {
        var runs = 0
        try {
            runOnHeadUnit<String>({ "10.0.0.1" }, { "10.0.0.2" }) { runs++; throw SshUnreachable("timed out") }
            fail("should have failed")
        } catch (e: SshUnreachable) {
            // expected
        }
        assertEquals(2, runs)
    }

    @Test
    fun withoutAnAddressTheFirstScreenIsPointedTo() {
        try {
            runOnHeadUnit<String>({ null }, { "10.0.0.2" }) { "never" }
            fail("should have failed")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("DETECT CAR"))
        }
    }

    @Test
    fun internetFailuresAreNeverWordedAsHeadUnitProblems() {
        // a timeout or refusal toward GitHub is "no internet", not "Timed out talking to the head unit"
        assertTrue(Online.offline(java.net.SocketTimeoutException("failed to connect to github.com after 8000ms")))
        assertTrue(Online.offline(java.net.UnknownHostException("api.github.com")))
        assertTrue(Online.offline(java.net.ConnectException("Connection refused")))
        assertTrue(Online.offline(java.net.SocketException("Network is unreachable")))
        assertTrue(Online.offline(IOException("No network with internet")))
        assertTrue(Online.offline(RuntimeException("wrapped", javax.net.ssl.SSLException("Connection reset"))))
        assertEquals(Online.NO_INTERNET, Online.describe(java.net.SocketTimeoutException("timed out")))
        assertEquals(Online.NO_INTERNET, Online.describe(java.net.ConnectException("Connection refused")))
        for (e in listOf<Throwable>(
            java.net.SocketTimeoutException("timed out"), java.net.ConnectException("Connection refused"),
        )) {
            assertFalse(Online.describe(e).contains("head unit", ignoreCase = true))
            assertFalse(Online.describe(e).contains("SSH", ignoreCase = true))
        }
        // GitHub answered, so its own words are shown
        assertFalse(Online.offline(IOException("GitHub answered 500")))
        assertEquals("GitHub answered 500", Online.describe(IOException("GitHub answered 500")))
        assertEquals("GitHub does not know this page (404).", Online.describe(IOException("GitHub does not know this page (404).")))
    }
}
