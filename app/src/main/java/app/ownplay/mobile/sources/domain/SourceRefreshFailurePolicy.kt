package app.ownplay.mobile.sources.domain

object SourceRefreshFailurePolicy {
    fun present(rawCodes: String?): SourceError {
        val codes = rawCodes.orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        fun has(code: String): Boolean = codes.any { it.equals(code, ignoreCase = true) }
        fun hasPrefix(prefix: String): Boolean = codes.any { it.startsWith(prefix, ignoreCase = true) }

        return when {
            has("XTREAM_AUTH") || has("HTTP_401") || has("HTTP_403") -> SourceError(
                code = "REFRESH_AUTH",
                safeMessage = "The provider rejected these credentials. Check the username and password, then reconnect the source.",
            )

            has("CLEARTEXT_BLOCKED") -> SourceError(
                code = "REFRESH_HTTP_BLOCKED",
                safeMessage = "This HTTP provider connection was blocked by the device network policy.",
            )

            has("NETWORK_DNS") -> SourceError(
                code = "REFRESH_DNS",
                safeMessage = "The provider hostname could not be resolved. Check the server address and internet connection.",
            )

            has("NETWORK_TIMEOUT") -> SourceError(
                code = "REFRESH_TIMEOUT",
                safeMessage = "The provider did not respond in time. Check the server address and port, then try again.",
            )

            has("TLS") -> SourceError(
                code = "REFRESH_TLS",
                safeMessage = "The secure connection to the provider failed. Check its HTTPS support and certificate.",
            )

            has("NETWORK_CONNECT") || has("NETWORK") -> SourceError(
                code = "REFRESH_NETWORK",
                safeMessage = "OwnPlay could not reach the provider. Check the server address, port, internet connection, and provider availability.",
            )

            has("HTTP_404") -> SourceError(
                code = "REFRESH_HTTP_404",
                safeMessage = "The server was reached, but its Xtream API endpoint was not found. Check the base URL and port.",
            )

            has("HTTP_429") -> SourceError(
                code = "REFRESH_HTTP_429",
                safeMessage = "The provider is temporarily rate-limiting requests. Wait briefly, then refresh again.",
            )

            codes.any { code ->
                code.startsWith("HTTP_5", ignoreCase = true) && code.drop(6).toIntOrNull() != null
            } -> SourceError(
                code = "REFRESH_PROVIDER_HTTP",
                safeMessage = "The provider server returned an error. Try refreshing again later.",
            )

            has("INVALID_URL") -> SourceError(
                code = "REFRESH_INVALID_URL",
                safeMessage = "The source address is invalid. Check the server URL and port.",
            )

            has("XTREAM_JSON") || has("XTREAM_ARRAY_FORMAT") || hasPrefix("XTREAM_") -> SourceError(
                code = "REFRESH_XTREAM_FORMAT",
                safeMessage = "The server responded, but not with a compatible Xtream catalog. Check the base URL, port, username, and password.",
            )

            has("READ") -> SourceError(
                code = "REFRESH_READ",
                safeMessage = "The provider response could not be read. Try refreshing again.",
            )

            else -> SourceError(
                code = "REFRESH_FAILED",
                safeMessage = "Source refresh failed; the last known catalog was preserved.",
            )
        }
    }
}
