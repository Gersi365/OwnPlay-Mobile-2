from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    content = target.read_text()
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}")
    target.write_text(content.replace(old, new, 1))


live = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"
replace_once(
    live,
    "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.asImageBitmap\n",
    "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.ImageBitmap\nimport androidx.compose.ui.graphics.asImageBitmap\n",
)
replace_once(
    live,
    "val bitmap by produceState(initialValue = null, key1 = channel.logoUrl) {",
    "val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = channel.logoUrl) {",
)

for path in (
    "app/src/main/java/app/ownplay/mobile/feature/settings/ui/DownloadManagementScreen.kt",
    "app/src/main/java/app/ownplay/mobile/feature/settings/ui/LiveManagementScreen.kt",
):
    replace_once(path, "import androidx.compose.foundation.layout.weight\n", "")

print("Applied Stage 12 personalization/settings compile fixes.")
