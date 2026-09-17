package app.ownplay.mobile.sources.data.m3u

import app.ownplay.mobile.sources.data.ProviderPayloadKind
import app.ownplay.mobile.sources.data.ProviderPayloadValidation
import app.ownplay.mobile.sources.data.ProviderPayloadValidationResult
import app.ownplay.mobile.sources.data.ProviderTransport

interface M3uClient {
    suspend fun fetch(remoteUrl: String): M3uParseResult
}

class OkHttpM3uClient(
    private val transport: ProviderTransport,
) : M3uClient {
    override suspend fun fetch(remoteUrl: String): M3uParseResult {
        val response = transport.get(remoteUrl)
        if (response.statusCode == 401 || response.statusCode == 403) {
            throw M3uClientException(M3uClientFailureCategory.AUTHENTICATION)
        }
        if (response.statusCode !in 200..299) {
            throw M3uClientException(M3uClientFailureCategory.PROVIDER)
        }

        when (
            ProviderPayloadValidation.validate(
                body = response.body,
                kind = ProviderPayloadKind.M3U,
            )
        ) {
            ProviderPayloadValidationResult.Valid -> Unit
            is ProviderPayloadValidationResult.Invalid -> {
                throw M3uClientException(M3uClientFailureCategory.INVALID_PAYLOAD)
            }
        }

        return M3uParser.parse(response.body)
    }
}

class M3uClientException(
    val category: M3uClientFailureCategory,
) : Exception(category.name)

enum class M3uClientFailureCategory {
    AUTHENTICATION,
    PROVIDER,
    INVALID_PAYLOAD,
}
