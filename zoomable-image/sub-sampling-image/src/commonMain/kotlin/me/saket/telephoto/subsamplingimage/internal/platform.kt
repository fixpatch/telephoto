package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions

/** Whether the library was built in debug mode. Used for surfacing image loading errors early. */
internal expect val isDebugBuild: Boolean

/** Parameters handed to [ImageRegionDecoder.Factory.create]. */
@Composable
internal expect fun rememberImageDecoderFactoryParams(imageOptions: ImageBitmapOptions): ImageRegionDecoder.FactoryParams

/**
 * Whether the bitmap contains [ultra HDR content](https://developer.android.com/media/grow/ultra-hdr/display).
 * Always false on platforms that don't support it.
 */
internal expect fun ImageBitmap.hasUltraHdrContent(): Boolean

/** Switches the window into HDR color mode while [enabled]. No-op on platforms that don't support it. */
@Composable
internal expect fun UltraHdrColorModeEffect(enabled: Boolean)

/** Whether this is running inside a screenshot test or an IDE preview, where images are not decoded. */
@Composable
internal expect fun isInScreenshotTest(): Boolean
