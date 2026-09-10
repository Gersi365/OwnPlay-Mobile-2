package app.ownplay.mobile.sources.domain

enum class SourceType {
    XTREAM,
    M3U,
}

data class Source(
    val sourceId: String,
    val displayName: String,
    val type: SourceType,
    val baseLocator: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

sealed interface SourceCredential {
    class Xtream(
        val username: String,
        val password: String,
    ) : SourceCredential {
        override fun toString(): String = "Xtream(username=<redacted>, password=<redacted>)"
    }

    class M3uRemoteLocator(
        val locator: String,
    ) : SourceCredential {
        override fun toString(): String = "M3uRemoteLocator(locator=<redacted>)"
    }
}

sealed interface NewSource {
    val displayName: String

    class Xtream(
        override val displayName: String,
        val baseUrl: String,
        val credential: SourceCredential.Xtream,
    ) : NewSource {
        override fun toString(): String =
            "NewSource.Xtream(displayName=$displayName, baseUrl=$baseUrl, credential=<redacted>)"
    }

    class M3u(
        override val displayName: String,
        val credential: SourceCredential.M3uRemoteLocator,
    ) : NewSource {
        override fun toString(): String =
            "NewSource.M3u(displayName=$displayName, credential=<redacted>)"
    }
}

sealed interface SourceConnectionUpdate {
    data class Xtream(
        val baseUrl: String? = null,
        val credential: SourceCredential.Xtream? = null,
    ) : SourceConnectionUpdate

    class M3u(
        val credential: SourceCredential.M3uRemoteLocator,
    ) : SourceConnectionUpdate {
        override fun toString(): String = "SourceConnectionUpdate.M3u(credential=<redacted>)"
    }
}

data class SourceUpdate(
    val sourceId: String,
    val displayName: String? = null,
    val enabled: Boolean? = null,
    val connection: SourceConnectionUpdate? = null,
)

enum class RefreshStatus {
    SUCCESS,
    PARTIAL,
}

data class RefreshSummary(
    val sourceId: String,
    val status: RefreshStatus,
    val generation: Long,
    val liveCategories: Int,
    val liveChannels: Int,
    val vodCategories: Int,
    val movies: Int,
    val seriesCategories: Int,
    val series: Int,
    val warnings: List<String>,
)

data class SourceError(
    val code: String,
    val safeMessage: String,
)

sealed interface SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>
    data class Failure(val error: SourceError) : SourceResult<Nothing>
}
