package app.inkloom.document

import java.io.File

object DocumentAssetGarbageCollector {
    private const val NEW_ASSET_GRACE_PERIOD_MS = 5 * 60 * 1_000L

    fun collect(
        rootDirectory: File,
        documents: List<InkDocument>,
        pageTemplates: PageTemplateLibrary,
        protectedPaths: Set<String> = emptySet(),
    ): Int {
        val root = rootDirectory.canonicalFile
        val referenced = linkedSetOf<String>()
        fun retain(path: String?) {
            if (path.isNullOrBlank()) return
            val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return
            if (file.parentFile == root) referenced += file.path
        }
        protectedPaths.forEach(::retain)
        documents.forEach { document ->
            retain(document.coverImagePath)
            document.pages.forEach { page ->
                when (val background = page.background) {
                    is PageBackground.Pdf -> retain(background.assetPath)
                    is PageBackground.Image -> retain(background.assetPath)
                    is PageBackground.Paper -> Unit
                }
                page.layers.forEach { layer -> retain(layer.rasterAssetPath) }
                page.elements.forEach { element -> when (element) {
                    is PageElement.Image -> retain(element.assetPath)
                    is PageElement.AudioMarker -> retain(element.assetPath)
                    is PageElement.Fill -> retain(element.maskAssetPath)
                    is PageElement.Shape -> retain(element.maskAssetPath)
                    else -> Unit
                } }
            }
        }
        pageTemplates.customTemplates.forEach { template -> when (val background = template.background) {
            is PageBackground.Pdf -> retain(background.assetPath)
            is PageBackground.Image -> retain(background.assetPath)
            is PageBackground.Paper -> Unit
        } }
        var removed = 0
        val orphanCutoff = System.currentTimeMillis() - NEW_ASSET_GRACE_PERIOD_MS
        root.listFiles()?.filter(File::isFile)?.forEach { file ->
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
            // An import may have finished copying its file just before the document transaction is
            // saved. Never race that short unreferenced window; a later run collects true orphans.
            if (canonical.parentFile == root && canonical.path !in referenced && canonical.lastModified() <= orphanCutoff && canonical.delete()) removed += 1
        }
        return removed
    }
}
