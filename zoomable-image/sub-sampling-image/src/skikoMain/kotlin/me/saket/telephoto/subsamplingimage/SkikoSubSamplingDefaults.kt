package me.saket.telephoto.subsamplingimage

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
}
