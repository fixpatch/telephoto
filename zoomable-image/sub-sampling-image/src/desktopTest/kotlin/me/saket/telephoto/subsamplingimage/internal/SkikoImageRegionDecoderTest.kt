package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.testing.Quadrant
import me.saket.telephoto.subsamplingimage.testing.encodeQuadrants
import me.saket.telephoto.subsamplingimage.testing.render
import me.saket.telephoto.subsamplingimage.testing.withExifOrientation
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class SkikoImageRegionDecoderTest {

  @Test fun jpeg_full_resolution_tile() = runBlocking {
    val decoder = decoder(encodeQuadrants(4000, 3000, EncodedImageFormat.JPEG))
    assertEquals(IntSize(4000, 3000), decoder.imageSize)

    val tile = decoder.decodeRegion(IntRect(0, 0, 1000, 1000), sampleSize = 1).render()
    assertEquals(IntSize(1000, 1000), tile.size)
    assertEquals(Quadrant.TopLeft, tile.dominantQuadrantAt(500, 500))

    val bottomRight = decoder.decodeRegion(IntRect(3000, 2000, 4000, 3000), sampleSize = 1).render()
    assertEquals(Quadrant.BottomRight, bottomRight.dominantQuadrantAt(500, 500))
    decoder.close()
  }

  @Test fun jpeg_sampled_tile_is_smaller() = runBlocking {
    val decoder = decoder(encodeQuadrants(4000, 3000, EncodedImageFormat.JPEG))
    val tile = decoder.decodeRegion(IntRect(0, 0, 4000, 3000), sampleSize = 4).render()
    assertEquals(IntSize(1000, 750), tile.size)
    assertEquals(Quadrant.TopLeft, tile.dominantQuadrantAt(250, 180))
    assertEquals(Quadrant.TopRight, tile.dominantQuadrantAt(750, 180))
    assertEquals(Quadrant.BottomLeft, tile.dominantQuadrantAt(250, 560))
    assertEquals(Quadrant.BottomRight, tile.dominantQuadrantAt(750, 560))
    decoder.close()
  }

  @Test fun webp_sampled_tile() = runBlocking {
    val decoder = decoder(encodeQuadrants(3000, 2000, EncodedImageFormat.WEBP))
    val tile = decoder.decodeRegion(IntRect(1500, 0, 3000, 1000), sampleSize = 2).render()
    assertEquals(IntSize(750, 500), tile.size)
    assertEquals(Quadrant.TopRight, tile.dominantQuadrantAt(375, 250))
    decoder.close()
  }

  @Test fun memory_budget_selects_a_coarser_level() = runBlocking {
    // 4000 x 3000 = 12M pixels. With a 1M pixel budget, a 1/4 level (1000 x 750) is the finest allowed.
    val decoder = decoder(encodeQuadrants(4000, 3000, EncodedImageFormat.PNG), maxDecodedPixels = 1_000_000)
    assertEquals(4, (decoder as SkikoImageRegionDecoder).levelScaleFor(sampleSize = 1))

    val tile = decoder.decodeRegion(IntRect(0, 0, 2000, 1500), sampleSize = 1).render()
    assertEquals(IntSize(500, 375), tile.size)
    assertEquals(Quadrant.TopLeft, tile.dominantQuadrantAt(250, 180))
    decoder.close()
  }

  @Test fun exif_rotation_by_90_degrees() = runBlocking {
    // Orientation 6: the stored image must be rotated 90° clockwise to be displayed.
    val jpeg = encodeQuadrants(400, 300, EncodedImageFormat.JPEG).withExifOrientation(6)
    val decoder = decoder(jpeg)
    assertEquals(IntSize(300, 400), decoder.imageSize)

    val tile = decoder.decodeRegion(IntRect(0, 0, 300, 400), sampleSize = 1).render()
    assertEquals(IntSize(300, 400), tile.size)
    // After a clockwise rotation, the stored bottom-left corner becomes the top-left corner.
    assertEquals(Quadrant.BottomLeft, tile.dominantQuadrantAt(75, 100))
    assertEquals(Quadrant.TopLeft, tile.dominantQuadrantAt(225, 100))
    assertEquals(Quadrant.BottomRight, tile.dominantQuadrantAt(75, 300))
    assertEquals(Quadrant.TopRight, tile.dominantQuadrantAt(225, 300))
    decoder.close()
  }

  @Test fun exif_rotation_by_180_degrees() = runBlocking {
    val decoder = decoder(encodeQuadrants(400, 300, EncodedImageFormat.JPEG).withExifOrientation(3))
    assertEquals(IntSize(400, 300), decoder.imageSize)

    val tile = decoder.decodeRegion(IntRect(0, 0, 200, 150), sampleSize = 1).render()
    assertEquals(Quadrant.BottomRight, tile.dominantQuadrantAt(100, 75))
    decoder.close()
  }

  @Test fun exif_horizontal_flip() = runBlocking {
    val decoder = decoder(encodeQuadrants(400, 300, EncodedImageFormat.JPEG).withExifOrientation(2))
    val tile = decoder.decodeRegion(IntRect(0, 0, 200, 150), sampleSize = 1).render()
    assertEquals(Quadrant.TopRight, tile.dominantQuadrantAt(100, 75))
    decoder.close()
  }

  private suspend fun decoder(
    bytes: ByteArray,
    maxDecodedPixels: Long = SkikoImageRegionDecoder.DefaultMaxDecodedPixels,
  ): ImageRegionDecoder {
    return SkikoImageRegionDecoder.Factory(bytes = { bytes }, maxDecodedPixels = maxDecodedPixels)
      .create(SkikoImageDecoderFactoryParams(ImageBitmapOptions.Default))
  }
}
