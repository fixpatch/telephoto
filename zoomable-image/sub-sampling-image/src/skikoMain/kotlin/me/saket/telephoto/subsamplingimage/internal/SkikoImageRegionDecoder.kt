package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.skia.Image as SkiaImage

/**
 * [ImageRegionDecoder] for platforms that render with Skia (desktop, iOS and web).
 *
 * Skia's codecs, as exposed by skiko, can't decode a partial region of an image. They can
 * however decode a whole image at a smaller size for some formats (measured with skiko 0.150):
 *
 * - JPEG: 1/2, 1/4 and 1/8 (DCT scaling)
 * - WebP: any size
 * - PNG, GIF, BMP, etc.: full size only
 *
 * This decoder therefore keeps a "level": the whole image decoded at `1/levelScale` of its size,
 * and cuts tiles out of it. A level is decoded at the smallest size its codec supports that is
 * still at least as detailed as the requested sample size, and then halved down to the exact
 * scale, so that a JPEG or WebP never has to be expanded to its full size to show a zoomed-out
 * region.
 *
 * Retained memory is bounded by [maxDecodedPixels]: when a level would be larger than that
 * (for example when zooming into a 10000 x 10000 photo), a coarser level is used and its tiles
 * are drawn upscaled. Formats that can't be decoded at a smaller size still need to be decoded
 * at their full size once while creating a level, but only the downscaled level is kept.
 *
 * EXIF orientations are applied when a level is created, so tiles are always in the correct
 * orientation and [imageSize] is the oriented size.
 *
 * All work happens on a single-threaded dispatcher, which serializes access to the codec
 * and the cached levels.
 */
