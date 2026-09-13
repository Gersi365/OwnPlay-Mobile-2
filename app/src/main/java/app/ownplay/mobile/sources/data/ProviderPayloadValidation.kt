package app.ownplay.mobile.sources.data

internal object ProviderPayloadValidation {
    fun m3uFailureCode(parsedEntryCount: Int, diagnosticCount: Int, resolvedEntryCount: Int): String? = when {
        parsedEntryCount == 0 && diagnosticCount > 0 -> "M3U_PARSE_CONTENT"
        parsedEntryCount > 0 && resolvedEntryCount == 0 -> "M3U_LOCATOR_CONTENT"
        else -> null
    }

    fun xtreamArrayFailureCode(rawRowCount: Int, mappedRowCount: Int): String? =
        if (rawRowCount > 0 && mappedRowCount == 0) "XTREAM_ARRAY_CONTENT" else null

    fun <T> distinctM3uByLocator(rows: List<Pair<T, String>>): List<Pair<T, String>> =
        rows.distinctBy { (_, locator) -> locator }
}
