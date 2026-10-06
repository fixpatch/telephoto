package me.saket.telephoto.zoomable.coil3.internal

import androidx.compose.ui.graphics.ImageBitmap
import coil3.ImageLoader
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import okio.FileSystem

internal actual suspend fun localImageSourceOrNull(
  result: SuccessResult,
  imageLoader: ImageLoader,
  preview: ImageBitmap?,
): SubSamplingImageSource? = localFileImageSourceOrNull(result, imageLoader, preview, FileSystem.SYSTEM)

internal actual fun <T> runBlockingForPreview(block: suspend () -> T): T = runBlocking { block() }
