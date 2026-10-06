package me.saket.telephoto.subsamplingimage.internal

import android.content.pm.ActivityInfo
import android.os.Build.VERSION.SDK_INT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import me.saket.telephoto.subsamplingimage.BuildConfig
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions

internal actual val isDebugBuild: Boolean get() = BuildConfig.DEBUG

@Composable
internal actual fun rememberImageDecoderFactoryParams(imageOptions: ImageBitmapOptions): ImageRegionDecoder.FactoryParams {
  val context = LocalContext.current
  return remember(context, imageOptions) {
    AndroidImageDecoderFactoryParams(
      context = context,
      imageOptions = imageOptions,
    )
  }
}

internal actual fun ImageBitmap.hasUltraHdrContent(): Boolean {
  return if (SDK_INT >= 34) asAndroidBitmap().hasGainmap() else false
}

@Composable
internal actual fun UltraHdrColorModeEffect(enabled: Boolean) {
  if (enabled && SDK_INT >= 26) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
      val activity = context.findActivity()
      val previousColorMode = activity.window.colorMode
      activity.window.colorMode = ActivityInfo.COLOR_MODE_HDR
      onDispose {
        activity.window.colorMode = previousColorMode
      }
    }
  }
}
