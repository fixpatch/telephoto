package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions

// Non-Android platforms have no build-type flag. Errors are reported to the
// SubSamplingImageErrorReporter without crashing, as in Android release builds.
internal actual val isDebugBuild: Boolean get() = false

@Immutable
internal data class SkikoImageDecoderFactoryParams(
  override val imageOptions: ImageBitmapOptions,
) : ImageRegionDecoder.FactoryParams

@Composable
internal actual fun rememberImageDecoderFactoryParams(imageOptions: ImageBitmapOptions): ImageRegionDecoder.FactoryParams {
  return remember(imageOptions) { SkikoImageDecoderFactoryParams(imageOptions) }
}

internal actual fun ImageBitmap.hasUltraHdrContent(): Boolean = false

@Composable
internal actual fun UltraHdrColorModeEffect(enabled: Boolean) = Unit

@Composable
internal actual fun isInScreenshotTest(): Boolean = LocalInspectionMode.current
