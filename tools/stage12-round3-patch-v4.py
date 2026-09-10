#!/usr/bin/env python3
from pathlib import Path
import runpy
import sys

# Apply the fully guarded Round 3 transform, then replace only the category
# normalization helper with an equivalent implementation that avoids fragile
# backslash escaping across the Python -> Kotlin generation boundary.
runpy.run_path(
    str(Path(__file__).with_name("stage12-round3-patch-v3.py")),
    run_name="__main__",
)

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
target = root / "app/src/main/java/app/ownplay/mobile/sources/domain/ProviderCategoryVisibility.kt"
target.write_text('''package app.ownplay.mobile.sources.domain

import java.util.Locale

object ProviderCategoryVisibility {
    private val exactUtilityLabels = setOf(
        "all",
        "all channels",
        "all live",
        "all live channels",
        "all movies",
        "all series",
        "all tv",
        "all vod",
    )

    fun isUtilityLabel(label: String): Boolean {
        val normalized = normalize(label)
        if (normalized in exactUtilityLabels) return true
        return normalized.contains("account information") ||
            normalized.contains("account info")
    }

    fun normalized(label: String): String = normalize(label)

    private fun normalize(label: String): String = label
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")
}
''')

print("Round 3 category normalization generation corrected")
