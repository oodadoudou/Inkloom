package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** CSP's material palette: pictures registered for placing, and whole pages registered as templates. */
@Serializable
enum class MaterialKind { IMAGE, TEMPLATE, ANIMATION }

/**
 * One registered material. An image keeps its picture as a managed asset; a template keeps a
 * snapshot of the page with its layers, masks and objects. Both refer to immutable assets, so
 * pages made from them share pixels until they are edited.
 */
@Serializable
data class Material(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: MaterialKind,
    val imageAssetPath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val page: InkPage? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    init { require((kind == MaterialKind.IMAGE) == (imageAssetPath != null) && (kind != MaterialKind.IMAGE) == (page != null)) }
}

class MaterialStore(root: File) {
    private val file = File(root, "materials.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<Material> = runCatching {
        if (!file.isFile) emptyList() else json.decodeFromString(ListSerializer(Material.serializer()), file.readText())
    }.getOrDefault(emptyList())

    fun add(material: Material): List<Material> = write(list().filterNot { it.id == material.id } + material)
    fun delete(id: String): List<Material> = write(list().filterNot { it.id == id })
    fun rename(id: String, name: String): List<Material> = write(list().map { if (it.id == id) it.copy(name = name.trim().take(80).ifBlank { it.name }) else it })

    private fun write(materials: List<Material>): List<Material> {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(ListSerializer(Material.serializer()), materials))
        check(temporary.renameTo(file)) { "Materials could not be saved" }
        return materials
    }

    companion object {
        /** Asset garbage collection sees materials as one document, so their assets stay alive. */
        fun asDocument(materials: List<Material>): InkDocument = InkDocument(id = "materials-library", title = "Materials",
            pages = materials.mapNotNull { material ->
                material.page ?: material.imageAssetPath?.let { path ->
                    InkPage(id = material.id, elements = listOf(PageElement.Image(x = 0f, y = 0f, width = 1f, height = 1f, assetPath = path)))
                }
            }.ifEmpty { listOf(InkPage(id = "materials-empty")) })

        /** A page made from a template material: new IDs for the page and its objects, shared immutable pixels. */
        fun pageFromTemplate(template: InkPage, title: String, nextId: () -> String = { UUID.randomUUID().toString() }): InkPage =
            template.copy(id = nextId(), title = title, bookmarked = false, customNumber = null,
                elements = template.elements.map { element -> element.withId(nextId()) },
                savedSelections = template.savedSelections.map { it.copy(id = nextId()) },
                pdfGotoLinks = emptyList(), recognizedTextBlocks = emptyList(), handwritingIndexFingerprint = "", imageOcrFingerprint = "")

        /** A copy of [element] with a new ID, on the layer [layerId]. */
        fun withLayer(element: PageElement, layerId: String): PageElement = when (val copy = element.withId(UUID.randomUUID().toString())) {
            is PageElement.Text -> copy.copy(layerId = layerId); is PageElement.Sticky -> copy.copy(layerId = layerId); is PageElement.Shape -> copy.copy(layerId = layerId)
            is PageElement.Image -> copy.copy(layerId = layerId); is PageElement.Fill -> copy.copy(layerId = layerId); is PageElement.Link -> copy.copy(layerId = layerId)
        }

        private fun PageElement.withId(id: String): PageElement = when (this) {
            is PageElement.Text -> copy(id = id); is PageElement.Sticky -> copy(id = id); is PageElement.Shape -> copy(id = id)
            is PageElement.Image -> copy(id = id); is PageElement.Fill -> copy(id = id); is PageElement.Link -> copy(id = id)
        }
    }
}

/** Registers a layer's pixels as an image material, trimmed to its visible content. */
internal object MaterialImages {
    fun register(assets: DocumentAssetService, cacheDir: File, page: InkPage, layerId: String, name: String): Material {
        val size = PagePngExportSize.from(page)
        val snapshot = assets.renderRasterLayerSurface(page, layerId, size.width).snapshot()
        var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1
        snapshot.tileInfo.filter { it.nonTransparentPixelCount > 0 }.forEach { info ->
            val x0 = info.key.column * snapshot.tileSize; val y0 = info.key.row * snapshot.tileSize
            for (y in y0 until minOf(snapshot.logicalHeight, y0 + snapshot.tileSize)) for (x in x0 until minOf(snapshot.logicalWidth, x0 + snapshot.tileSize))
                if (snapshot.linearPixelAt(x, y).alpha > 0f) { left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1) }
        }
        require(right > left) { "This layer has nothing to register" }
        val bitmap = app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(snapshot,
            app.inkloom.engine.v5.surface.PixelRect(left, top, right, bottom))
        val file = File(cacheDir, "material-${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) { "The material could not be saved" } }
            val image = assets.importPageImage(android.net.Uri.fromFile(file))
            return Material(name = name, kind = MaterialKind.IMAGE, imageAssetPath = image.assetPath, imageWidth = right - left, imageHeight = bottom - top)
        } finally { bitmap.recycle(); file.delete() }
    }
}
