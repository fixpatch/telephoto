package me.saket.telephoto.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import okio.Closeable

/**
 * Image to display with [SubSamplingImage]. Can be one of:
 *
 * * [SubSamplingImageSource.file]
 * * [SubSamplingImageSource.asset]
 * * [SubSamplingImageSource.resource]
 * * [SubSamplingImageSource.contentUri]
 * * [SubSamplingImageSource.rawSource]
 */
interface SubSamplingImageSource : Closeable {
  /**
   * A preview that can be displayed immediately while the bitmap tiles
   * are loaded, which can be slightly slow depending on the file size.
   */
  val preview: ImageBitmap?

  companion object {}

  suspend fun decoder(): ImageRegionDecoder.Factory

  /** Called when the image is no longer visible. */
  override fun close() = Unit
}

