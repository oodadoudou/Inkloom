package app.inkloom.document

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/**
 * CSP file objects: an image placed from a file that stays linked to it. The notebook keeps its
 * own copy, so it still opens when the file is gone; Update reads the file again when it has
 * changed, and a missing or no longer readable file is reported by name instead of failing quietly.
 */
class FileObjects(private val context: Context, private val assets: DocumentAssetService) {
    sealed interface Refresh {
        data class Updated(val image: PageElement.Image) : Refresh
        data object Unchanged : Refresh
        data class Missing(val name: String) : Refresh
    }

    /** The file's name, modification time and size, or null when it can no longer be read. */
    fun inspect(uri: Uri): FileObjectSource? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun text(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getString)
            fun number(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            FileObjectSource(uri.toString(), text(OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment.orEmpty(),
                number(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L, number(OpenableColumns.SIZE) ?: -1L)
        }
    }.getOrNull()

    /** A new file object at the default placement. */
    fun place(uri: Uri): PageElement.Image {
        val source = requireNotNull(inspect(uri)) { "The file could not be read" }
        return assets.importPageImage(uri).copy(source = source)
    }

    /** Reads the file again when it changed since it was last read (or always with [force]). */
    fun refresh(image: PageElement.Image, force: Boolean = false): Refresh {
        val link = image.source ?: return Refresh.Unchanged
        val current = inspect(link.uri.toUri()) ?: return Refresh.Missing(link.name)
        if (!force && current.modified == link.modified && current.size == link.size) return Refresh.Unchanged
        return runCatching { Refresh.Updated(reread(image, link.uri.toUri(), current, keepBox = true)) }.getOrElse { Refresh.Missing(link.name) }
    }

    /** Links the object to another file, keeping its place and width; height follows the new image. */
    fun replace(image: PageElement.Image, uri: Uri): PageElement.Image {
        val source = requireNotNull(inspect(uri)) { "The file could not be read" }
        return reread(image, uri, source, keepBox = false)
    }

    private fun reread(image: PageElement.Image, uri: Uri, source: FileObjectSource, keepBox: Boolean): PageElement.Image {
        val fresh = assets.importPageImage(uri)
        val height = if (keepBox) image.height else image.width * fresh.height / fresh.width
        return image.copy(assetPath = fresh.assetPath, height = height, source = source, crop = if (keepBox) image.crop else ImageCrop())
    }
}

/** CSP Convert layer to file object: the layer's pixels are written to a file and replaced by an image linked to it. */
internal object FileObjectConversion {
    fun prepare(context: Context, assets: DocumentAssetService, page: InkPage, layerId: String, destination: Uri): PreparedPageGeometry {
        val layer = requireNotNull(page.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() }) { "Choose a pixel layer to convert" }
        val raster = requireNotNull(layer.rasterTileSurface) { "This layer has no pixels to convert" }
        val size = PagePngExportSize.from(page)
        val bitmap = app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(assets.renderRasterLayerSurface(page, layerId, size.width).snapshot())
        try {
            requireNotNull(context.contentResolver.openOutputStream(destination, "wt")) { "The file could not be written" }.use { out ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) { "The file could not be written" }
            }
        } finally { bitmap.recycle() }
        runCatching { context.contentResolver.takePersistableUriPermission(destination, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val placed = FileObjects(context, assets).place(destination)
        // The object covers exactly the visible page the layer was rendered from.
        val visible = placed.copy(x = 0f, y = 0f, width = size.width.toFloat(), height = size.height.toFloat(), layerId = layerId)
        val image = PageCropTransform.unproject(visible, page.crop)
        // The pixels being replaced stay reserved for undo.
        val reserved = assets.saveRasterTileSurface(assets.loadRasterTileSurface(raster), raster)
        val after = page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(rasterTileSurface = null, rasterRotationQuarterTurns = 0, vector = it.vector?.let { VectorLayerContent() }) else it },
            elements = page.elements + image)
        return PreparedPageGeometry(PageGeometryEdit(page, after)) { assets.releaseRasterTileReservation(reserved) }
    }
}
