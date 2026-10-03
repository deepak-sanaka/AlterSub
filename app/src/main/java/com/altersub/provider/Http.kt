package com.altersub.provider

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * One HTTP client shared by every provider: each OkHttpClient owns its own connection pool and
 * threads, which add up on a 1GB TV.
 */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Suspends until the call completes and cancels the HTTP request if the coroutine is cancelled,
 * so a superseded search stops downloading instead of running to completion. The caller must close
 * the returned Response (use `use { }`).
 */
// resume(value, onCancellation) is the only race-free way to close a response that arrives after cancellation
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // If the coroutine was cancelled meanwhile, nobody will close the response, so close it here
            continuation.resume(response) { response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
