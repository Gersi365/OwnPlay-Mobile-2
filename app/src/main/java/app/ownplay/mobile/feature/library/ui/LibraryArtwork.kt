package app.ownplay.mobile.feature.library.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.feature.library.data.LibraryArtworkDecodePolicy
import app.ownplay.mobile.feature.library.data.LibraryArtworkLoader
import app.ownplay.mobile.feature.library.data.LibraryArtworkPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryArtwork(
    url: String?,
    loader: LibraryArtworkLoader,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var image by remember(url, loader) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url, loader) {
        image = null
        val artworkUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val payload = loader.load(artworkUrl) ?: return@LaunchedEffect
        image = withContext(Dispatchers.Default) {
            decodeArtwork(payload)?.asImageBitmap()
        }
    }

    Surface(
        color = OwnPlayColors.Surface,
        modifier = if (compact) {
            modifier.size(width = 56.dp, height = 84.dp)
        } else {
            modifier.size(width = 72.dp, height = 108.dp)
        },
    ) {
        val current = image
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Artwork", color = OwnPlayColors.TextMuted)
            }
        } else {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun decodeArtwork(payload: LibraryArtworkPayload): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = LibraryArtworkDecodePolicy.sampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
        )
    }
    return BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size, options)
}
