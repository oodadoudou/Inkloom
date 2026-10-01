package app.inkloom.document

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.*

/** Keeps a decoder open and renders only the requested strip, including EXIF, crop and rotation. */
internal class PageBackgroundStripRenderer(private val page: InkPage, private val width: Int, private val height: Int) : AutoCloseable {
    private val visible = PageCropTransform.project(page)
    private val frame = visible.backgroundFrame ?: PageBackgroundFrame(0f, 0f, visible.width, visible.height)
    private val target = RectF(frame.left * width / visible.width, frame.top * height / visible.height,
        (frame.left + frame.width) * width / visible.width, (frame.top + frame.height) * height / visible.height)
    private val crop = PageCropTransform.normalized(page.backgroundSourceCrop ?: page.crop)
    private val turns = page.backgroundRotationQuarterTurns.mod(4)
    private val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
    private var descriptor: ParcelFileDescriptor? = null
    private var pdf: PdfRenderer? = null
    private var pdfPage: PdfRenderer.Page? = null
    private var image: BitmapRegionDecoder? = null
    private var orientation = 1

    init {
        if (backgroundVisible) when (val background = page.background) {
            is PageBackground.Pdf -> try {
                descriptor = ParcelFileDescriptor.open(File(background.assetPath), ParcelFileDescriptor.MODE_READ_ONLY)
                pdf = PdfRenderer(requireNotNull(descriptor)); pdfPage = pdf!!.openPage(background.pageIndex)
            } catch (failure: Throwable) { close(); throw failure }
            is PageBackground.Image -> {
                image = BitmapRegionDecoder.newInstance(background.assetPath, false)
                orientation = runCatching { ExifInterface(background.assetPath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            }
            else -> Unit
        }
    }

    private fun paperPoint(u: Float, v: Float, top: Int): Pair<Float, Float> {
        val rotated = when (turns) { 1 -> 1-v to u; 2 -> 1-u to 1-v; 3 -> v to 1-u; else -> u to v }
        return target.left + (rotated.first - crop.left) / (crop.right - crop.left) * target.width() to
            target.top + (rotated.second - crop.top) / (crop.bottom - crop.top) * target.height() - top
    }

    private fun matrix(sourceWidth: Int, sourceHeight: Int, top: Int, mapper: (Float, Float) -> Pair<Float, Float>): Matrix {
        val src = floatArrayOf(0f, 0f, sourceWidth.toFloat(), 0f, 0f, sourceHeight.toFloat())
        val points = listOf(mapper(0f, 0f), mapper(sourceWidth.toFloat(), 0f), mapper(0f, sourceHeight.toFloat()))
        val dst = points.flatMap { listOf(it.first, it.second) }.toFloatArray()
        return Matrix().also { check(it.setPolyToPoly(src, 0, dst, 0, 3)) }
    }

    fun draw(bitmap: Bitmap, top: Int) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (!backgroundVisible) Color.WHITE else when (val b = page.background) {
            is PageBackground.Paper -> b.colorArgb.toInt(); is PageBackground.Image -> b.paperColorArgb.toInt(); else -> Color.WHITE
        })
        if (!backgroundVisible) return
        when (val background = page.background) {
            is PageBackground.Paper -> { canvas.translate(0f, -top.toFloat()); PaperPatternRenderer.draw(canvas, background.pattern, background.density, width.toFloat(), height.toFloat()) }
            is PageBackground.Pdf -> {
                val p = requireNotNull(pdfPage)
                val transform = matrix(p.width, p.height, top) { x, y -> paperPoint(x / p.width, y / p.height, top) }
                val clip = Rect(maxOf(0, floor(target.left).toInt()), maxOf(0, floor(target.top - top).toInt()),
                    minOf(width, ceil(target.right).toInt()), minOf(bitmap.height, ceil(target.bottom - top).toInt()))
                if (!clip.isEmpty) {
                    if(bitmap.config==Bitmap.Config.RGBA_F16) {
                        val prepared=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
                        try { prepared.eraseColor(Color.WHITE);p.render(prepared,clip,transform,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);canvas.drawBitmap(prepared,0f,0f,null) } finally { prepared.recycle() }
                    } else p.render(bitmap,clip,transform,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
            is PageBackground.Image -> {
                val decoder = requireNotNull(image)
                val swaps = orientation in 5..8
                val ow = if (swaps) decoder.height.toFloat() else decoder.width.toFloat()
                val oh = if (swaps) decoder.width.toFloat() else decoder.height.toFloat()
                val paperWidth = ow
                val paperHeight = ow / background.paperAspectRatio.takeIf { it > 0 && it.isFinite() }.let { it ?: (ow / oh) }
                val fit = if (background.cropToFill) max(paperWidth / ow, paperHeight / oh) else min(paperWidth / ow, paperHeight / oh)
                val transform = matrix(decoder.width, decoder.height, top) { x, y ->
                    val point = when (orientation) {
                        2 -> decoder.width - x to y; 3 -> decoder.width - x to decoder.height - y
                        4 -> x to decoder.height - y; 5 -> y to x; 6 -> decoder.height - y to x
                        7 -> decoder.height - y to decoder.width - x; 8 -> y to decoder.width - x; else -> x to y
                    }
                    paperPoint((point.first * fit + (paperWidth - ow * fit) / 2) / paperWidth,
                        (point.second * fit + (paperHeight - oh * fit) / 2) / paperHeight, top)
                }
                val inverse = Matrix(); check(transform.invert(inverse))
                val region = RectF(0f, 0f, width.toFloat(), bitmap.height.toFloat()); inverse.mapRect(region)
                val source = Rect(floor(region.left - 1).toInt().coerceIn(0, decoder.width), floor(region.top - 1).toInt().coerceIn(0, decoder.height),
                    ceil(region.right + 1).toInt().coerceIn(0, decoder.width), ceil(region.bottom + 1).toInt().coerceIn(0, decoder.height))
                if (source.isEmpty) return
                val decoded = decoder.decodeRegion(source, BitmapFactory.Options().apply { inPreferredConfig = if(bitmap.config==Bitmap.Config.RGBA_F16) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                    inPreferredColorSpace=android.graphics.ColorSpace.get(if(bitmap.config==Bitmap.Config.RGBA_F16) android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB else android.graphics.ColorSpace.Named.SRGB) })
                try {
                    canvas.clipRect(target.left, target.top - top, target.right, target.bottom - top)
                    canvas.concat(transform)
                    canvas.drawBitmap(decoded, null, RectF(source), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                } finally { decoded.recycle() }
            }
        }
    }

    override fun close() { pdfPage?.close(); pdfPage = null; pdf?.close(); pdf = null; descriptor?.close(); descriptor = null; image?.recycle(); image = null }
}
