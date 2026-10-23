package app.inkloom.document

import app.inkloom.color.pixelFormat
import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.selection.CanonicalLassoContour
import app.inkloom.engine.v5.selection.SelectionMaskRasterizer
import app.inkloom.engine.v5.selection.SparseAlphaMask
import app.inkloom.engine.v5.selection.AlphaMaskOperation
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.tools.PixelSurfaceResampling
import app.inkloom.engine.v5.tools.VectorLayerRenderer
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Page edits for frame border folders. A frame folder's mask is rendered from its frames (grown
 * by half the line width so contents reach under the line), and its locked border layer holds the
 * frame lines; both are rebuilt whenever the frames change. Each edit is one undo step.
 */
internal object FrameBorderEdits {
    private fun pixelSize(page: InkPage): Pair<Int, Int> =
        page.width.roundToInt().coerceIn(1, 8192) to page.height.roundToInt().coerceIn(1, 8192)

    private fun requireUncropped(page: InkPage) = require(page.crop == PageCrop()) { "Remove the page crop to use frame border folders" }

    /** CSP's New frame border folder: one frame inset [margin] page units, above [targetId]. */
    fun create(assets: DocumentAssetService, page: InkPage, targetId: String, border: FrameBorder, names: FrameNames): PreparedPageGeometry {
        requireUncropped(page)
        val target = requireNotNull(page.layers.firstOrNull { it.id == targetId }) { "Choose a layer first" }
        val parentId = target.parentId?.takeIf { id -> page.layers.any { it.id == id && it.role == LayerRole.FOLDER } }
        val folder = InkLayer(name = nextName(page, names.folder), role = LayerRole.FOLDER, parentId = parentId)
        val content = InkLayer(name = names.content, parentId = folder.id)
        val after = insertAbove(page, target, listOf(folder, content))
        return rebuild(assets, page, after, folder.id, border, names.border)
    }

    /** New frames for an existing frame folder; with [division] new frames may get their own folders. */
    fun update(assets: DocumentAssetService, page: InkPage, folderId: String, border: FrameBorder, names: FrameNames,
               division: FrameDivision = FrameDivision.BORDER_ONLY): PreparedPageGeometry {
        requireUncropped(page)
        val folder = requireNotNull(page.layers.firstOrNull { it.id == folderId && it.frame != null }) { "This is not a frame border folder" }
        require(border.frames.isNotEmpty()) { "A frame border folder needs at least one frame" }
        val old = folder.frame!!.frames.mapTo(hashSetOf()) { it.id }
        val added = border.frames.filter { it.id !in old }
        if (division == FrameDivision.BORDER_ONLY || added.isEmpty()) return rebuild(assets, page, page, folderId, border, names.border)
        // New frames move to new frame folders above this one, empty or with copies of its layers.
        val kept = border.copy(frames = border.frames.filter { it.id in old })
        var working = page
        val releases = mutableListOf<() -> Unit>()
        try {
            added.forEach { frame ->
                val newFolder = folder.copy(id = UUID.randomUUID().toString(), name = nextName(working, names.folder), frame = null, mask = null)
                val children = if (division == FrameDivision.FOLDERS_DUPLICATE) descendants(working, folderId).filter { it.frameBorderOf == null }
                    .map { it.copy(id = UUID.randomUUID().toString(), parentId = newFolder.id) }
                    else listOf(InkLayer(name = names.content, parentId = newFolder.id))
                working = insertAbove(working, folder, listOf(newFolder) + children)
                val prepared = rebuild(assets, working, working, newFolder.id, border.copy(frames = listOf(frame)), names.border)
                releases += prepared::close
                working = prepared.edit.after
            }
            val final = rebuild(assets, working, working, folderId, kept, names.border)
            releases += final::close
            return PreparedPageGeometry(PageGeometryEdit(page, final.edit.after)) { releases.forEach { it() } }
        } catch (failure: Throwable) { releases.forEach { it() }; throw failure }
    }

    /** CSP's Delete frame border: the folder becomes an ordinary folder; its layers stay. */
    fun removeBorder(page: InkPage, folderId: String): PageGeometryEdit {
        require(page.layers.any { it.id == folderId && it.frame != null }) { "This is not a frame border folder" }
        return PageGeometryEdit(page, page.copy(layers = page.layers.filterNot { it.frameBorderOf == folderId }
            .map { if (it.id == folderId) it.copy(frame = null, mask = null) else it }))
    }

