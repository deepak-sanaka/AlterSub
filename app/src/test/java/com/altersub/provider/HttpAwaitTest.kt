package com.altersub.provider

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HttpAwaitTest {

    /** A Call that never completes until told to, so cancellation can be observed. */
    private class PendingCall : Call {
        private val request = Request.Builder().url("https://example.invalid/").build()
        var callback: Callback? = null
        @Volatile var cancelled = false

        override fun request() = request
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = callback != null
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = this

        fun respond(body: String) {
            val response = Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody())
                .build()
            callback!!.onResponse(this, response)
        }
    }

    // runBlocking is single-threaded: UNDISPATCHED runs await() up to its suspension point right away,
    // so the callback is registered before the test continues (no busy-waiting on the only thread)
    @Test
    fun testAwaitReturnsTheResponse() = runBlocking {
        val call = PendingCall()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { call.await().use { it.body!!.string() } }
        call.respond("subtitles")
        assertEquals("subtitles", pending.await())
    }

    @Test
    fun testCancellingTheCoroutineCancelsTheHttpCall() = runBlocking {
        val call = PendingCall()
        try {
            withTimeout(50) { call.await() }
        } catch (_: Exception) {
        }
        assertTrue("Cancelling the coroutine must cancel the in-flight request", call.cancelled)
    }

    @Test(expected = IOException::class)
    fun testNetworkFailuresSurfaceAsExceptions(): Unit = runBlocking {
        val call = PendingCall()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { call.await() }
        call.callback!!.onFailure(call, IOException("connection reset"))
        pending.await()
    }
}
