package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.sources.domain.SourceType

internal object BackupSourceRestorePolicy {
    fun resolveBaseLocator(
        sourceType: String,
        existingBaseLocator: String,
        existingCredentialReference: String?,
        importedSafeLocator: String,
    ): String = when {
        sourceType == SourceType.XTREAM.name && existingCredentialReference != null -> existingBaseLocator
        sourceType == SourceType.M3U.name -> existingBaseLocator
        else -> importedSafeLocator
    }
}
