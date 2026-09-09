package app.ownplay.mobile.sources.data.m3u

import app.ownplay.mobile.sources.data.ProviderHttpTransport
import app.ownplay.mobile.sources.data.TransportResult

class OkHttpM3uClient(
    private val transport: ProviderHttpTransport,
) : M3uClient {
    override suspend fun fetch(remoteLocator: String): M3uResult<String> = when (val result = transport.getText(remoteLocator)) {
        is TransportResult.Failure -> M3uResult.Failure(result.code)
        is TransportResult.Success -> M3uResult.Success(result.body)
    }
}
