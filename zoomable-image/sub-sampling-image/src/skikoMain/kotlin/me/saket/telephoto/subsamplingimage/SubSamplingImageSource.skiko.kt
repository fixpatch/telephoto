package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.SkikoImageRegionDecoder
import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * An image stored on a file system. This can be used with image loading
 * libraries that store cached images on disk, such as Coil's disk cache.
 *
 * Skia can't read a partial region of a file, so the encoded file is read into memory once
 * when the image is displayed. Decoded pixels are bounded by [maxDecodedPixels]. See
 * [SkikoImageRegionDecoder] for how tiles are produced.
 *
 * @param preview See [SubSamplingImageSource.preview].
 * @param onClose Called when the image is no longer visible. This is useful for files
 *                stored in, say, an LRU cache that is capable of locking open files to
 *                prevent them from getting discarded.
 * @param maxDecodedPixels Upper limit for the decoded pixels kept in memory. Zooming in beyond
 *                         this resolution draws the closest available resolution upscaled.
 *
 * @return The returned value is stable and does not need to be remembered.
 */
@Stable
fun SubSamplingImageSource.Companion.file(
  path: Path,
  fileSystem: FileSystem,
  preview: ImageBitmap? = null,
  onClose: Closeable? = null,
  maxDecodedPixels: Long = SkikoImageRegionDecoder.DefaultMaxDecodedPixels,
): SubSamplingImageSource = SkikoFileImageSource(path, fileSystem, preview, onClose, maxDecodedPixels)

/**
 * An encoded image (JPEG, PNG, WebP, etc.) that is already in memory.
 *
 * Unlike [SubSamplingImageSource.file], the returned value is not stable
 * across recompositions and should be remembered by the caller.
 *
 * @param preview See [SubSamplingImageSource.preview].
 * @param onClose Called when the image is no longer visible.
 * @param maxDecodedPixels See [SubSamplingImageSource.Companion.file].
 */
fun SubSamplingImageSource.Companion.bytes(
  bytes: ByteArray,
  preview: ImageBitmap? = null,
  onClose: Closeable? = null,
  maxDecodedPixels: Long = SkikoImageRegionDecoder.DefaultMaxDecodedPixels,
): SubSamplingImageSource = SkikoBytesImageSource(bytes, preview, onClose, maxDecodedPixels)

@Immutable
internal data class SkikoFileImageSource(
  val path: Path,
  val fileSystem: FileSystem,
  override val preview: ImageBitmap?,
  val onClose: Closeable?,
  val maxDecodedPixels: Long,
) : SkikoBufferedImageSource {

  override fun peek(): BufferedSource = fileSystem.source(path).buffer()

  override suspend fun decoder(): ImageRegionDecoder.Factory {
    return SkikoImageRegionDecoder.Factory(
      bytes = { fileSystem.read(path) { readByteArray() } },
      maxDecodedPixels = maxDecodedPixels,
    )
  }

  override fun close() {
    onClose?.close()
  }
}

internal class SkikoBytesImageSource(
  private val bytes: ByteArray,
  override val preview: ImageBitmap?,
  private val onClose: Closeable?,
  private val maxDecodedPixels: Long,
) : SkikoBufferedImageSource {

  override fun peek(): BufferedSource = Buffer().write(bytes)

  override suspend fun decoder(): ImageRegionDecoder.Factory {
    return SkikoImageRegionDecoder.Factory(
      bytes = { bytes },
      maxDecodedPixels = maxDecodedPixels,
    )
  }

  override fun close() {
    onClose?.close()
  }

  override fun toString(): String = "SkikoBytesImageSource(${bytes.size} bytes)"
}

internal interface SkikoBufferedImageSource : SubSamplingImageSource {
  /** Reads the encoded image from its start. Used for inspecting its format. */
  fun peek(): BufferedSource
}
