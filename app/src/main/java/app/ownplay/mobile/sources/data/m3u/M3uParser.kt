package app.ownplay.mobile.sources.data.m3u

class M3uParser {
    fun parse(text: String): M3uParseResult {
        val entries = mutableListOf<M3uEntry>()
        val diagnostics = mutableListOf<M3uDiagnostic>()
        var pending: PendingExtInf? = null

        text.lineSequence().forEachIndexed { zeroBasedIndex, originalLine ->
            val lineNumber = zeroBasedIndex + 1
            val line = if (zeroBasedIndex == 0) originalLine.removePrefix("\uFEFF").trim() else originalLine.trim()
            if (line.isBlank()) return@forEachIndexed

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pending = parseExtInf(line, lineNumber, diagnostics)
                return@forEachIndexed
            }

            if (line.startsWith("#")) return@forEachIndexed

            val metadata = pending
            if (metadata == null) {
                diagnostics += M3uDiagnostic(lineNumber, "ORPHAN_STREAM_LOCATOR")
                return@forEachIndexed
            }

            pending = null
            if (line.isBlank()) {
                diagnostics += M3uDiagnostic(lineNumber, "EMPTY_STREAM_LOCATOR")
                return@forEachIndexed
            }

            entries += M3uEntry(
                displayName = metadata.displayName,
                tvgId = metadata.attributes["tvg-id"].nullIfBlank(),
                tvgName = metadata.attributes["tvg-name"].nullIfBlank(),
                logoUrl = metadata.attributes["tvg-logo"].nullIfBlank(),
                groupTitle = metadata.attributes["group-title"].nullIfBlank(),
                streamLocator = line,
                attributes = metadata.attributes,
            )
        }

        pending?.let {
            diagnostics += M3uDiagnostic(it.lineNumber, "MISSING_STREAM_LOCATOR")
        }

        return M3uParseResult(entries = entries, diagnostics = diagnostics)
    }

    private fun parseExtInf(
        line: String,
        lineNumber: Int,
        diagnostics: MutableList<M3uDiagnostic>,
    ): PendingExtInf {
        val commaIndex = findMetadataComma(line)
        val attributesPart = if (commaIndex >= 0) line.substring(0, commaIndex) else line
        val displayPart = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else ""
        if (commaIndex < 0) {
            diagnostics += M3uDiagnostic(lineNumber, "EXTINF_MISSING_COMMA")
        }

        val attributes = linkedMapOf<String, String>()
        ATTRIBUTE_REGEX.findAll(attributesPart).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val quoted = match.groupValues[2]
            val unquoted = match.groupValues[3]
            attributes[key] = if (quoted.isNotEmpty() || match.value.contains('"')) quoted else unquoted
        }

        val displayName = displayPart
            .ifBlank { attributes["tvg-name"].orEmpty() }
            .ifBlank {
                diagnostics += M3uDiagnostic(lineNumber, "EXTINF_MISSING_DISPLAY_NAME")
                "Unnamed channel"
            }

        return PendingExtInf(
            lineNumber = lineNumber,
            displayName = displayName,
            attributes = attributes,
        )
    }

    private fun findMetadataComma(line: String): Int {
        var quoted = false
        var escaped = false
        line.forEachIndexed { index, char ->
            if (escaped) {
                escaped = false
            } else {
                when (char) {
                    '\\' -> escaped = true
                    '"' -> quoted = !quoted
                    ',' -> if (!quoted) return index
                }
            }
        }
        return -1
    }

    private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private data class PendingExtInf(
        val lineNumber: Int,
        val displayName: String,
        val attributes: Map<String, String>,
    )

    private companion object {
        val ATTRIBUTE_REGEX = Regex("([A-Za-z0-9_-]+)\\s*=\\s*(?:\"([^\"]*)\"|([^\\s,]+))")
    }
}
