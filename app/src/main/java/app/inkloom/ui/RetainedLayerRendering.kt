package app.inkloom.ui

import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.PageCropTransform
import app.inkloom.document.PageElement

/** Shared by visible retained content and temporary text-free tool references. */
internal fun renderRetainedLayerElements(
    assets: DocumentAssetService,
    page: InkPage,
    layer: InkLayer,
    width: Int,
    excludeText: Boolean = false,
): LayerElementBitmaps {
    val ids = page.elements.filter { it.layerId == layer.id && (!excludeText || it !is PageElement.Text) }
        .mapTo(linkedSetOf(), PageElement::id)
    val captured = layer.alphaLockBaseContentIds.toSet()
    val split = layer.alphaLock && captured.isNotEmpty()
    val baseIds = if (split) ids.intersect(captured) else ids
    val addedIds = if (split) ids - captured else emptySet()
    val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page)).toInt().coerceAtLeast(1)
    if (page.colorManagement.highPrecision || page.colorManagement.assignedProfile != null || page.colorManagement.softProof || width.toLong() * height > 4_194_304L) {
        return LayerElementBitmaps(null, null,
            baseIds.takeIf(Set<String>::isNotEmpty)?.let { assets.renderElementLayerSurface(page, layer.id, width, height, it) },
            addedIds.takeIf(Set<String>::isNotEmpty)?.let { assets.renderElementLayerSurface(page, layer.id, width, height, it) })
    }
    val base = baseIds.takeIf(Set<String>::isNotEmpty)?.let { assets.renderElementLayerBitmap(page, layer.id, width, it) }
    return try {
        LayerElementBitmaps(base, addedIds.takeIf(Set<String>::isNotEmpty)?.let { assets.renderElementLayerBitmap(page, layer.id, width, it) })
    } catch (failure: Throwable) {
        base?.recycle()
        throw failure
    }
}
