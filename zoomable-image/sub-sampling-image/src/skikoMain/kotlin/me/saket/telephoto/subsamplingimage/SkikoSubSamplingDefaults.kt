package me.saket.telephoto.subsamplingimage

import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.SkikoImageRegionDecoder

/** App-wide defaults for [SubSamplingImage] on platforms that decode with Skia (desktop, iOS and web). */
object SkikoSubSamplingDefaults {
  /**
   * Upper limit for the decoded pixels that are kept in memory for one image, used by
   * [SubSamplingImageSource.Companion.file] and [SubSamplingImageSource.Companion.bytes] when
   * no explicit limit is given (including the sources created by the Coil integration).
   *
   * Skia can't decode a partial region of an image, so a zoomed-in image is decoded as a whole
   * at the finest resolution that fits this limit. Images larger than this are drawn upscaled
   * from a coarser resolution when zoomed in all the way. Changes apply to images opened afterwards.
   */
  var maxDecodedPixels: Long = SkikoImageRegionDecoder.DefaultMaxDecodedPixels

  /**
   * Decoders that are tried, in order, before Skia's own for images read by
   * [SubSamplingImageSource.Companion.file] and [SubSamplingImageSource.Companion.bytes].
   *
   * Skia can't decode a partial region of an image. An app can register a decoder that can
   * (for example one built on libjpeg-turbo / libpng) so that only visible tiles are decoded,
   * like Android's `BitmapRegionDecoder`. A provider returns null for images it can't handle,
   * which then fall through to the next provider and finally to Skia.
   */
  var regionDecoderProviders: List<SkikoRegionDecoderProvider> = emptyList()
}

/** See [SkikoSubSamplingDefaults.regionDecoderProviders]. */
fun interface SkikoRegionDecoderProvider {
  /**
   * Creates a decoder for the encoded image in [bytes], or returns null to leave it to the
   * next provider. Throwing is treated the same as returning null.
   */
  suspend fun create(bytes: ByteArray, params: ImageRegionDecoder.FactoryParams): ImageRegionDecoder?
}