    /** Renders the mask and border layer of [folderId] in [after] for [border]. */
    private fun rebuild(assets: DocumentAssetService, before: InkPage, after: InkPage, folderId: String, border: FrameBorder, borderName: String): PreparedPageGeometry {
        val (width, height) = pixelSize(after)
        val maskSnapshot = mask(border, width, height, after.width, after.height)
        val mask = assets.saveMaskTileSurface(maskSnapshot)
        val raster = try {
            if (!border.drawBorder) null else {
                val lines = VectorLayerRenderer.render(VectorLayerContent(FrameGeometry.borderStrokes(border)), VectorFrame(width, height, width / after.width.coerceAtLeast(1f)))
                val surface = if (after.pixelFormat() == PixelTileFormat.PREMULTIPLIED_SRGB_8888) lines
                    else PixelSurfaceResampling.convert(lines.snapshot(), after.pixelFormat())
                assets.saveRasterTileSurface(surface)
            }
        } catch (failure: Throwable) { assets.releaseAssetReservation(mask.assetPath); throw failure }
        fun release() { assets.releaseAssetReservation(mask.assetPath); raster?.let(assets::releaseRasterTileReservation) }
        try {
            val folder = after.layers.first { it.id == folderId }
            val existingBorder = after.layers.firstOrNull { it.frameBorderOf == folderId }
            var layers = after.layers.map {
                if (it.id == folderId) it.copy(frame = border, mask = (it.mask ?: LayerMask()).copy(surface = mask, enabled = true)) else it
            }
            layers = when {
                raster == null -> layers.filterNot { it.frameBorderOf == folderId }
                existingBorder != null -> layers.map { if (it.id == existingBorder.id) it.copy(rasterTileSurface = raster, rasterRotationQuarterTurns = 0) else it }
                else -> {
                    // The border sits above the folder's other layers.
                    val line = InkLayer(name = borderName, parentId = folder.id, locked = true, frameBorderOf = folder.id, rasterTileSurface = raster)
                    val lastChild = layers.indexOfLast { isInside(layers, it, folder.id) }
                    layers.toMutableList().apply { add(if (lastChild >= 0) lastChild + 1 else layers.indexOfFirst { it.id == folder.id } + 1, line) }
                }
            }
            return PreparedPageGeometry(PageGeometryEdit(before, after.copy(layers = layers)), ::release)
        } catch (failure: Throwable) { release(); throw failure }
    }

    /** Union of the frames grown by half the line width. */
    internal fun mask(border: FrameBorder, width: Int, height: Int, pageWidth: Float, pageHeight: Float): MaskTileSurfaceSnapshot {
        val scaleX = width / pageWidth; val scaleY = height / pageHeight
        val parts = border.frames.mapNotNull { frame ->
            val outline = FrameGeometry.grown(frame, border.lineWidth / 2f, pageWidth, pageHeight)
            SelectionMaskRasterizer.rasterize(CanonicalLassoContour(outline.map { (x, y) -> DocumentPoint(x * scaleX, y * scaleY) }, closed = true),
                width.toFloat(), height.toFloat(), width, height)
        }
        val union = parts.drop(1).fold(parts.firstOrNull()?.let(SparseAlphaMask::fromSnapshot)) { mask, next ->
            mask?.composed(SparseAlphaMask.fromSnapshot(next), AlphaMaskOperation.ADD) ?: SparseAlphaMask.fromSnapshot(next)
        }
        return union?.surface?.snapshot() ?: app.inkloom.engine.v5.surface.MaskTileSurface(width, height).snapshot()
    }

    private fun isInside(layers: List<InkLayer>, layer: InkLayer, folderId: String): Boolean {
        var parent = layer.parentId
        while (parent != null) { if (parent == folderId) return true; parent = layers.firstOrNull { it.id == parent }?.parentId }
        return false
    }

    private fun descendants(page: InkPage, folderId: String) = page.layers.filter { isInside(page.layers, it, folderId) }

    /** [layers] (a folder followed by its children) placed just above [target] and its children. */
    private fun insertAbove(page: InkPage, target: InkLayer, layers: List<InkLayer>): InkPage {
        val lastOfTarget = page.layers.indexOfLast { it.id == target.id || isInside(page.layers, it, target.id) }
        val placed = layers.map { if (it.parentId == null || layers.none { other -> other.id == it.parentId }) it.copy(parentId = target.parentId) else it }
        return page.copy(layers = page.layers.toMutableList().apply { addAll(lastOfTarget + 1, placed) })
    }

    private fun nextName(page: InkPage, base: String): String {
        val names = page.layers.mapTo(hashSetOf()) { it.name }
        return generateSequence(1) { it + 1 }.map { "$base $it" }.first { it !in names }
    }
}

/** Display names for the layers a frame folder creates, in the user's language. */
data class FrameNames(val folder: String = "Frame", val content: String = "Layer 1", val border: String = "Frame border")
