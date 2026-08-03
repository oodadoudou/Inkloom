package app.inkloom.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.inkloom.document.*
import app.inkloom.model.ImportedBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class LibraryUiState(
    val documents: List<InkDocument> = emptyList(),
    val folders: List<InkFolder> = emptyList(),
    val selectedDocumentId: String? = null,
    val selectedPageId: String? = null,
    val recoveredOperations: Int = 0,
    val pdfIndexingDocumentIds: Set<String> = emptySet(),
)

class InkloomViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FileDocumentStore(application.filesDir)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val indexFile = File(application.filesDir, "library.json")
    private val backupService = LibraryBackupService(application.filesDir)
    private val pageTemplateStore = PageTemplateStore(application.filesDir)
    val assets = DocumentAssetService(application)
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state = mutableState.asStateFlow()
    private val mutablePageTemplates = MutableStateFlow(pageTemplateStore.load())
    val pageTemplates = mutablePageTemplates.asStateFlow()

    init {
        val recovered = store.recover()
        if (store.list().isEmpty()) {
            listOf(
                InkDocument(title = "Distributed Systems", coverArgb = 0xFF202020, pages = List(3) { InkPage(title = "Page ${it + 1}") }),
                InkDocument(title = "CS336 Reading Notes", coverArgb = 0xFF707070, pages = List(2) { InkPage(title = "Page ${it + 1}") }),
            ).forEach(store::save)
        }
        refresh(recovered)
    }

    fun createNote(title: String = "Untitled Note", coverStyle: CoverStyle = CoverStyle.BLACK): InkDocument = InkDocument(
        title = title.trim().ifBlank { "Untitled Note" },
        coverStyle = coverStyle,
        coverArgb = if (coverStyle in setOf(CoverStyle.WHITE, CoverStyle.WHITE_GRID)) 0xFFF4F4F4 else 0xFF202020,
    ).also { store.save(it); refresh(); select(it.id) }
    fun createNoteWithCover(uri: Uri, title: String = "Untitled Note"): InkDocument = InkDocument(
        title = title.trim().ifBlank { "Untitled Note" },
        coverStyle = CoverStyle.CUSTOM_IMAGE,
        coverImagePath = assets.importCoverImage(uri),
    ).also { store.save(it); refresh(); select(it.id) }
    fun importPdf(uri: Uri, title: String) = assets.importPdf(uri, title, indexText = false).also { document ->
        store.save(document)
        refresh()
        select(document.id)
        indexPdfText(document.id)
    }
    fun importImage(uri: Uri, title: String) = assets.importImage(uri, title).also { store.save(it); refresh(); select(it.id) }
    fun importBundle(uri: Uri) = assets.importBundle(uri).also { store.save(it); refresh(); select(it.id) }
    fun insertImage(uri: Uri) {
        val documentId = mutableState.value.selectedDocumentId ?: return
        val pageId = mutableState.value.selectedPageId ?: return
        val crop = store.get(documentId)?.pages?.firstOrNull { it.id == pageId }?.crop ?: PageCrop()
        addElement(documentId, pageId, PageCropTransform.unproject(assets.importPageImage(uri), crop))
    }
    fun select(documentId: String) { val document = store.get(documentId) ?: return; mutableState.value = mutableState.value.copy(selectedDocumentId = documentId, selectedPageId = document.pages.firstOrNull()?.id) }
    fun selectPage(pageId: String) { mutableState.value = mutableState.value.copy(selectedPageId = pageId) }

    fun rename(documentId: String, title: String) = update(documentId) { it.copy(title = title.ifBlank { it.title }, updatedAt = System.currentTimeMillis()) }
    fun toggleFavorite(documentId: String) = update(documentId) { it.copy(favorite = !it.favorite, updatedAt = System.currentTimeMillis()) }
    fun toggleArchive(documentId: String) = update(documentId) { it.copy(archivedAt = if (it.archivedAt == null) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis()) }
    fun setDocumentTags(documentId: String, tags: List<String>) = update(documentId) { it.copy(tags = normalizeTags(tags), updatedAt = System.currentTimeMillis()) }
    fun trash(documentId: String) = update(documentId) { it.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) }
    fun restore(documentId: String) = update(documentId) { it.copy(trashedAt = null, updatedAt = System.currentTimeMillis()) }
    fun deletePermanently(documentId: String) { store.deletePermanently(documentId); refresh() }
    fun duplicateDocument(documentId: String) {
        val source = store.get(documentId) ?: return
        val pageIds = source.pages.associate { it.id to java.util.UUID.randomUUID().toString() }
        val copiedPages = source.pages.map { page -> page.copy(id = pageIds.getValue(page.id), strokes = page.strokes.map { it.copy(id = java.util.UUID.randomUUID().toString()) }, elements = page.elements.map(::copyElement)) }
        val copy = source.copy(id = java.util.UUID.randomUUID().toString(), title = "${source.title} copy", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), pages = PdfGotoLinkNavigation.remapDestinations(copiedPages, pageIds))
        store.save(copy); refresh()
    }
    fun moveToFolder(documentId: String, folderId: String?) = update(documentId) { it.copy(folderId = folderId, updatedAt = System.currentTimeMillis()) }
    fun createFolder(name: String, parentId: String? = null) {
        if (name.isBlank()) return
        saveFolders(mutableState.value.folders + InkFolder(name = name.trim(), parentId = parentId)); refresh()
    }
    fun deleteFolder(folderId: String) {
        store.list().filter { it.folderId == folderId }.forEach { store.save(it.copy(folderId = null)) }
        saveFolders(mutableState.value.folders.filterNot { it.id == folderId || it.parentId == folderId }); refresh()
    }

    fun addPage(documentId: String, templateId: String? = null) = update(documentId) { document ->
        val library = mutablePageTemplates.value
        val template = library.template(templateId) ?: library.defaultFor(documentId)
        val page = PageTemplateOperations.newPage(template, "Page ${document.pages.size + 1}")
        mutableState.value = mutableState.value.copy(selectedPageId = page.id)
        document.copy(pages = document.pages + page, updatedAt = System.currentTimeMillis())
    }

    fun duplicatePage(documentId: String, pageId: String) = update(documentId) { document ->
        val source = document.pages.first { it.id == pageId }
        val copiedPageId = java.util.UUID.randomUUID().toString()
        val copy = PdfGotoLinkNavigation.remapDestinations(
            listOf(source.copy(id = copiedPageId, title = "${source.title} copy", strokes = source.strokes.map { it.copy(id = java.util.UUID.randomUUID().toString()) }, elements = source.elements.map(::copyElement))),
            mapOf(source.id to copiedPageId),
        ).single()
        val index = document.pages.indexOf(source) + 1
        val pages = document.pages.toMutableList().apply { add(index, copy) }
        mutableState.value = mutableState.value.copy(selectedPageId = copy.id)
        document.copy(pages = pages, updatedAt = System.currentTimeMillis())
    }

    fun deletePage(documentId: String, pageId: String) = update(documentId) { document ->
        if (document.pages.size == 1) document else {
            val pages = document.pages.filterNot { it.id == pageId }
            mutableState.value = mutableState.value.copy(selectedPageId = pages.first().id)
            document.copy(pages = pages, updatedAt = System.currentTimeMillis())
        }
    }

    fun movePage(documentId: String, pageId: String, delta: Int) = update(documentId) { document ->
        val from = document.pages.indexOfFirst { it.id == pageId }
        val to = (from + delta).coerceIn(0, document.pages.lastIndex)
        if (from < 0 || from == to) document else document.copy(pages = document.pages.toMutableList().apply { add(to, removeAt(from)) }, updatedAt = System.currentTimeMillis())
    }

    fun transferPage(sourceDocumentId: String, pageId: String, targetDocumentId: String, copy: Boolean) {
        if (sourceDocumentId == targetDocumentId) return
        val source = store.get(sourceDocumentId) ?: return
        val target = store.get(targetDocumentId) ?: return
        val result = PageTransfer.apply(source, target, pageId, copy) ?: return
        store.save(result.target)
        if (!copy) store.save(result.source)
        refresh()
        mutableState.value = mutableState.value.copy(selectedDocumentId = targetDocumentId, selectedPageId = result.transferredPageId)
    }

    fun setPageTemplate(documentId: String, pageId: String, pattern: PaperPattern) = update(documentId) { document ->
        val template = PageTemplateCatalog.builtIns.first { it.background == PageBackground.Paper(pattern = pattern) }
        document.copy(pages = document.pages.map { page -> if (page.id == pageId) PageTemplateOperations.apply(page, template) else page }, updatedAt = System.currentTimeMillis())
    }

    fun applyPageTemplate(documentId: String, pageId: String, templateId: String) = update(documentId) { document ->
        val template = mutablePageTemplates.value.template(templateId) ?: return@update document
        document.copy(
            pages = document.pages.map { page -> if (page.id == pageId) PageTemplateOperations.apply(page, template) else page },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun importPageTemplates(uri: Uri, displayName: String): List<PageTemplate> = assets.importPageTemplates(uri, displayName).also { imported ->
        mutablePageTemplates.value = pageTemplateStore.add(imported)
    }

    fun savePageAsTemplate(documentId: String, pageId: String, name: String): PageTemplate? {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return null
        val template = PageTemplateOperations.fromPage(page, name)
        mutablePageTemplates.value = pageTemplateStore.add(listOf(template))
        return template
    }

    fun togglePageTemplateFavorite(templateId: String) {
        mutablePageTemplates.value = pageTemplateStore.toggleFavorite(templateId)
    }

    fun setNotebookDefaultTemplate(documentId: String, templateId: String?) {
        mutablePageTemplates.value = pageTemplateStore.setNotebookDefault(documentId, templateId)
    }

    fun deletePageTemplate(templateId: String) {
        mutablePageTemplates.value = pageTemplateStore.delete(templateId)
    }

    fun toggleBookmark(documentId: String, pageId: String) = update(documentId) { document -> document.copy(pages = document.pages.map { if (it.id == pageId) it.copy(bookmarked = !it.bookmarked) else it }, updatedAt = System.currentTimeMillis()) }

    fun changePageOutlineLevel(documentId: String, pageId: String, delta: Int) = update(documentId) { document ->
        document.copy(pages = PageOutline.changeLevel(document.pages, pageId, delta), updatedAt = System.currentTimeMillis())
    }

    fun togglePageOutlineCollapsed(documentId: String, pageId: String) = update(documentId) { document ->
        val index = document.pages.indexOfFirst { it.id == pageId }
        if (!PageOutline.hasChildren(document.pages, index)) document else document.copy(
            pages = document.pages.mapIndexed { pageIndex, page -> if (pageIndex == index) page.copy(outlineCollapsed = !page.outlineCollapsed) else page },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun renamePage(documentId: String, pageId: String, title: String) = updatePage(documentId, pageId) { page -> page.copy(title = title.trim().ifBlank { page.title }) }
    fun setPageMetadata(documentId: String, pageId: String, customNumber: String?, tags: List<String>) = updatePage(documentId, pageId) { page ->
        page.copy(customNumber = customNumber?.trim()?.ifBlank { null }, tags = normalizeTags(tags))
    }
    fun clearPageInk(documentId: String, pageId: String) = updatePage(documentId, pageId) { page ->
        val locked = page.layers.filter { LayerHierarchy.effectiveLocked(page.layers, it) }.mapTo(mutableSetOf()) { it.id }
        page.copy(strokes = page.strokes.filter { it.layerId in locked })
    }
    fun setPageCrop(documentId: String, pageId: String, crop: PageCrop) = updatePage(documentId, pageId) { page ->
        page.copy(crop = PageCropTransform.normalized(crop))
    }
    fun rotatePageClockwise(documentId: String, pageId: String) = update(documentId) { document ->
        val rotated = document.pages.map { page -> if (page.id == pageId) PageGeometry.rotateClockwise(page) else page }
        document.copy(pages = PdfGotoLinkNavigation.rotateDestinationCoordinates(rotated, pageId), updatedAt = System.currentTimeMillis())
    }

    fun addLayer(documentId: String, pageId: String, name: String = "Layer") = updatePage(documentId, pageId) { page ->
        val suffix = page.layers.count { it.role == LayerRole.CUSTOM } + 1
        page.copy(layers = page.layers + InkLayer(name = if (name == "Layer") "Layer $suffix" else name.trim().ifBlank { "Layer $suffix" }))
    }

    fun addLayerFolder(documentId: String, pageId: String) = updatePage(documentId, pageId) { page ->
        val suffix = page.layers.count { it.role == LayerRole.FOLDER } + 1
        page.copy(layers = page.layers + InkLayer(name = "Folder $suffix", role = LayerRole.FOLDER))
    }

    fun renameLayer(documentId: String, pageId: String, layerId: String, name: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    fun toggleLayerVisibility(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(visible = !it.visible) else it })
    }

    fun toggleLayerLock(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(locked = !it.locked) else it })
    }

    fun setLayerOpacity(documentId: String, pageId: String, layerId: String, opacity: Float) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it })
    }

    fun setLayerBlendMode(documentId: String, pageId: String, layerId: String, blendMode: LayerBlendMode) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) it.copy(blendMode = blendMode) else it })
    }

    fun moveLayerToFolder(documentId: String, pageId: String, layerId: String, folderId: String?) = updatePage(documentId, pageId) { page ->
        LayerHierarchy.moveToFolder(page, layerId, folderId)
    }

    fun toggleLayerFolderCollapsed(documentId: String, pageId: String, folderId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == folderId && it.role == LayerRole.FOLDER) it.copy(collapsed = !it.collapsed) else it })
    }

    fun moveLayer(documentId: String, pageId: String, layerId: String, delta: Int) = updatePage(documentId, pageId) { page ->
        val from = page.layers.indexOfFirst { it.id == layerId }
        val to = (from + delta).coerceIn(0, page.layers.lastIndex)
        if (from < 0 || from == to) page else page.copy(layers = page.layers.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun duplicateLayer(documentId: String, pageId: String, layerId: String): String? {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return null
        val result = LayerOperations.duplicate(page, layerId) ?: return null
        updatePage(documentId, pageId) { result.page }
        return result.activeLayerId
    }

    fun mergeLayerDown(documentId: String, pageId: String, layerId: String): String? {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return null
        val result = LayerOperations.mergeDown(page, layerId) ?: return null
        updatePage(documentId, pageId) { result.page }
        return result.activeLayerId
    }

    fun deleteLayer(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return@updatePage page
        when (layer.role) {
            LayerRole.FOLDER -> LayerHierarchy.removeFolder(page, layerId)
            LayerRole.CUSTOM -> page.copy(
                layers = page.layers.filterNot { it.id == layerId },
                strokes = page.strokes.map { if (it.layerId == layerId) it.copy(layerId = LayerIds.INK) else it },
                elements = page.elements.map { element -> if (element.layerId == layerId) element.withLayer(LayerIds.OBJECTS) else element },
            )
            else -> page
        }
    }

    fun addStroke(documentId: String, pageId: String, stroke: InkStroke) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        if (page.layers.none { it.id == stroke.layerId && it.role != LayerRole.FOLDER && LayerHierarchy.effectiveVisible(page.layers, it) && !LayerHierarchy.effectiveLocked(page.layers, it) }) return
        store.append(JournalEntry.StrokeAdded(documentId, pageId, stroke)); refreshSelection()
    }
    fun removeStroke(documentId: String, pageId: String, strokeId: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val stroke = page.strokes.firstOrNull { it.id == strokeId } ?: return
        if (page.layers.firstOrNull { it.id == stroke.layerId }?.locked == true) return
        store.append(JournalEntry.StrokeRemoved(documentId, pageId, strokeId)); refreshSelection()
    }
    fun replaceStrokes(documentId: String, pageId: String, removedStrokeIds: List<String>, strokes: List<InkStroke>, replacementIndices: List<Int>) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val lockedLayerIds = page.layers.filter { it.locked }.mapTo(mutableSetOf()) { it.id }
        if (page.strokes.any { it.id in removedStrokeIds && it.layerId in lockedLayerIds }) return
        if (strokes.any { replacement -> page.layers.none { it.id == replacement.layerId && it.visible && !it.locked } }) return
        if (replacementIndices.size != strokes.size) return
        store.append(JournalEntry.StrokesReplaced(documentId, pageId, removedStrokeIds.distinct(), strokes, replacementIndices)); refreshSelection()
    }
    fun addElement(documentId: String, pageId: String, element: PageElement) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        if (page.layers.none { it.id == element.layerId && it.visible && !it.locked }) return
        store.append(JournalEntry.ElementUpserted(documentId, pageId, element)); refreshSelection()
    }
    fun removeElement(documentId: String, pageId: String, elementId: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val element = page.elements.firstOrNull { it.id == elementId } ?: return
        if (page.layers.firstOrNull { it.id == element.layerId }?.locked == true) return
        store.append(JournalEntry.ElementRemoved(documentId, pageId, elementId)); refreshSelection()
    }
    fun duplicateElement(documentId: String, pageId: String, elementId: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val source = page.elements.firstOrNull { it.id == elementId } ?: return
        if (page.layers.firstOrNull { it.id == source.layerId }?.locked == true) return
        val copy = copyElement(source).offsetBy(.03f, .03f)
        store.append(JournalEntry.ElementUpserted(documentId, pageId, copy)); refreshSelection()
    }

    fun currentDocument(): InkDocument? = mutableState.value.selectedDocumentId?.let(store::get)
    fun currentPage(): InkPage? = currentDocument()?.pages?.firstOrNull { it.id == mutableState.value.selectedPageId }
    fun exportCurrent(uri: Uri) { currentDocument()?.let { assets.exportPdf(it, uri) } }
    fun exportCurrentPage(uri: Uri) { currentPage()?.let { assets.exportPagePng(it, uri) } }
    fun exportCurrentBundle(uri: Uri) { currentDocument()?.let { assets.exportBundle(it, uri) } }

    fun exportLibraryBackup(uri: Uri, brushes: List<ImportedBrush>, brushGroups: List<String>, preferences: ByteArray?, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        backupService.export(store.list(), loadFolders(), brushes, output, brushGroups, preferences, mutablePageTemplates.value)
                    } ?: error("Could not open the backup destination")
                }
            }
            onComplete(result)
        }
    }

    fun restoreLibraryBackup(uri: Uri, onComplete: (Result<LibraryRestoreResult>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val restored = getApplication<Application>().contentResolver.openInputStream(uri)?.use(backupService::restore)
                        ?: error("Selected backup is unreadable")
                    restored.documents.forEach(store::save)
                    saveFolders(loadFolders() + restored.folders)
                    val existingTemplates = pageTemplateStore.load()
                    mutablePageTemplates.value = pageTemplateStore.save(existingTemplates.copy(
                        customTemplates = (existingTemplates.customTemplates + restored.pageTemplates.customTemplates).distinctBy(PageTemplate::id),
                        favoriteIds = existingTemplates.favoriteIds + restored.pageTemplates.favoriteIds,
                        notebookDefaults = existingTemplates.notebookDefaults + restored.pageTemplates.notebookDefaults,
                    ))
                    restored
                }
            }
            result.getOrNull()?.let { restored ->
                refresh()
                restored.documents.firstOrNull()?.let { select(it.id) }
            }
            onComplete(result)
        }
    }

    fun indexPdfText(documentId: String) {
        if (documentId in mutableState.value.pdfIndexingDocumentIds) return
        val document = store.get(documentId) ?: return
        if (document.pages.none { it.background is PageBackground.Pdf && it.pdfTextIndexVersion == 0 }) return
        mutableState.value = mutableState.value.copy(pdfIndexingDocumentIds = mutableState.value.pdfIndexingDocumentIds + documentId)
        viewModelScope.launch {
            try {
                val indexed = withContext(Dispatchers.IO) { store.get(documentId)?.let(assets::indexPdfText) }
                if (indexed != null) withContext(Dispatchers.IO) {
                    val latest = store.get(documentId) ?: return@withContext
                    val indexedPages = indexed.pages.associateBy(InkPage::id)
                    val mergedPages = latest.pages.map { current ->
                        val indexedPage = indexedPages[current.id] ?: return@map current
                        if (current.background != indexedPage.background || current.backgroundRotationQuarterTurns != indexedPage.backgroundRotationQuarterTurns) current
                        else current.copy(pdfTextBlocks = indexedPage.pdfTextBlocks, pdfTextIndexVersion = indexedPage.pdfTextIndexVersion)
                    }
                    if (mergedPages != latest.pages) store.save(latest.copy(pages = mergedPages))
                }
            } finally {
                refreshSelection()
                mutableState.value = mutableState.value.copy(pdfIndexingDocumentIds = mutableState.value.pdfIndexingDocumentIds - documentId)
            }
        }
    }

    private fun update(id: String, transform: (InkDocument) -> InkDocument) { store.get(id)?.let(transform)?.let(store::save); refreshSelection() }
    private fun updatePage(documentId: String, pageId: String, transform: (InkPage) -> InkPage) = update(documentId) { document ->
        document.copy(pages = document.pages.map { if (it.id == pageId) transform(it) else it }, updatedAt = System.currentTimeMillis())
    }
    private fun refresh(recovered: Int = mutableState.value.recoveredOperations) { mutableState.value = mutableState.value.copy(documents = store.list(), folders = loadFolders(), recoveredOperations = recovered) }
    private fun refreshSelection() { refresh(); mutableState.value.selectedDocumentId?.let { id -> if (store.get(id) == null) mutableState.value = mutableState.value.copy(selectedDocumentId = null, selectedPageId = null) } }

    private fun copyElement(element: PageElement): PageElement = when (element) {
        is PageElement.Text -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Sticky -> element.copy(
            id = java.util.UUID.randomUUID().toString(),
            strokes = element.strokes.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
        )
        is PageElement.Shape -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Image -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.AudioMarker -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Link -> element.copy(id = java.util.UUID.randomUUID().toString())
    }

    private fun PageElement.withLayer(layerId: String): PageElement = when (this) {
        is PageElement.Text -> copy(layerId = layerId)
        is PageElement.Sticky -> copy(layerId = layerId)
        is PageElement.Shape -> copy(layerId = layerId)
        is PageElement.Image -> copy(layerId = layerId)
        is PageElement.AudioMarker -> copy(layerId = layerId)
        is PageElement.Link -> copy(layerId = layerId)
    }

    private fun PageElement.offsetBy(dx: Float, dy: Float): PageElement = when (this) {
        is PageElement.Text -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Sticky -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Shape -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Image -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.AudioMarker -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Link -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
    }

    private fun loadFolders(): List<InkFolder> = if (!indexFile.isFile) emptyList() else runCatching { json.decodeFromString<List<InkFolder>>(indexFile.readText()) }.getOrDefault(emptyList())
    private fun saveFolders(folders: List<InkFolder>) {
        val temporary = File(indexFile.parentFile, ".library.json.tmp")
        temporary.writeText(json.encodeToString(folders))
        if (!temporary.renameTo(indexFile)) { indexFile.writeText(temporary.readText()); temporary.delete() }
    }

    private fun normalizeTags(tags: List<String>): List<String> = tags.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase).take(24)
}
