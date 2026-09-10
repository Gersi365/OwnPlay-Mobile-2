package app.ownplay.mobile.sources.data.m3u

data class M3uEntry(
    val displayName: String,
    val tvgId: String?,
    val tvgName: String?,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamLocator: String,
    val attributes: Map<String, String>,
)

data class M3uDiagnostic(
    val lineNumber: Int,
    val code: String,
)

data class M3uParseResult(
    val entries: List<M3uEntry>,
    val diagnostics: List<M3uDiagnostic>,
)

sealed interface M3uResult<out T> {
    data class Success<T>(val value: T) : M3uResult<T>
    data class Failure(val code: String) : M3uResult<Nothing>
}
