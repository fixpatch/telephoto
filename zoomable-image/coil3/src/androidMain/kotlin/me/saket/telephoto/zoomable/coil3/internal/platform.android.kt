package me.saket.telephoto.zoomable.coil3.internal

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.toAndroidUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.contentUriOrNull
import me.saket.telephoto.subsamplingimage.file
import me.saket.telephoto.subsamplingimage.util.canBeSubSampled
import me.saket.telephoto.subsamplingimage.util.exists
import okio.Closeable
import okio.FileSystem
import okio.Path
import java.io.File

internal actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

internal actual fun BitmapImage.toPreviewImageBitmap(): ImageBitmap = bitmap.asImageBitmap()

internal actual fun BitmapImage.imageBitmapOptions(): ImageBitmapOptions = ImageBitmapOptions(from = bitmap)

internal actual fun diskCacheImageSource(
  path: Path,
  fileSystem: FileSystem,
  preview: ImageBitmap?,
  onClose: Closeable?,
): SubSamplingImageSource {
  // Android's disk cache lives on the device's file system, which
  // BitmapRegionDecoder reads directly through a file descriptor.
  return SubSamplingImageSource.file(path, preview, onClose)
}

internal actual suspend fun localImageSourceOrNull(
  result: SuccessResult,
  imageLoader: ImageLoader,
  preview: ImageBitmap?,
): SubSamplingImageSource? {
  return result.request.mapRequestDataToUriOrNull(imageLoader)
    ?.let { uri -> SubSamplingImageSource.contentUriOrNull(uri, preview) }
    ?.takeIf {
      result.dataSource != DataSource.MEMORY_CACHE || it.exists(result.request.context)
    }
}

internal actual suspend fun canBeSubSampled(source: SubSamplingImageSource, context: PlatformContext): Boolean {
  return source.canBeSubSampled(context)
}

internal actual fun <T> runBlockingForPreview(block: suspend () -> T): T = runBlocking { block() }

private fun ImageRequest.mapRequestDataToUriOrNull(imageLoader: ImageLoader): Uri? {
  val dummyOptions = Options(this.context) // Good enough for mappers that only use Context.
  return when (val mapped = imageLoader.components.map(data, dummyOptions)) {
    is Uri -> mapped
    is File -> Uri.parse(mapped.path)
    is coil3.Uri -> mapped.toAndroidUri()
    else -> null
  }
}
