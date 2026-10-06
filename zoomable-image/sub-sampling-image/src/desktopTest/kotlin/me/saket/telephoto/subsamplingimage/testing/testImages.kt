package me.saket.telephoto.subsamplingimage.testing

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

/** Colors of the four quadrants drawn by [encodeQuadrants]. Chosen so that each has one dominant trait. */
enum class Quadrant(val argb: Int) {
  TopLeft(Color.RED),
  TopRight(Color.GREEN),
  BottomLeft(Color.BLUE),
  BottomRight(Color.WHITE),
}

/** Encodes an image whose four quadrants are filled with the colors of [Quadrant]. */
fun encodeQuadrants(width: Int, height: Int, format: EncodedImageFormat): ByteArray {
  val surface = Surface.makeRasterN32Premul(width, height)
  try {
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    fun fill(quadrant: Quadrant, left: Float, top: Float) {
      surface.canvas.drawRect(Rect.makeXYWH(left, top, halfWidth, halfHeight), Paint().apply { color = quadrant.argb })
    }
    fill(Quadrant.TopLeft, 0f, 0f)
    fill(Quadrant.TopRight, halfWidth, 0f)
    fill(Quadrant.BottomLeft, 0f, halfHeight)
    fill(Quadrant.BottomRight, halfWidth, halfHeight)
    return surface.makeImageSnapshot().encodeToData(format, 90)!!.bytes
  } finally {
    surface.close()
  }
}

/**
 * Inserts an EXIF segment carrying [orientation] (1-8) right after the JPEG's
 * start-of-image marker. Big-endian TIFF with a single IFD entry.
 */
fun ByteArray.withExifOrientation(orientation: Int): ByteArray {
  check(this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) { "Not a JPEG" }
  val tiff = byteArrayOf(
    'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A,  // Big endian TIFF header.
    0x00, 0x00, 0x00, 0x08,                            // Offset of the first IFD.
    0x00, 0x01,                                        // One entry.
    0x01, 0x12,                                        // Tag: Orientation.
    0x00, 0x03,                                        // Type: SHORT.
    0x00, 0x00, 0x00, 0x01,                            // Count.
    0x00, orientation.toByte(), 0x00, 0x00,            // Value.
    0x00, 0x00, 0x00, 0x00,                            // No next IFD.
  )
  val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
  val length = payload.size + 2
  val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
  return copyOfRange(0, 2) + segment + copyOfRange(2, size)
}

class RenderedTile(val size: IntSize, private val pixels: PixelMap) {
  /** Which [Quadrant]'s color the pixel at ([x], [y]) is closest to. */
  fun dominantQuadrantAt(x: Int, y: Int): Quadrant {
    val color = pixels[x, y]
    val red = (color.red * 255).roundToInt()
    val green = (color.green * 255).roundToInt()
    val blue = (color.blue * 255).roundToInt()
    return Quadrant.entries.minBy { quadrant ->
      val q = quadrant.argb
      val dr = red - Color.getR(q)
      val dg = green - Color.getG(q)
      val db = blue - Color.getB(q)
      dr * dr + dg * dg + db * db
    }
  }
}

/** Draws the decoded tile's painter at its intrinsic size and reads back its pixels. */
fun ImageRegionDecoder.DecodeResult.render(): RenderedTile {
  val intrinsic = painter.intrinsicSize
  val size = IntSize(intrinsic.width.roundToInt(), intrinsic.height.roundToInt())
  val bitmap = ImageBitmap(size.width, size.height)
  CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(intrinsic.width, intrinsic.height)) {
    with(painter) { draw(this@draw.size) }
  }
  return RenderedTile(size, bitmap.toPixelMap())
}
