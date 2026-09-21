package app.inkloom.document

import app.inkloom.engine.v5.selection.LayerMaskRuntime
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import java.util.concurrent.atomic.AtomicBoolean

internal enum class LayerMaskRejection { MISSING_LAYER, PAPER, LOCKED, MISSING_MASK, EXISTING_MASK, MISSING_SELECTION, GEOMETRY, ASSET_UNAVAILABLE, WRONG_DISPATCHER }
internal class LayerMaskCommandRejected(val reason: LayerMaskRejection) : IllegalArgumentException(reason.name)

/** A prepared attachment and the exact assets its history must keep alive. */
internal class PreparedLayerMaskEdit(
    val edit: LayerMaskAttachmentEdit,
    private val releaseReservation: () -> Unit,
    private val releaseHistory: () -> Unit,
) : AutoCloseable {
    private val published = AtomicBoolean()
    private val disposed = AtomicBoolean()
    fun published() { if (published.compareAndSet(false, true)) releaseReservation() }
    override fun close() {
        published()
        if (disposed.compareAndSet(false, true)) releaseHistory()
    }
}

/** Runs off the input thread after the page's publication barrier, without mutating the document. */
internal class LayerMaskCommandPlanner(private val assets: DocumentAssetService) {
    fun prepare(page: InkPage, layerId: String, command: LayerMaskCommand,
        selection: MaskTileSurfaceSnapshot?): PreparedLayerMaskEdit {
        fun reject(reason: LayerMaskRejection): Nothing = throw LayerMaskCommandRejected(reason)
        val layer = page.layers.firstOrNull { it.id == layerId } ?: reject(LayerMaskRejection.MISSING_LAYER)
        if (layer.role == LayerRole.BACKGROUND) reject(LayerMaskRejection.PAPER)
        if (LayerHierarchy.effectiveLocked(page.layers, layer)) reject(LayerMaskRejection.LOCKED)
        val before = layer.mask
        val creating = command == LayerMaskCommand.CREATE_MASK_SELECTION || command == LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION
        if (creating && before != null) reject(LayerMaskRejection.EXISTING_MASK)
        if (!creating && before == null) reject(LayerMaskRejection.MISSING_MASK)
        var newPath: String? = null
        val after: LayerMask? = when (command) {
            LayerMaskCommand.DELETE -> null
            LayerMaskCommand.TOGGLE_ENABLED -> requireNotNull(before).copy(enabled = !before.enabled)
            LayerMaskCommand.TOGGLE_LINKED -> requireNotNull(before).copy(linked = !before.linked)
            LayerMaskCommand.EDIT, LayerMaskCommand.REPLACE_SURFACE, LayerMaskCommand.LOAD_AS_SELECTION,
            LayerMaskCommand.TOGGLE_OVERLAY,
            LayerMaskCommand.APPLY -> reject(LayerMaskRejection.WRONG_DISPATCHER)
            else -> {
                if (command in setOf(LayerMaskCommand.HIDE_SELECTION, LayerMaskCommand.REVEAL_SELECTION) && selection == null)
                    reject(LayerMaskRejection.MISSING_SELECTION)
                val surface = if (before?.surface != null) {
                    try { assets.loadMaskTileSurface(before.surface) }
                    catch (_: Exception) { reject(LayerMaskRejection.ASSET_UNAVAILABLE) }
                } else if (creating && selection != null) {
                    MaskTileSurface(selection.logicalWidth, selection.logicalHeight, selection.tileSize)
                } else {
                    val size = SelectionMaskCompositor.maskSize(page.width, page.height) ?: reject(LayerMaskRejection.GEOMETRY)
                    MaskTileSurface(size.width, size.height, initialDefaultAlpha = if (before?.baseVisible != false) 255 else 0)
                }
                if (selection != null && command in setOf(LayerMaskCommand.HIDE_SELECTION, LayerMaskCommand.REVEAL_SELECTION) &&
                    (selection.logicalWidth != surface.logicalWidth || selection.logicalHeight != surface.logicalHeight)) reject(LayerMaskRejection.GEOMETRY)
                var changed = false
                val snapshot = LayerMaskRuntime(surface).use { runtime ->
                    changed = when (command) {
                        LayerMaskCommand.CREATE_MASK_SELECTION -> if (selection == null) runtime.fill(255) != null else runtime.replaceFromSelection(selection) != null
                        LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION -> if (selection == null) runtime.fill(0) != null else runtime.replaceFromSelection(selection, inverted = true) != null
                        LayerMaskCommand.FILL_WHITE -> runtime.fill(255) != null
                        LayerMaskCommand.FILL_BLACK -> runtime.fill(0) != null
                        LayerMaskCommand.HIDE_SELECTION -> runtime.hideSelection(requireNotNull(selection)) != null
                        LayerMaskCommand.REVEAL_SELECTION -> runtime.revealSelection(requireNotNull(selection)) != null
                        LayerMaskCommand.INVERT -> runtime.invert() != null
                        else -> reject(LayerMaskRejection.WRONG_DISPATCHER)
                    }
                    runtime.snapshot
                }
                if (!changed && before?.surface != null) before else {
                    val reference = assets.saveMaskTileSurface(snapshot)
                    newPath = reference.assetPath
                    (before ?: LayerMask()).copy(baseVisible = reference.defaultAlpha > 0, surface = reference)
                }
            }
        }
        val releaseHistory = assets.retainHistoryAssets(listOfNotNull(before?.surface?.assetPath, after?.surface?.assetPath))
        return PreparedLayerMaskEdit(LayerMaskAttachmentEdit(layerId, before, after),
            { newPath?.let(assets::releaseAssetReservation) }, releaseHistory)
    }
}
