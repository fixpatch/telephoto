package me.saket.telephoto.subsamplingimage.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SkikoBufferedImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.isAvif
import me.saket.telephoto.subsamplingimage.internal.isGif
import me.saket.telephoto.subsamplingimage.internal.isSvg
import okio.use

/**
 * Check whether an image source can be sub-sampled and decoded using Skia.
 * Same rules as Android: SVGs, GIFs and AVIFs are left to the image loader.
 */
suspend fun SubSamplingImageSource.canBeSubSampled(): Boolean {
  if (this !is SkikoBufferedImageSource) {
    return true
  }
  return withContext(Dispatchers.Default) {
    try {
      peek().use {
        !isSvg(it) && !isGif(it) && !isAvif(it)
      }
    } catch (_: okio.IOException) {
      // The file may have been deleted by the cache before it could be read.
      false
    }
  }
}
