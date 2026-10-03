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
 * Only one phone can be paired at a time. While it is, further pairing is refused, so nobody who glimpses
 * the TV can silently take over; the TV or the paired phone itself must unpair first. The token is handed
 * to [onTokenChanged] so it can be persisted, and the phone stays paired across restarts.
 */
class RemoteAuth(
    savedToken: String? = null,
    private val onTokenChanged: (String?) -> Unit = {},
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
        /** Another phone is already paired; it must be unpaired first. */
        object AlreadyPaired : PairResult
    }

    private val _pairing = MutableStateFlow<Pairing>(Pairing.Closed)
    val pairing: StateFlow<Pairing> = _pairing.asStateFlow()

    private var token: String? = savedToken?.takeIf { it.isNotEmpty() }
    private var failedAttempts = 0

    private val _isPaired = MutableStateFlow(token != null)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

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
        // Checked before the PIN, so guesses against a paired TV don't count towards (or reveal) anything
        if (token != null) return PairResult.AlreadyPaired

        if (!constantTimeEquals(pin.trim(), open.pin)) {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                _pairing.value = Pairing.Locked
                return PairResult.Locked
            }
            return PairResult.WrongPin(MAX_FAILED_ATTEMPTS - failedAttempts)
        }

        val newToken = newToken()
        setToken(newToken)
        return PairResult.Paired(newToken)
    }

    @Synchronized
    fun isAuthorized(candidate: String?): Boolean {
        val current = token ?: return false
        return !candidate.isNullOrEmpty() && constantTimeEquals(candidate, current)
    }

    /** Unpairs from the TV side, e.g. when the paired phone is lost or its browser data was cleared. */
    @Synchronized
    fun unpair() = setToken(null)

    /** Unpairs from the phone side; only the phone holding [candidate] can do this. */
    @Synchronized
    fun revoke(candidate: String?): Boolean {
        if (!isAuthorized(candidate)) return false
        setToken(null)
        return true
    }

    private fun setToken(value: String?) {
        token = value
        _isPaired.value = value != null
        onTokenChanged(value)
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
        private const val TOKEN_BYTES = 16

        /** Request header the remote page sends its token in (NanoHTTPD lower-cases header names). */
        const val TOKEN_HEADER = "x-altersub-token"
    }
}
