@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package me.saket.telephoto.zoomable.coil3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import coil3.compose.LocalPlatformContext
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImageModelEqualityDelegate
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImageModelEqualityDelegate
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.asPainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.crossfadeMillis
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.SizeResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.coil3.internal.canBeSubSampled
import me.saket.telephoto.zoomable.coil3.internal.diskCacheImageSource
import me.saket.telephoto.zoomable.coil3.internal.imageBitmapOptions
import me.saket.telephoto.zoomable.coil3.internal.ioDispatcher
import me.saket.telephoto.zoomable.coil3.internal.isLocalImage
import me.saket.telephoto.zoomable.coil3.internal.localImageSourceOrNull
import me.saket.telephoto.zoomable.coil3.internal.runBlockingForPreview
import me.saket.telephoto.zoomable.coil3.internal.toPreviewImageBitmap
import me.saket.telephoto.zoomable.ZoomableImageSource.PainterDelegate
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.copy
import me.saket.telephoto.zoomable.internal.RememberWorker
import me.saket.telephoto.zoomable.isInScreenshotTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import coil3.size.Size as CoilSize

@Immutable
@OptIn(ExperimentalCoilApi::class)
internal data class Coil3ImageSource(
  private val model: State<Any?>,
  private val imageLoader: State<ImageLoader>,
) : ZoomableImageSource {

  @Composable
  override fun resolve(canvasSize: Flow<Size>): ResolveResult {
    val context = LocalPlatformContext.current
    val modelEqualityDelegate = LocalAsyncImageModelEqualityDelegate.current
    val previewHandler = if (isInScreenshotTest()) LocalAsyncImagePreviewHandler.current else null

    val resolver = if (previewHandler != null) {
      val model = model.value
      val imageLoader = imageLoader.value
      remember(ModelEqualityKey(model, modelEqualityDelegate), imageLoader) {
        val request = model as? ImageRequest
          ?: ImageRequest.Builder(context)
            .data(model)
            .build()
        PreviewResolver(
          request = request,
          imageLoader = imageLoader,
          previewHandler = previewHandler,
        )
      }
    } else {
      remember(this) {
        val requests = snapshotFlow { model.value }
          .distinctUntilChanged(modelEqualityDelegate::equals)
          .map { model ->
            model as? ImageRequest
              ?: ImageRequest.Builder(context)
                .data(model)
                .build()
          }
        RealResolver(
          requests = requests,
          imageLoaders = snapshotFlow { imageLoader.value },
          sizeResolver = { canvasSize.first().toCoilSize() },
        )
      }
    }
    return resolver.resolved
  }

  private fun Size.toCoilSize() = CoilSize(
    width = if (width.isFinite()) Dimension(width.roundToInt()) else Dimension.Undefined,
    height = if (height.isFinite()) Dimension(height.roundToInt()) else Dimension.Undefined
  )
}

private interface AbstractImageResolver {
  val resolved: ResolveResult
}

