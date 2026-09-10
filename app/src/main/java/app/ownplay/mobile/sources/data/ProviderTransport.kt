package app.ownplay.mobile.sources.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
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

internal object ProviderTransportFailurePolicy {
    fun codeFor(exception: IOException): String = when {
        exception is UnknownServiceException &&
            exception.message.orEmpty().contains("cleartext", ignoreCase = true) -> "CLEARTEXT_BLOCKED"

        exception is UnknownHostException -> "NETWORK_DNS"
        exception is SocketTimeoutException -> "NETWORK_TIMEOUT"
        exception is SSLException -> "TLS"
        exception is ConnectException -> "NETWORK_CONNECT"
        else -> "NETWORK"
    }
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
                        continuation.resume(TransportResult.Failure(ProviderTransportFailurePolicy.codeFor(e)))
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
