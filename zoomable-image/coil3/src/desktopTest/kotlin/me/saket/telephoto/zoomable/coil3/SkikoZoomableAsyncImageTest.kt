@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable.coil3

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.nio.file.Files
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SkikoZoomableAsyncImageTest {
  private val tempDir: Path = Files.createTempDirectory("telephoto-coil3").toOkioPath()

  @AfterTest fun cleanUp() {
    FileSystem.SYSTEM.deleteRecursively(tempDir)
  }

  /** Remote images are read from Coil's disk cache, which is the path FutabaViewer takes. */
  @Test fun disk_cached_image_is_sub_sampled() = runComposeUiTest {
    val diskCache = DiskCache.Builder()
      .directory(tempDir / "cache")
      .maxSizeBytes(64L * 1024 * 1024)
      .build()
    diskCache.openEditor(DiskCachedModel.KEY)!!.let { editor ->
      FileSystem.SYSTEM.write(editor.data) { write(encodeQuadrants(6000, 4000)) }
      editor.commit()
    }
    val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
      .diskCache(diskCache)
      .components { add(DiskCachedModel.Fetcher.Factory(diskCache)) }
      .build()

    assertDisplaysQuadrants(imageLoader, model = DiskCachedModel)
  }

  /** Local files that Coil reads directly are sub-sampled from the same file. */
  @Test fun local_file_is_sub_sampled() = runComposeUiTest {
    val file = tempDir / "large.jpg"
    FileSystem.SYSTEM.write(file) { write(encodeQuadrants(6000, 4000)) }
    val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE).build()

    assertDisplaysQuadrants(imageLoader, model = file.toString())
  }

  private fun androidx.compose.ui.test.ComposeUiTest.assertDisplaysQuadrants(imageLoader: ImageLoader, model: Any) {
    lateinit var state: ZoomableImageState
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        state = rememberZoomableImageState()
        ZoomableAsyncImage(
          model = model,
          imageLoader = imageLoader,
          contentDescription = null,
          state = state,
          modifier = Modifier.size(600.dp, 400.dp),
        )
      }
    }

    waitUntil(timeoutMillis = 10_000) { state.isImageDisplayedInFullQuality }
    assertNotNull(state.subSamplingState, "Image wasn't sub-sampled")

    val pixels = onRoot().captureToImage().toPixelMap()
    assertColor(Quadrant.TopLeft, pixels[150, 100])
    assertColor(Quadrant.TopRight, pixels[450, 100])
    assertColor(Quadrant.BottomLeft, pixels[150, 300])
    assertColor(Quadrant.BottomRight, pixels[450, 300])
  }

  private object DiskCachedModel {
    const val KEY = "large-image"

    class Fetcher(private val diskCache: DiskCache) : coil3.fetch.Fetcher {
      override suspend fun fetch(): FetchResult {
        val snapshot = diskCache.openSnapshot(KEY)!!
        return SourceFetchResult(
          source = ImageSource(
            file = snapshot.data,
            fileSystem = diskCache.fileSystem,
            diskCacheKey = KEY,
            closeable = snapshot,
          ),
          mimeType = "image/jpeg",
          dataSource = DataSource.DISK,
        )
      }

      class Factory(private val diskCache: DiskCache) : coil3.fetch.Fetcher.Factory<DiskCachedModel> {
        override fun create(data: DiskCachedModel, options: Options, imageLoader: ImageLoader) = Fetcher(diskCache)
      }
    }
  }
}

private enum class Quadrant(val argb: Int) {
  TopLeft(org.jetbrains.skia.Color.RED),
  TopRight(org.jetbrains.skia.Color.GREEN),
  BottomLeft(org.jetbrains.skia.Color.BLUE),
  BottomRight(org.jetbrains.skia.Color.WHITE),
}

private fun encodeQuadrants(width: Int, height: Int): ByteArray {
  val surface = Surface.makeRasterN32Premul(width, height)
  try {
    val w = width / 2f
    val h = height / 2f
    fun fill(quadrant: Quadrant, left: Float, top: Float) {
      surface.canvas.drawRect(Rect.makeXYWH(left, top, w, h), Paint().apply { color = quadrant.argb })
    }
    fill(Quadrant.TopLeft, 0f, 0f)
    fill(Quadrant.TopRight, w, 0f)
    fill(Quadrant.BottomLeft, 0f, h)
    fill(Quadrant.BottomRight, w, h)
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)!!.bytes
  } finally {
    surface.close()
  }
}

private fun assertColor(expected: Quadrant, actual: Color) {
  val rgb = listOf(actual.red, actual.green, actual.blue).map { (it * 255).roundToInt() }
  val q = expected.argb
  val target = listOf(org.jetbrains.skia.Color.getR(q), org.jetbrains.skia.Color.getG(q), org.jetbrains.skia.Color.getB(q))
  val distance = rgb.zip(target).sumOf { (a, b) -> (a - b) * (a - b) }
  assertTrue(distance < 40 * 40 * 3, "Expected $expected but was rgb$rgb")
}