private class RealResolver(
  private val requests: Flow<ImageRequest>,
  private val imageLoaders: Flow<ImageLoader>,
  private val sizeResolver: SizeResolver,
) : RememberWorker(), AbstractImageResolver {

  override var resolved: ResolveResult by mutableStateOf(
    ResolveResult(delegate = null)
  )

  override suspend fun work() {
    combine(requests, imageLoaders, ::Pair).collectLatest { (request, imageLoader) ->
      work(
        request = request,
        imageLoader = imageLoader,
        retryAttempts = 0,
      )
    }
  }

  private suspend fun work(request: ImageRequest, imageLoader: ImageLoader, retryAttempts: Int) {
    val result = imageLoader.execute(
      request.newBuilder()
        .size(request.defined.sizeResolver ?: sizeResolver)
        // There's no easy way to be certain whether an image will require sub-sampling in
        // advance so assume it'll be needed and force Coil to write this image to disk.
        .diskCachePolicy(
          when (request.diskCachePolicy) {
            CachePolicy.ENABLED -> CachePolicy.ENABLED
            CachePolicy.READ_ONLY -> CachePolicy.ENABLED
            CachePolicy.WRITE_ONLY,
            CachePolicy.DISABLED -> CachePolicy.WRITE_ONLY
          }
        )
        .memoryCachePolicy(
          if (retryAttempts > 0) CachePolicy.WRITE_ONLY else request.memoryCachePolicy
        )
        // This will unfortunately replace any existing target, but it is also the only
        // way to read placeholder images set using ImageRequest#placeholderMemoryCacheKey.
        // Placeholder images should be small in size so sub-sampling isn't needed here.
        .target(
          onStart = {
            resolved = resolved.copy(
              placeholder = it?.asPainter(request.context),
            )
          }
        )
        // Increase memory cache hit rate because the image will anyway fit the canvas
        // size at draw time.
        .precision(
          when (request.defined.precision) {
            Precision.EXACT -> request.precision
            else -> Precision.INEXACT
          }
        )
        .build()
    )

    val imageSource = result.toSubSamplingImageSource(imageLoader)
    if (imageSource == null && retryAttempts < 1 && result is SuccessResult && result.image is BitmapImage) {
      // Sub-sampling isn't available. This can happen if:
      // - The image was served from memory cache, but the disk cache was cleared.
      // - The disk cache entry was evicted between Coil writing it and telephoto reading it.
      // Retry once to re-populate it before falling back to a non-sub-sampled image.
      work(request, imageLoader, retryAttempts = retryAttempts + 1)
      return
    }

    resolved = resolved.copy(
      crossfadeDuration = result.crossfadeDuration(),
      delegate = if (result is SuccessResult && imageSource != null) {
        ZoomableImageSource.SubSamplingDelegate(
          source = imageSource,
          imageOptions = (result.image as BitmapImage).imageBitmapOptions()
        )
      } else {
        ZoomableImageSource.PainterDelegate(
          painter = result.image?.asPainter(request.context)
        )
      },
    )
  }

  private suspend fun ImageResult.toSubSamplingImageSource(imageLoader: ImageLoader): SubSamplingImageSource? {
    val result = this
    val resultImage = (result as? SuccessResult)?.image
    val source = if (result is SuccessResult && resultImage is BitmapImage) {
      val preview = resultImage.toPreviewImageBitmap()
      when {
        // Prefer reading of images directly from files whenever possible because
        // it is significantly faster than reading from their input streams.
        result.diskCacheKey != null -> {
          val diskCache = imageLoader.diskCache!!
          val snapshot = withContext(ioDispatcher) {  // IO because openSnapshot can delete files.
            diskCache.openSnapshot(result.diskCacheKey!!)
          }
          if (snapshot == null) {
            null
          } else {
            diskCacheImageSource(
              path = snapshot.data,
              fileSystem = diskCache.fileSystem,
              preview = preview,
              onClose = object : okio.Closeable {
                override fun close() = snapshot.close()
              },
            )
          }
        }

        result.isLocalImage() -> {
          // Possible reasons for reaching this code path:
          // - Locally stored images such as assets, resource, etc.
          // - Remote image that wasn't saved to disk because of a "no-store" HTTP header.
          localImageSourceOrNull(result, imageLoader, preview)
        }

        else -> {
          // Image wasn't saved to the disk. Telephoto won't be able to load this image in its full
          // quality. It'll attempt to display the bitmap directly as a fallback, but that can
          // potentially cause an OutOfMemoryError when the bitmap is drawn.
          return null
        }
      }
    } else {
      return null
    }
    return source?.takeIf { canBeSubSampled(it, request.context) }
  }

  private fun ImageResult.crossfadeDuration(): Duration {
    // I'm intentionally not using the transition factory because it optimizes crossfade duration
    // to zero if the image was fetched from memory cache. SubSamplingImage will only read
    // bitmaps from the disk so there will always be some delay in showing the image.
    val crossfadeMillis = request.crossfadeMillis
    return if (this is SuccessResult && crossfadeMillis > 0) {
      crossfadeMillis.milliseconds
    } else {
      Duration.ZERO
    }
  }

}

@OptIn(ExperimentalCoilApi::class)
private class PreviewResolver(
  request: ImageRequest,
  imageLoader: ImageLoader,
  previewHandler: AsyncImagePreviewHandler,
) : AbstractImageResolver {

  override val resolved: ResolveResult = runBlockingForPreview {
    val preview = previewHandler.handle(
      imageLoader = imageLoader,
      request = request.newBuilder()
        .coroutineContext(EmptyCoroutineContext)
        .build()
    ).painter
    ResolveResult(PainterDelegate(preview))
  }
}

// todo: can this be replaced by a SnapshotMutationPolicy?
@OptIn(ExperimentalCoilApi::class)
private class ModelEqualityKey(
  private val model: Any?,
  private val delegate: AsyncImageModelEqualityDelegate,
) {
  override fun equals(other: Any?): Boolean {
    return other is ModelEqualityKey &&
      delegate == other.delegate &&
      delegate.equals(model, other.model)
  }

  override fun hashCode(): Int {
    return 31 * delegate.hashCode() + delegate.hashCode(model)
  }
}
