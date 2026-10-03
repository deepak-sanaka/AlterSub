package com.altersub.server

import com.altersub.server.RemoteAuth.PairResult
import com.altersub.server.RemoteAuth.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class RemoteAuthTest {

    private var savedTokens = emptyList<String>()
    private val auth = RemoteAuth(onTokensChanged = { savedTokens = it })

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
        assertTrue(savedTokens.isEmpty())
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
        assertEquals(1, auth.pairedCount.value)
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
    fun testTokensArePersistedAndRestored() {
        val token = auth.pairOnce()
        assertEquals(listOf(token), savedTokens)

        val restarted = RemoteAuth(savedTokens = savedTokens)

        assertTrue(restarted.isAuthorized(token))
        assertEquals(1, restarted.pairedCount.value)
    }

    @Test
    fun testOldestPhoneIsDroppedPastTheLimit() {
        val tokens = List(RemoteAuth.MAX_TOKENS + 1) { auth.pairOnce() }

        assertFalse(auth.isAuthorized(tokens.first()))
        assertTrue(tokens.drop(1).all(auth::isAuthorized))
        assertEquals(RemoteAuth.MAX_TOKENS, auth.pairedCount.value)
        assertEquals(tokens.drop(1), savedTokens)
    }

    @Test
    fun testUnpairAllRevokesEveryToken() {
        val first = auth.pairOnce()
        val second = auth.pairOnce()

        auth.unpairAll()

        assertFalse(auth.isAuthorized(first))
        assertFalse(auth.isAuthorized(second))
        assertEquals(0, auth.pairedCount.value)
        assertTrue(savedTokens.isEmpty())
    }
}
