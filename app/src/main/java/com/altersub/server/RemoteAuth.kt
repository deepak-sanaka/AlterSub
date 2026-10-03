package com.altersub.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pairing for the phone web remote. The TV shows a 6-digit PIN only while its setup screen is open; a phone
 * that enters it gets a long random token, which every /api call must then carry. Too many wrong PINs lock
 * pairing until the screen is reopened, so the PIN can't be brute-forced from elsewhere on the network.
 *
 * Paired tokens are handed to [onTokensChanged] so they can be persisted; phones stay paired across restarts.
 */
class RemoteAuth(
    savedTokens: List<String> = emptyList(),
    private val onTokensChanged: (List<String>) -> Unit = {},
    private val random: SecureRandom = SecureRandom()
) {

    sealed interface Pairing {
        /** The TV setup screen isn't showing, so no PIN is accepted. */
        object Closed : Pairing
        data class Open(val pin: String) : Pairing
        /** Too many wrong PINs; reopening the TV screen issues a new one. */
        object Locked : Pairing
    }

    sealed interface PairResult {
        data class Paired(val token: String) : PairResult
        data class WrongPin(val attemptsLeft: Int) : PairResult
        object NotOpen : PairResult
        object Locked : PairResult
    }

    private val _pairing = MutableStateFlow<Pairing>(Pairing.Closed)
    val pairing: StateFlow<Pairing> = _pairing.asStateFlow()

    // Oldest first, so the oldest phone is dropped once MAX_TOKENS is reached
    private val tokens = ArrayList(savedTokens.takeLast(MAX_TOKENS))
    private var failedAttempts = 0

    private val _pairedCount = MutableStateFlow(tokens.size)
    val pairedCount: StateFlow<Int> = _pairedCount.asStateFlow()

    /** Shows a fresh PIN; called whenever the TV setup screen comes to the foreground. */
    @Synchronized
    fun openPairing() {
        failedAttempts = 0
        // Int.toString is always ASCII digits, unlike String.format in some locales
        _pairing.value = Pairing.Open(random.nextInt(1_000_000).toString().padStart(PIN_LENGTH, '0'))
    }

    @Synchronized
    fun closePairing() {
        _pairing.value = Pairing.Closed
    }

    @Synchronized
    fun pair(pin: String): PairResult {
        val open = when (val state = _pairing.value) {
            is Pairing.Open -> state
            Pairing.Closed -> return PairResult.NotOpen
            Pairing.Locked -> return PairResult.Locked
        }

        if (!constantTimeEquals(pin.trim(), open.pin)) {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                _pairing.value = Pairing.Locked
                return PairResult.Locked
            }
            return PairResult.WrongPin(MAX_FAILED_ATTEMPTS - failedAttempts)
        }

        val token = newToken()
        tokens += token
        while (tokens.size > MAX_TOKENS) tokens.removeAt(0)
        tokensChanged()
        return PairResult.Paired(token)
    }

    @Synchronized
    fun isAuthorized(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        return tokens.any { constantTimeEquals(token, it) }
    }

    /** Forgets every paired phone; each must enter a PIN again. */
    @Synchronized
    fun unpairAll() {
        tokens.clear()
        tokensChanged()
    }

    private fun tokensChanged() {
        _pairedCount.value = tokens.size
        onTokensChanged(tokens.toList())
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun constantTimeEquals(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    companion object {
        const val PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 5
        const val MAX_TOKENS = 8
        private const val TOKEN_BYTES = 16

        /** Request header the remote page sends its token in (NanoHTTPD lower-cases header names). */
        const val TOKEN_HEADER = "x-altersub-token"
    }
}
