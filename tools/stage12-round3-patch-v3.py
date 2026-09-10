#!/usr/bin/env python3
from pathlib import Path

source_path = Path(__file__).with_name("stage12-round3-patch.py")
source = source_path.read_text()

duplicate_messages = '''replace_once(
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
message_fix = '''library_path = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
library_text = read(library_path)
old_category_message = '                    message = "Choose another provider category or All.",'
new_category_message = '                    message = "Choose another provider category.",'
if library_text.count(old_category_message) != 2:
    raise SystemExit(
        f"{library_path}: expected two category-empty messages, found {library_text.count(old_category_message)}"
    )
write(library_path, library_text.replace(old_category_message, new_category_message))
'''
if source.count(duplicate_messages) != 1:
    raise SystemExit("Expected exactly one duplicate Library category-message block")
source = source.replace(duplicate_messages, message_fix)

wrong_snapshot = '''    activeTarget: VideoTarget = VideoTarget.NONE,
    errorCode: Int? = null,
)'''
correct_snapshot = '''    val activeTarget: VideoTarget = VideoTarget.NONE,
    val errorCode: Int? = null,
)'''
if source.count(wrong_snapshot) != 1:
    raise SystemExit("Expected exactly one PlaybackSnapshot old-target typo")
source = source.replace(wrong_snapshot, correct_snapshot)

wrong_snapshot_new = '''    activeTarget: VideoTarget = VideoTarget.NONE,
    audioTrackPresent: Boolean? = null,
    audioTrackSupported: Boolean? = null,
    audioTrackSelected: Boolean? = null,
    errorCode: Int? = null,
)'''
correct_snapshot_new = '''    val activeTarget: VideoTarget = VideoTarget.NONE,
    val audioTrackPresent: Boolean? = null,
    val audioTrackSupported: Boolean? = null,
    val audioTrackSelected: Boolean? = null,
    val errorCode: Int? = null,
)'''
if source.count(wrong_snapshot_new) != 1:
    raise SystemExit("Expected exactly one PlaybackSnapshot replacement typo")
source = source.replace(wrong_snapshot_new, correct_snapshot_new)

exec(compile(source, str(source_path), "exec"), {"__name__": "__main__", "__file__": str(source_path)})
