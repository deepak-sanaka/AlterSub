package com.altersub.server

import com.altersub.server.RemoteAuth.PairResult
import com.altersub.server.RemoteAuth.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class RemoteAuthTest {

    private var savedToken: String? = null
    private val auth = RemoteAuth(onTokenChanged = { savedToken = it })

    private fun RemoteAuth.pin() = (pairing.value as Pairing.Open).pin

    private fun wrongPin(pin: String) = if (pin == "000000") "111111" else "000000"

    private fun RemoteAuth.pairOnce(): String {
        openPairing()
        return (pair(pin()) as PairResult.Paired).token
    }

    @Test
    fun testPinIsSixAsciiDigitsWithLeadingZeros() {
        val lowRandom = object : SecureRandom() {
            override fun nextInt(bound: Int) = 42
        }
        val auth = RemoteAuth(random = lowRandom)

        auth.openPairing()

        assertEquals("000042", auth.pin())
    }

    @Test
    fun testPinsAreOnlyAcceptedWhileTheTvScreenIsOpen() {
        assertEquals(PairResult.NotOpen, auth.pair("123456"))

        auth.openPairing()
        val pin = auth.pin()
        auth.closePairing()

        assertEquals(PairResult.NotOpen, auth.pair(pin))
        assertNull(savedToken)
    }

    @Test
    fun testCorrectPinIssuesATokenThatAuthorizes() {
        auth.openPairing()

        val result = auth.pair(" ${auth.pin()} ") as PairResult.Paired

        assertTrue(result.token.matches(Regex("[0-9a-f]{32}")))
        assertTrue(auth.isAuthorized(result.token))
        assertFalse(auth.isAuthorized(result.token.dropLast(1)))
        assertFalse(auth.isAuthorized(""))
        assertFalse(auth.isAuthorized(null))
        assertTrue(auth.isPaired.value)
    }

    @Test
    fun testWrongPinsLockPairingUntilTheScreenIsReopened() {
        auth.openPairing()
        val pin = auth.pin()

        for (attemptsLeft in RemoteAuth.MAX_FAILED_ATTEMPTS - 1 downTo 1) {
            assertEquals(PairResult.WrongPin(attemptsLeft), auth.pair(wrongPin(pin)))
        }
        assertEquals(PairResult.Locked, auth.pair(wrongPin(pin)))
        assertEquals(Pairing.Locked, auth.pairing.value)

        // Even the right PIN is refused once locked
        assertEquals(PairResult.Locked, auth.pair(pin))

        auth.openPairing()
        assertTrue(auth.pair(auth.pin()) is PairResult.Paired)
    }

    @Test
    fun testASecondPhoneIsRefusedWhileOneIsPaired() {
        val first = auth.pairOnce()
        auth.openPairing()

        // Even with the right PIN, and without using up the wrong-PIN allowance
        assertEquals(PairResult.AlreadyPaired, auth.pair(auth.pin()))
        repeat(RemoteAuth.MAX_FAILED_ATTEMPTS + 1) {
            assertEquals(PairResult.AlreadyPaired, auth.pair(wrongPin(auth.pin())))
        }
        assertTrue(auth.isAuthorized(first))
    }

    @Test
    fun testTheTvCanUnpairThePhone() {
        val token = auth.pairOnce()

        auth.unpair()

        assertFalse(auth.isAuthorized(token))
        assertFalse(auth.isPaired.value)
        assertNull(savedToken)
        auth.openPairing()
        assertTrue(auth.pair(auth.pin()) is PairResult.Paired)
    }

    @Test
    fun testOnlyThePairedPhoneCanUnpairItself() {
        val token = auth.pairOnce()

        assertFalse(auth.revoke("someone-else"))
        assertFalse(auth.revoke(null))
        assertTrue(auth.isAuthorized(token))

        assertTrue(auth.revoke(token))
        assertFalse(auth.isAuthorized(token))
        assertFalse(auth.isPaired.value)
    }

    @Test
    fun testTokenIsPersistedAndRestored() {
        val token = auth.pairOnce()
        assertEquals(token, savedToken)

        val restarted = RemoteAuth(savedToken = savedToken)

        assertTrue(restarted.isAuthorized(token))
        assertTrue(restarted.isPaired.value)
        assertFalse(RemoteAuth(savedToken = "").isPaired.value)
    }
}
