package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions

internal data class AndroidImageDecoderFactoryParams(
  val context: Context,
  override val imageOptions: ImageBitmapOptions,
) : ImageRegionDecoder.FactoryParams
