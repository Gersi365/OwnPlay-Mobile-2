from pathlib import Path

path = Path("app/src/main/java/app/ownplay/mobile/sources/domain/ProviderCategoryVisibility.kt")
text = path.read_text()
start = text.index("        val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFKD)")
end = text.index("\n\n        return decomposed", start)
replacement = '''        val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFKD)
            .filterNot { character ->
                when (Character.getType(character)) {
                    Character.NON_SPACING_MARK.toInt(),
                    Character.COMBINING_SPACING_MARK.toInt(),
                    Character.ENCLOSING_MARK.toInt(),
                    Character.FORMAT.toInt() -> true
                    else -> false
                }
            }
'''.rstrip()
path.write_text(text[:start] + replacement + text[end:])
