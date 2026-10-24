package app.inkloom.document

import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Animation export, as CSP's File > Export animation > Animated GIF: one frame per cel, each held
 * for its timeline duration at the folder's frame rate, rendered with the page's paper and the
 * other layers exactly as an image export would (onion skin is display only and never included).
 */
object AnimationExport {
    data class Frame(val page: InkPage, val celId: String, val delayCentiseconds: Int)

    /** The pages to render, in timeline order; rounding is carried over so the total length stays right. */
    fun frames(page: InkPage, folderId: String): List<Frame> {
        val folder = page.layers.firstOrNull { it.id == folderId }?.animation ?: return emptyList()
        var carry = 0.0
        return folder.cels.filter { cel -> page.layers.any { it.id == cel.layerId } }.map { cel ->
            val exact = cel.frames * 100.0 / folder.frameRate + carry
            val delay = exact.roundToInt().coerceAtLeast(2)
            carry = exact - delay
            Frame(AnimationEdits.show(page, folderId, cel.layerId), cel.layerId, delay)
        }
    }

    /** Writes the animated GIF at [width] pixels wide; [onProgress] gets the frames done so far. */
    fun writeGif(assets: DocumentAssetService, page: InkPage, folderId: String, width: Int, out: OutputStream, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val frames = frames(page, folderId)
        require(frames.isNotEmpty()) { "The animation has no cels" }
        var encoder: GifEncoder? = null
        frames.forEachIndexed { index, frame ->
            val bitmap = assets.renderPageBitmap(frame.page, width.coerceIn(16, 2048))
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                (encoder ?: GifEncoder(out, bitmap.width, bitmap.height).also { encoder = it }).addFrame(pixels, frame.delayCentiseconds)
            } finally { bitmap.recycle() }
            onProgress(index + 1, frames.size)
        }
        requireNotNull(encoder).finish()
    }
}
