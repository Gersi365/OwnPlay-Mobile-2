#!/usr/bin/env python3
from pathlib import Path

source_path = Path(__file__).with_name("stage12-round3-patch.py")
source = source_path.read_text()
old = '''replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    '                    message = "Choose another provider category or All.",',
    '                    message = "Choose another provider category.",',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    '                    message = "Choose another provider category or All.",',
    '                    message = "Choose another provider category.",',
)
'''
new = '''library_path = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
library_text = read(library_path)
old_category_message = '                    message = "Choose another provider category or All.",'
new_category_message = '                    message = "Choose another provider category.",'
if library_text.count(old_category_message) != 2:
    raise SystemExit(
        f"{library_path}: expected two category-empty messages, found {library_text.count(old_category_message)}"
    )
write(library_path, library_text.replace(old_category_message, new_category_message))
'''
if source.count(old) != 1:
    raise SystemExit("Expected exactly one duplicate Library category-message block in Round 3 patch")
exec(compile(source.replace(old, new), str(source_path), "exec"), {"__name__": "__main__", "__file__": str(source_path)})
