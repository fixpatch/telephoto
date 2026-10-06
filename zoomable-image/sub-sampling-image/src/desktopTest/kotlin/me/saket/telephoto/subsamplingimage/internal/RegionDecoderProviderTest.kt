package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SkikoRegionDecoderProvider
import me.saket.telephoto.subsamplingimage.SkikoSubSamplingDefaults
import me.saket.telephoto.subsamplingimage.testing.encodeQuadrants
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class RegionDecoderProviderTest {
  private val params = SkikoImageDecoderFactoryParams(ImageBitmapOptions.Default)

  @AfterTest fun reset() {
    SkikoSubSamplingDefaults.regionDecoderProviders = emptyList()
  }

  @Test fun registered_decoder_is_used_first() = runBlocking {
    val custom = FakeDecoder()
    SkikoSubSamplingDefaults.regionDecoderProviders = listOf(
      SkikoRegionDecoderProvider { _, _ -> null },  // Declines: falls through to the next one.
      SkikoRegionDecoderProvider { _, _ -> custom },
    )
    val decoder = SkikoImageRegionDecoder.Factory(bytes = { jpeg() }, maxDecodedPixels = Long.MAX_VALUE).create(params)
    assertSame(custom, decoder)
  }

  @Test fun failing_provider_falls_back_to_skia() = runBlocking {
    SkikoSubSamplingDefaults.regionDecoderProviders = listOf(
      SkikoRegionDecoderProvider { _, _ -> error("Unsupported") },
    )
    val decoder = SkikoImageRegionDecoder.Factory(bytes = { jpeg() }, maxDecodedPixels = Long.MAX_VALUE).create(params)
    assertIs<SkikoImageRegionDecoder>(decoder)
    decoder.close()
  }

  private fun jpeg() = encodeQuadrants(64, 48, EncodedImageFormat.JPEG)

  private class FakeDecoder : ImageRegionDecoder {
    override val imageSize = IntSize(64, 48)
    override suspend fun decodeRegion(region: IntRect, sampleSize: Int) =
      ImageRegionDecoder.DecodeResult(ColorPainter(androidx.compose.ui.graphics.Color.Red), hasUltraHdrContent = false)
  }
}
