package app.inkloom.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.inkloom.document.RasterEnlarger
import app.inkloom.engine.v5.composite.ExtendedSrgb
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.PixelSurfaceResampling
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ED-10 super resolution, on the device: the bundled Real-ESRGAN anime model (realesr-animevideov3,
 * 4×, BSD-3-Clause) enlarges illustrations, filling in detail instead of only interpolating. Work
 * is done in overlapping tiles and each tile is scaled straight to the requested size, so memory
 * stays bounded; transparency is enlarged by the model too.
 */
class SuperResolution(private val context: Context) : RasterEnlarger, AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private var loaded: OrtSession? = null
    private val session: OrtSession get() = loaded ?: run {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            runCatching { addXnnpack(mapOf("intra_op_num_threads" to threads.toString())) }
        }
        environment.createSession(context.assets.open(MODEL).use { it.readBytes() }, options).also { loaded = it }
    }

    override fun enlarge(source: PixelTileSurfaceSnapshot, width: Int, height: Int, cancelled: () -> Boolean): PixelTileSurface {
        val sw = source.logicalWidth; val sh = source.logicalHeight
        require(width >= sw && height >= sh) { "Super resolution only enlarges" }
        val output = PixelTileSurface(width, height, pixelFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        val scaleX = width / sw.toFloat(); val scaleY = height / sh.toFloat()
        val write = output.beginWrite("super resolution")
        val tiles = ((sw + TILE - 1) / TILE) * ((sh + TILE - 1) / TILE)
        var done = 0
        try {
            var ty = 0
            while (ty < sh) {
                var tx = 0
                while (tx < sw) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("Super resolution cancelled")
                    val core = PixelRect(tx, ty, min(sw, tx + TILE), min(sh, ty + TILE))
                    // Only tiles with something in them are worth the model's time.
                    if (hasContent(source, core)) {
                        val padded = PixelRect(max(0, core.left - PAD), max(0, core.top - PAD), min(sw, core.right + PAD), min(sh, core.bottom + PAD))
                        val enlarged = runTile(source, padded)
                        val target = PixelRect(floor(core.left * scaleX).toInt(), floor(core.top * scaleY).toInt(),
                            min(width, ceil(core.right * scaleX).toInt()), min(height, ceil(core.bottom * scaleY).toInt()))
                        if (!target.isEmpty) write.replacePremultipliedPixels(target, resampleInto(enlarged, padded, target, scaleX, scaleY))
                    }
                    tx += TILE
                    progressState.value = ++done / tiles.toFloat()
                }
                ty += TILE
            }
            write.commit().close()
        } catch (failure: Throwable) { write.cancel(); throw failure }
        return if (source.pixelFormat == output.pixelFormat) output else PixelSurfaceResampling.convert(output.snapshot(), source.pixelFormat)
    }

    /** Straight RGBA (0..1) of the region enlarged 4× by the model: [width × 4, height × 4, 4] values. */
    private fun runTile(source: PixelTileSurfaceSnapshot, region: PixelRect): FloatArray {
        val w = region.width; val h = region.height; val n = w * h
        val rgba = FloatArray(n * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val p = source.linearPixelAt(region.left + x, region.top + y)
            rgba[i + 3] = p.alpha
            if (p.alpha > 0f) { rgba[i] = ExtendedSrgb.encode(p.red / p.alpha).coerceIn(0f, 1f); rgba[i + 1] = ExtendedSrgb.encode(p.green / p.alpha).coerceIn(0f, 1f); rgba[i + 2] = ExtendedSrgb.encode(p.blue / p.alpha).coerceIn(0f, 1f) }
        }
        fillTransparentColours(rgba, w, h)
        val colour = infer(FloatArray(n * 3) { k -> rgba[(k % n) * 4 + k / n] }, w, h)
        val alpha = infer(FloatArray(n * 3) { k -> rgba[(k % n) * 4 + 3] }, w, h)
        val big = (w * 4) * (h * 4)
        return FloatArray(big * 4) { k ->
            val pixel = k / 4; val channel = k % 4
            (if (channel < 3) colour[channel * big + pixel] else alpha[pixel]).coerceIn(0f, 1f)
        }
    }

    /** NCHW float RGB in, NCHW float RGB at 4× out. */
    private fun infer(planes: FloatArray, w: Int, h: Int): FloatArray =
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(planes), longArrayOf(1, 3, h.toLong(), w.toLong())).use { input ->
            session.run(mapOf("input" to input)).use { result ->
                val tensor = result[0] as OnnxTensor
                val buffer = tensor.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }

    /** Transparent pixels take nearby colours, so edges do not pick up black halos. */
    private fun fillTransparentColours(rgba: FloatArray, w: Int, h: Int) {
        val known = BooleanArray(w * h) { rgba[it * 4 + 3] > 0f }
        if (known.none { it }) return
        repeat(PAD) {
            val next = known.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (known[i]) continue
                var r = 0f; var g = 0f; var b = 0f; var count = 0
                for ((dx, dy) in NEIGHBOURS) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h || !known[ny * w + nx]) continue
                    val j = (ny * w + nx) * 4; r += rgba[j]; g += rgba[j + 1]; b += rgba[j + 2]; count++
                }
                if (count > 0) { rgba[i * 4] = r / count; rgba[i * 4 + 1] = g / count; rgba[i * 4 + 2] = b / count; next[i] = true }
            }
            next.copyInto(known)
        }
    }

    /** Area-averages the 4× tile into the target rectangle, returning premultiplied ARGB. */
    private fun resampleInto(enlarged: FloatArray, padded: PixelRect, target: PixelRect, scaleX: Float, scaleY: Float): IntArray {
        val bw = padded.width * 4; val bh = padded.height * 4
        val out = IntArray(target.width * target.height)
        for (y in 0 until target.height) for (x in 0 until target.width) {
            // The target pixel's footprint in the 4× tile.
            val sx0 = ((target.left + x) / scaleX - padded.left) * 4f; val sx1 = ((target.left + x + 1) / scaleX - padded.left) * 4f
            val sy0 = ((target.top + y) / scaleY - padded.top) * 4f; val sy1 = ((target.top + y + 1) / scaleY - padded.top) * 4f
            var r = 0f; var g = 0f; var b = 0f; var a = 0f; var count = 0
            for (yy in floor(sy0).toInt().coerceIn(0, bh - 1)..(ceil(sy1).toInt() - 1).coerceIn(0, bh - 1))
                for (xx in floor(sx0).toInt().coerceIn(0, bw - 1)..(ceil(sx1).toInt() - 1).coerceIn(0, bw - 1)) {
                    val i = (yy * bw + xx) * 4
                    val alpha = enlarged[i + 3]
                    r += enlarged[i] * alpha; g += enlarged[i + 1] * alpha; b += enlarged[i + 2] * alpha; a += alpha; count++
                }
            if (count == 0 || a <= 0f) continue
            val alpha = a / count
            val a8 = (alpha * 255f).roundToInt().coerceIn(0, 255)
            if (a8 == 0) continue
            fun channel(sum: Float) = (sum / count * 255f).roundToInt().coerceIn(0, a8)
            out[y * target.width + x] = (a8 shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
        return out
    }

    private fun hasContent(source: PixelTileSurfaceSnapshot, region: PixelRect): Boolean {
        val tile = source.tileSize
        return source.tileInfo.any { info ->
            info.nonTransparentPixelCount > 0 && info.key.column * tile < region.right && (info.key.column + 1) * tile > region.left &&
                info.key.row * tile < region.bottom && (info.key.row + 1) * tile > region.top
        }
    }

    override fun close() { loaded?.close(); loaded = null }

    companion object {
        private val progressState = kotlinx.coroutines.flow.MutableStateFlow(0f)
        /** Share of the current layer done, for a progress bar. */
        val progress: kotlinx.coroutines.flow.StateFlow<Float> = progressState
        private const val MODEL = "models/realesr-animevideov3.onnx"
        private const val TILE = 96
        private const val PAD = 8
        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
