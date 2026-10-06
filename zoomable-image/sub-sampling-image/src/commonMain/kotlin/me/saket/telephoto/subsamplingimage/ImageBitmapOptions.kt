package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import dev.drewhamilton.poko.Poko
import kotlin.LazyThreadSafetyMode.NONE

/**
 * @param colorSpace If this is null, the platform's decoder will pick either the color space
 * embedded in the image or the color space best suited for the requested image configuration.
 */
@Poko
@Immutable
class ImageBitmapOptions internal constructor(
  val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888,
  /**
   * The decoder's own color space object, if it was read from a platform bitmap (for example
   * `android.graphics.ColorSpace`). Kept untyped so that this class can live in commonMain.
   */
  internal val platformColorSpace: Any? = null,
  colorSpace: () -> ColorSpace?,
) {
  // The compose color space is computed lazily to prevent crashes by bad Android <> Compose UI
  // conversions. Example: https://issuetracker.google.com/issues/377021410#comment3
  @Suppress("unused")
  val colorSpace: ColorSpace? by lazy(NONE) { colorSpace() }

  // TODO: remove when https://issuetracker.google.com/issues/377021410 is resolved.
  constructor(
    config: ImageBitmapConfig = ImageBitmapConfig.Argb8888,
    colorSpace: ColorSpace? = null,
  ) : this(
    config = config,
    colorSpace = { colorSpace },
    platformColorSpace = null,
  )

  @Deprecated("", level = DeprecationLevel.HIDDEN)  // For binary compatibility.
  constructor(config: ImageBitmapConfig) : this(config, null)

  companion object {
    val Default = ImageBitmapOptions()
  }
}
