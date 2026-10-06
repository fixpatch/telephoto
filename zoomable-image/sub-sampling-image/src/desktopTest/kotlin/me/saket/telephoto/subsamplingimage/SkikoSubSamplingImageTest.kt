package me.saket.telephoto.subsamplingimage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import me.saket.telephoto.subsamplingimage.testing.Quadrant
import me.saket.telephoto.subsamplingimage.testing.encodeQuadrants
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

  private fun assertQuadrant(expected: Quadrant, actual: androidx.compose.ui.graphics.Color) {
    val red = (actual.red * 255).roundToInt()
    val green = (actual.green * 255).roundToInt()
    val blue = (actual.blue * 255).roundToInt()
    val q = expected.argb
    val distance = listOf(red - Color.getR(q), green - Color.getG(q), blue - Color.getB(q)).sumOf { it * it }
    assertTrue(distance < 40 * 40 * 3, "Expected $expected but was rgb($red, $green, $blue)")
  }
}
