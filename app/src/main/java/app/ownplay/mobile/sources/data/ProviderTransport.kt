package app.ownplay.mobile.sources.data

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume

sealed interface TransportResult {
    data class Success(val body: String) : TransportResult
    data class Failure(val code: String) : TransportResult
}

class ProviderHttpTransport(
    private val client: OkHttpClient,
) {
    suspend fun getText(url: String): TransportResult {
        val request = try {
            Request.Builder().url(url).get().build()
        } catch (_: IllegalArgumentException) {
            return TransportResult.Failure("INVALID_URL")
        }

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(TransportResult.Failure("NETWORK"))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        if (!response.isSuccessful) {
                            continuation.resume(TransportResult.Failure("HTTP_${response.code}"))
                            return
                        }
                        val body = try {
                            response.body.string()
                        } catch (_: IOException) {
                            continuation.resume(TransportResult.Failure("READ"))
                            return
                        }
                        continuation.resume(TransportResult.Success(body))
                    }
                }
            })
        }
    }
}
