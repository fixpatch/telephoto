package me.saket.telephoto.zoomable.coil3.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.request.Options
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.file
import me.saket.telephoto.subsamplingimage.util.canBeSubSampled
import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

// Dispatchers.IO isn't available on every Skia platform (web). Reading a
// disk cache snapshot only touches the journal, so Default is good enough.
internal actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.Default

internal actual fun BitmapImage.toPreviewImageBitmap(): ImageBitmap = bitmap.asComposeImageBitmap()

// Skia decodes tiles in the same N32 color type that Coil uses.
internal actual fun BitmapImage.imageBitmapOptions(): ImageBitmapOptions = ImageBitmapOptions.Default

internal actual fun diskCacheImageSource(
  path: Path,
  fileSystem: FileSystem,
  preview: ImageBitmap?,
  onClose: Closeable?,
): SubSamplingImageSource {
  return SubSamplingImageSource.file(path, fileSystem, preview, onClose)
}

internal actual suspend fun canBeSubSampled(source: SubSamplingImageSource, context: PlatformContext): Boolean {
  return source.canBeSubSampled()
}

/**
 * A local file that Coil read directly (for example, a file path or a `file://` uri).
 * [fileSystem] is null on platforms without a local file system.
 */
internal suspend fun localFileImageSourceOrNull(
  result: SuccessResult,
  imageLoader: ImageLoader,
  preview: ImageBitmap?,
  fileSystem: FileSystem?,
): SubSamplingImageSource? {
  fileSystem ?: return null
  val request = result.request
  val path = when (val mapped = imageLoader.components.map(request.data, Options(request.context))) {
    is Path -> mapped
    is coil3.Uri -> mapped.takeIf { it.scheme == null || it.scheme == "file" }?.path?.toPath()
    else -> null
  } ?: return null

  if (!path.isAbsolute) {
    return null
  }
  if (result.dataSource == DataSource.MEMORY_CACHE) {
    // The image may have been deleted since it was put in the memory cache.
    val exists = withContext(ioDispatcher) { fileSystem.metadataOrNull(path)?.size?.let { it > 0 } == true }
    if (!exists) return null
  }
  return SubSamplingImageSource.file(path, fileSystem, preview)
}
