package me.saket.telephoto.subsamplingimage

import android.graphics.Bitmap
import android.os.Build.VERSION.SDK_INT
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toAndroidColorSpace
import androidx.compose.ui.graphics.toComposeColorSpace
import android.graphics.ColorSpace as AndroidColorSpace

/**
 * The color space to request from Android's decoders. Prefers the color space that was read
 * from an Android bitmap (see [ImageBitmapOptions] factory below) over a conversion of the
 * Compose color space.
 */
internal val ImageBitmapOptions.androidColorSpace: AndroidColorSpace?
  get() = when {
    SDK_INT < 26 -> null
    else -> platformColorSpace as AndroidColorSpace? ?: colorSpace?.toAndroidColorSpace()
  }

fun ImageBitmapOptions(from: Bitmap): ImageBitmapOptions {
  val androidColorSpace = if (SDK_INT >= 26) from.colorSpace else null
  return ImageBitmapOptions(
    config = from.config!!.toComposeConfig(),
    colorSpace = { if (SDK_INT >= 26) androidColorSpace?.toComposeColorSpace() else null },
    platformColorSpace = androidColorSpace,
  )
}

// TODO: remove when https://issuetracker.google.com/issues/322269945 is resolved.
private fun Bitmap.Config.toComposeConfig(): ImageBitmapConfig {
  return when {
    SDK_INT >= 26 && this == Bitmap.Config.HARDWARE -> ImageBitmapConfig.Gpu
    SDK_INT >= 26 && this == Bitmap.Config.RGBA_F16 -> ImageBitmapConfig.F16
    this == Bitmap.Config.ARGB_8888 -> ImageBitmapConfig.Argb8888
    this == Bitmap.Config.RGB_565 -> ImageBitmapConfig.Rgb565
    this == Bitmap.Config.ALPHA_8 -> ImageBitmapConfig.Alpha8
    else -> ImageBitmapConfig.Argb8888
  }
}

internal fun ImageBitmapConfig.toAndroidConfig(): Bitmap.Config {
  return when {
    SDK_INT >= 26 && this == ImageBitmapConfig.Gpu -> Bitmap.Config.HARDWARE
    SDK_INT >= 26 && this == ImageBitmapConfig.F16 -> Bitmap.Config.RGBA_F16
    this == ImageBitmapConfig.Argb8888 -> Bitmap.Config.ARGB_8888
    this == ImageBitmapConfig.Rgb565 -> Bitmap.Config.RGB_565
    this == ImageBitmapConfig.Alpha8 -> Bitmap.Config.ALPHA_8
    else -> Bitmap.Config.ARGB_8888
  }
}
