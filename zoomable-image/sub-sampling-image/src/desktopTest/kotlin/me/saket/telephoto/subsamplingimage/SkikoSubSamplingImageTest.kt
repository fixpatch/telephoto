package me.saket.telephoto.subsamplingimage

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.testing.Quadrant
import me.saket.telephoto.subsamplingimage.testing.encodeQuadrants
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalTestApi::class)
class SkikoSubSamplingImageTest {

  /**
   * A 6000 x 4000 image is larger than any single GPU texture that some devices can upload.
   * SubSamplingImage must still display it, without needing a preview bitmap.
   */
  @Test fun displays_a_large_image_in_full_quality() = runComposeUiTest {
    val bytes = encodeQuadrants(6000, 4000, EncodedImageFormat.JPEG)
    lateinit var imageState: SubSamplingImageState

    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
        val source = remember { SubSamplingImageSource.bytes(bytes) }
        imageState = rememberSubSamplingImageState(imageSource = source, zoomableState = zoomableState)
        SubSamplingImage(
          state = imageState,
          contentDescription = null,
          modifier = Modifier
            .size(600.dp, 400.dp)
            .zoomable(zoomableState),
        )
      }
    }

    waitUntil(timeoutMillis = 10_000) { imageState.isImageDisplayedInFullQuality }
    assertEquals(androidx.compose.ui.unit.IntSize(6000, 4000), imageState.imageSize)

    val pixels = onRoot().captureToImage().toPixelMap()
    assertQuadrant(Quadrant.TopLeft, pixels[150, 100])
    assertQuadrant(Quadrant.TopRight, pixels[450, 100])
    assertQuadrant(Quadrant.BottomLeft, pixels[150, 300])
    assertQuadrant(Quadrant.BottomRight, pixels[450, 300])
  }

  /**
   * A 1200 x 900 image in an 800 x 600 viewport has a full-resolution base tile (sample size 1),
   * so no foreground tiles exist. The preview is only as large as the viewport. While the preview
   * fits on screen the base tile must stay undecoded, but zooming in to 100% must decode it
   * instead of stretching the preview.
   */
  @Test fun decodes_base_tile_when_zooming_past_the_preview_resolution() = runComposeUiTest {
    val decodedSampleSizes = ConcurrentLinkedQueue<Int>()
    SkikoSubSamplingDefaults.regionDecoderProviders = listOf(
      SkikoRegionDecoderProvider { _, _ ->
        object : ImageRegionDecoder {
          override val imageSize = IntSize(1200, 900)
          override suspend fun decodeRegion(region: IntRect, sampleSize: Int): ImageRegionDecoder.DecodeResult {
            decodedSampleSizes += sampleSize
            return ImageRegionDecoder.DecodeResult(ColorPainter(ComposeColor.Red), hasUltraHdrContent = false)
          }
        }
      },
    )
    val bytes = encodeQuadrants(1200, 900, EncodedImageFormat.JPEG)
    lateinit var imageState: SubSamplingImageState
    lateinit var zoomableState: ZoomableState
    lateinit var scope: CoroutineScope

    setContent {
      scope = rememberCoroutineScope()
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
        val source = remember { SubSamplingImageSource.bytes(bytes, preview = ImageBitmap(800, 600)) }
        imageState = rememberSubSamplingImageState(imageSource = source, zoomableState = zoomableState)
        SubSamplingImage(
          state = imageState,
          contentDescription = null,
          modifier = Modifier
            .size(800.dp, 600.dp)
            .zoomable(zoomableState),
        )
      }
    }

    waitUntil(timeoutMillis = 10_000) { imageState.isImageDisplayed && imageState.imageSize != null }
    waitForIdle()
    assertTrue(decodedSampleSizes.isEmpty(), "The preview should stand in for the base tile: $decodedSampleSizes")

    // 800 / 1200 is the fitted scale, so zooming by 1.5x shows the image at 100%.
    scope.launch { zoomableState.zoomTo(1.5f, animationSpec = snap()) }
    waitUntil(timeoutMillis = 10_000) { 1 in decodedSampleSizes }
  }

  @AfterTest fun reset() {
    SkikoSubSamplingDefaults.regionDecoderProviders = emptyList()
  }

  private fun assertQuadrant(expected: Quadrant, actual: androidx.compose.ui.graphics.Color) {
    val red = (actual.red * 255).roundToInt()
    val green = (actual.green * 255).roundToInt()
    val blue = (actual.blue * 255).roundToInt()
    val q = expected.argb
    val distance = listOf(red - Color.getR(q), green - Color.getG(q), blue - Color.getB(q)).sumOf { it * it }
    assertTrue(distance < 40 * 40 * 3, "Expected $expected but was rgb($red, $green, $blue)")
  }
}
