package me.saket.telephoto.zoomable.coil3.internal

import androidx.compose.ui.graphics.ImageBitmap
import coil3.ImageLoader
import coil3.request.SuccessResult
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource

// Browsers have no local file system that Coil could have read the image from.
internal actual suspend fun localImageSourceOrNull(
  result: SuccessResult,
  imageLoader: ImageLoader,
  preview: ImageBitmap?,
): SubSamplingImageSource? = null

internal actual fun <T> runBlockingForPreview(block: suspend () -> T): T {
  throw UnsupportedOperationException("Synchronous previews aren't supported on the web")
}
