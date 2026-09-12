from pathlib import Path

path = Path("app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt")
text = path.read_text()
old = '''                        prominent = true,
                        if (catalog.continueWatching.isEmpty()) {'''
new = '''                        prominent = true,
                    ) {
                        if (catalog.continueWatching.isEmpty()) {'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected exactly one malformed Continue Watching section anchor, found {count}")
path.write_text(text.replace(old, new, 1))
