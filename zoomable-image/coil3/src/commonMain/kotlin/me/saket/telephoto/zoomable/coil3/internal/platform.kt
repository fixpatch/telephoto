package me.saket.telephoto.zoomable.coil3.internal

import androidx.compose.ui.graphics.ImageBitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineDispatcher
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import okio.Closeable
import okio.FileSystem
import okio.Path

/** Dispatcher for disk reads. */
internal expect val ioDispatcher: CoroutineDispatcher

internal expect fun BitmapImage.toPreviewImageBitmap(): ImageBitmap

/** Bitmap options to decode tiles with, so that they match the bitmap decoded by Coil. */
internal expect fun BitmapImage.imageBitmapOptions(): ImageBitmapOptions

/** An image stored in Coil's disk cache. */
internal expect fun diskCacheImageSource(
  path: Path,
  fileSystem: FileSystem,
  preview: ImageBitmap?,
  onClose: Closeable?,
): SubSamplingImageSource

/**
 * An image that was loaded from local storage, or a remote image that wasn't
 * saved to disk because of a "no-store" HTTP header. Null if it can't be read again.
 */
internal expect suspend fun localImageSourceOrNull(
  result: SuccessResult,
  imageLoader: ImageLoader,
  preview: ImageBitmap?,
): SubSamplingImageSource?

internal expect suspend fun canBeSubSampled(source: SubSamplingImageSource, context: PlatformContext): Boolean

/** Used for resolving images synchronously in screenshot tests and IDE previews. */
internal expect fun <T> runBlockingForPreview(block: suspend () -> T): T

internal fun SuccessResult.isLocalImage(): Boolean =
  dataSource == DataSource.DISK || dataSource == DataSource.MEMORY_CACHE