internal class SkikoImageRegionDecoder private constructor(
  private val data: Data,
  private val codec: Codec,
  private val maxDecodedPixels: Long,
  private val dispatcher: CoroutineDispatcher,
) : ImageRegionDecoder {

  private val origin: EncodedOrigin = codec.encodedOrigin
  private val rawWidth: Int = codec.imageInfo.width
  private val rawHeight: Int = codec.imageInfo.height
  private val supportedCodecScales: List<Int> = when (codec.encodedImageFormat) {
    EncodedImageFormat.JPEG -> listOf(1, 2, 4, 8)
    // WebP can be decoded at any size. Powers of two are all that levels need.
    EncodedImageFormat.WEBP -> generateSequence(1) { it * 2 }.takeWhile { it <= MaxLevelScale }.toList()
    else -> listOf(1)
  }

  override val imageSize: IntSize =
    if (origin.swapsWidthHeight()) IntSize(rawHeight, rawWidth) else IntSize(rawWidth, rawHeight)

  /** Level scale -> the whole image decoded (and oriented) at 1/scale of its size. */
  private val levels = LinkedHashMap<Int, SkiaImage>()
  private var isClosed = false

  override suspend fun decodeRegion(region: IntRect, sampleSize: Int): ImageRegionDecoder.DecodeResult {
    return withContext(dispatcher) {
      check(!isClosed) { "Decoder was closed" }
      val level = level(scale = levelScaleFor(sampleSize))
      val tile = cut(level, region)
      ImageRegionDecoder.DecodeResult(
        painter = BitmapPainter(tile.toComposeImageBitmap()),
        hasUltraHdrContent = false,
      ).also {
        tile.close()
      }
    }
  }

  override fun close() {
    // Levels are released on the decoding dispatcher so that an in-flight
    // decodeRegion() never reads a level that has already been freed.
    CoroutineScope(dispatcher).launch {
      isClosed = true
      levels.values.forEach { it.close() }
      levels.clear()
      codec.close()
      data.close()
    }
  }

  /**
   * The level to cut tiles from. Prefers the requested [sampleSize], but falls back
   * to coarser levels when the requested one wouldn't fit in [maxDecodedPixels].
   */
  internal fun levelScaleFor(sampleSize: Int): Int {
    var scale = highestPowerOfTwoAtMost(sampleSize.coerceAtLeast(1))
    while (scale < MaxLevelScale && pixelCount(scale) > maxDecodedPixels) {
      scale *= 2
    }
    return scale
  }

  private fun pixelCount(scale: Int): Long =
    ceilDiv(rawWidth, scale).toLong() * ceilDiv(rawHeight, scale).toLong()

  private fun level(scale: Int): SkiaImage {
    levels.remove(scale)?.let { cached ->
      levels[scale] = cached  // Move to the end (most recently used).
      return cached
    }
    val created = createLevel(scale)
    levels[scale] = created
    // Keep at most two levels: the one being zoomed into and the previous one,
    // whose tiles are still visible while the new ones are loading.
    while (levels.size > 2) {
      val eldest = levels.keys.first()
      levels.remove(eldest)!!.close()
    }
    return created
  }

  private fun createLevel(scale: Int): SkiaImage {
    // Decode as small as the codec allows without going below the requested detail.
    var image = decodeWithCodec(codecScale = supportedCodecScales.last { it <= scale })

    // Halve down to the exact scale. Halving with linear filtering averages
    // 2x2 pixels, so detail isn't lost to aliasing even for large reductions.
    val targetWidth = ceilDiv(rawWidth, scale)
    val targetHeight = ceilDiv(rawHeight, scale)
    while (image.width > targetWidth || image.height > targetHeight) {
      val halved = resize(
        image = image,
        width = max(targetWidth, ceilDiv(image.width, 2)),
        height = max(targetHeight, ceilDiv(image.height, 2)),
      )
      image.close()
      image = halved
    }
    return if (origin == EncodedOrigin.TOP_LEFT) image else orient(image).also { image.close() }
  }

  private fun decodeWithCodec(codecScale: Int): SkiaImage {
    val bitmap = Bitmap()
    try {
      val info = codec.imageInfo
      val decoded = runCatching {
        // A size different from the image's own size is a request to scale.
        bitmap.allocPixels(info.withWidthHeight(ceilDiv(rawWidth, codecScale), ceilDiv(rawHeight, codecScale)))
        codec.readPixels(bitmap)
      }
      if (decoded.isFailure && codecScale != 1) {
        // The codec couldn't scale to this size. Decode the full image instead.
        bitmap.close()
        return decodeWithCodec(codecScale = 1)
      }
      decoded.getOrThrow()
      bitmap.setImmutable()
      return SkiaImage.makeFromBitmap(bitmap)
    } finally {
      // makeFromBitmap() shares the pixels of an immutable bitmap. Closing
      // the Kotlin wrapper only drops this reference to them.
      bitmap.close()
    }
  }

  /** Draws [image] (in its stored orientation) upright, as described by its EXIF orientation. */
  private fun orient(image: SkiaImage): SkiaImage {
    val width = if (origin.swapsWidthHeight()) image.height else image.width
    val height = if (origin.swapsWidthHeight()) image.width else image.height
    val surface = Surface.makeRasterN32Premul(width, height)
    try {
      surface.canvas.concat(origin.toMatrix(width, height))
      surface.canvas.drawImage(image, 0f, 0f)
      return surface.makeImageSnapshot()
    } finally {
      surface.close()
    }
  }

  /** Copies [region] (in [imageSize] coordinates) out of [level]. */
  private fun cut(level: SkiaImage, region: IntRect): SkiaImage {
    val scaleX = level.width / imageSize.width.toFloat()
    val scaleY = level.height / imageSize.height.toFloat()
    val src = Rect(
      left = region.left * scaleX,
      top = region.top * scaleY,
      right = region.right * scaleX,
      bottom = region.bottom * scaleY,
    )
    return resize(level, src, width = max(1, src.width.roundToInt()), height = max(1, src.height.roundToInt()))
  }

  private fun resize(
    image: SkiaImage,
    width: Int,
    height: Int,
  ): SkiaImage = resize(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), width, height)

  private fun resize(
    image: SkiaImage,
    src: Rect,
    width: Int,
    height: Int,
  ): SkiaImage {
    val surface = Surface.makeRasterN32Premul(width, height)
    try {
      surface.canvas.drawImageRect(
        image,
        src,
        Rect.makeWH(width.toFloat(), height.toFloat()),
        SamplingMode.LINEAR,
        null,
        true,
      )
      return surface.makeImageSnapshot()
    } finally {
      surface.close()
    }
  }

  companion object {
    /**
     * Default upper limit for the pixels of a decoded level: 4096 x 4096,
     * or 64 MiB at 4 bytes per pixel.
     */
    const val DefaultMaxDecodedPixels: Long = 4096L * 4096L

    /** Coarsest level. Telephoto's base tile never asks for anything smaller than the viewport. */
    private const val MaxLevelScale = 1 shl 10

    @OptIn(ExperimentalCoroutinesApi::class)
    fun Factory(
      bytes: suspend () -> ByteArray,
      maxDecodedPixels: Long,
    ) = ImageRegionDecoder.Factory {
      val dispatcher = Dispatchers.Default.limitedParallelism(1)
      withContext(dispatcher) {
        val data = Data.makeFromBytes(bytes())
        val codec = try {
          Codec.makeFromData(data)
        } catch (e: Throwable) {
          data.close()
          throw okio.IOException("Image format isn't supported by Skia", e)
        }
        SkikoImageRegionDecoder(
          data = data,
          codec = codec,
          maxDecodedPixels = maxDecodedPixels,
          dispatcher = dispatcher,
        )
      }
    }
  }
}

private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

private fun highestPowerOfTwoAtMost(value: Int): Int {
  var result = 1
  while (result * 2 <= value) result *= 2
  return result
}
