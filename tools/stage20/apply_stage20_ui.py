from pathlib import Path
import shutil
import sys

staging = Path(sys.argv[1])


def replace_once(path_str: str, old: str, new: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path_str}: expected 1 occurrence, found {count}")
    path.write_text(text.replace(old, new, 1))


def remove_once(path_str: str, block: str) -> None:
    replace_once(path_str, block, "")


library = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
live = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"

library_before = Path(library).read_text()
library_marker = "@Composable\nprivate fun LibraryFullscreenPlayer"
library_suffix = library_before[library_before.index(library_marker):]

live_before = Path(live).read_text()
live_marker = "@Composable\nprivate fun FullscreenLive"
live_suffix = live_before[live_before.index(live_marker):]

shutil.copyfile(
    staging / "RemoteImageLoader.kt",
    "app/src/main/java/app/ownplay/mobile/design/RemoteImageLoader.kt",
)

for import_line in (
    "import android.graphics.BitmapFactory\n",
    "import androidx.compose.ui.graphics.asImageBitmap\n",
    "import java.io.ByteArrayOutputStream\n",
    "import java.net.HttpURLConnection\n",
    "import java.net.URL\n",
    "import kotlinx.coroutines.Dispatchers\n",
    "import kotlinx.coroutines.withContext\n",
):
    remove_once(library, import_line)

replace_once(
    library,
    "import app.ownplay.mobile.design.OwnPlayColors\n",
    "import app.ownplay.mobile.design.OwnPlayColors\n"
    "import app.ownplay.mobile.design.OwnPlayRemoteImageLoader\n"
    "import app.ownplay.mobile.design.RemoteImageProfile\n",
)
replace_once(
    library,
    """    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = locator) {
        value = locator?.takeIf { it.isNotBlank() }?.let { loadLibraryArtwork(it) }
    }
""",
    """    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = locator) {
        value = OwnPlayRemoteImageLoader.load(locator, RemoteImageProfile.ARTWORK)
    }
""",
)
remove_once(
    library,
    """private suspend fun loadLibraryArtwork(locator: String) = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(locator).openConnection() as? HttpURLConnection ?: return@runCatching null
        connection.connectTimeout = 4_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > MAX_LIBRARY_ARTWORK_BYTES) return@runCatching null
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private const val MAX_LIBRARY_ARTWORK_BYTES = 4 * 1024 * 1024

""",
)

for import_line in (
    "import android.graphics.BitmapFactory\n",
    "import androidx.compose.ui.graphics.asImageBitmap\n",
    "import java.io.ByteArrayOutputStream\n",
    "import java.net.HttpURLConnection\n",
    "import java.net.URL\n",
    "import kotlinx.coroutines.Dispatchers\n",
    "import kotlinx.coroutines.withContext\n",
):
    remove_once(live, import_line)

replace_once(
    live,
    "import app.ownplay.mobile.design.OwnPlayColors\n",
    "import app.ownplay.mobile.design.OwnPlayColors\n"
    "import app.ownplay.mobile.design.OwnPlayRemoteImageLoader\n"
    "import app.ownplay.mobile.design.RemoteImageProfile\n",
)
replace_once(
    live,
    """    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = channel.logoUrl) {
        value = channel.logoUrl?.takeIf { it.isNotBlank() }?.let { loadChannelLogo(it) }
    }
""",
    """    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = channel.logoUrl) {
        value = OwnPlayRemoteImageLoader.load(channel.logoUrl, RemoteImageProfile.LOGO)
    }
""",
)
remove_once(
    live,
    """private suspend fun loadChannelLogo(locator: String) = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(locator).openConnection() as? HttpURLConnection ?: return@runCatching null
        connection.connectTimeout = 4_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > MAX_CHANNEL_LOGO_BYTES) return@runCatching null
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private const val MAX_CHANNEL_LOGO_BYTES = 2 * 1024 * 1024

""",
)

library_after = Path(library).read_text()
if library_after[library_after.index(library_marker):] != library_suffix:
    raise SystemExit("Library fullscreen player suffix changed unexpectedly")

live_after = Path(live).read_text()
if live_after[live_after.index(live_marker):] != live_suffix:
    raise SystemExit("Live fullscreen implementation changed unexpectedly")

print("Stage 20 UI/image edits applied; fullscreen implementations preserved.")
