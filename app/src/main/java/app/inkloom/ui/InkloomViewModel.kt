package app.inkloom.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.inkloom.document.*
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.DrawingColorStore
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.storage.RecoveryJournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val imageOcrPageIds: Set<String> = emptySet(),
    val textRecognitionDocumentIds: Set<String> = emptySet(),
    val migratingLegacyFills: Boolean = false,
    val legacyFillMigrationError: String? = null,
)

class InkloomViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FileDocumentStore(application.filesDir)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val indexFile = File(application.filesDir, "library.json")
    private val backupService = LibraryBackupService(application.filesDir)
    private val pageTemplateStore = PageTemplateStore(application.filesDir)
    val assets = DocumentAssetService(application)
    val selectionRuntime = DocumentSelectionRuntime()
    private val textRecognition = OnDeviceTextRecognitionService(application)
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state = mutableState.asStateFlow()
    private val mutablePageTemplates = MutableStateFlow(pageTemplateStore.load())
    val pageTemplates = mutablePageTemplates.asStateFlow()
    private var assetGarbageCollectionJob: Job? = null

    override fun onCleared() {
        store.awaitRealtimeWrites()
        selectionRuntime.clear()
        super.onCleared()
    }

    init {
        val recovered = store.recover()
        if (store.list().isEmpty()) {
            listOf(
                InkDocument(title = "Distributed Systems", coverArgb = 0xFF202020, pages = List(3) { InkPage(title = "Page ${it + 1}") }),
                InkDocument(title = "CS336 Reading Notes", coverArgb = 0xFF707070, pages = List(2) { InkPage(title = "Page ${it + 1}") }),
            ).forEach(store::save)
        }
        refresh(recovered)
        scheduleAssetGarbageCollection(delayMillis = 0L)
    }

    fun createNote(
        title: String = "Untitled Note",
        coverStyle: CoverStyle = CoverStyle.BLACK,
        templateId: String = PageTemplateCatalog.blank.id,
        paperColorArgb: Long = PaperColorCatalog.WHITE,
    ): InkDocument = createNoteDocument(title, coverStyle, null, templateId, paperColorArgb)

    fun importNotebookCover(uri: Uri, name: String): NotebookCoverTemplate {
        val cover = NotebookCoverTemplate(name = name.trim().ifBlank { "Cover" }.take(80), assetPath = assets.importCoverImage(uri))
        mutablePageTemplates.value = pageTemplateStore.addCover(cover)
        return cover
    }

    fun deleteNotebookCover(id: String) {
        mutablePageTemplates.value = pageTemplateStore.deleteCover(id)
        scheduleAssetGarbageCollection()
    }

    fun createNoteUsingCover(title: String, coverId: String, templateId: String, paperColorArgb: Long): InkDocument {
        val cover = requireNotNull(pageTemplateStore.load().customCovers.firstOrNull { it.id == coverId }) { "Cover no longer exists" }
        require(java.io.File(cover.assetPath).isFile) { "Cover image is missing" }
        return createNoteDocument(title, CoverStyle.CUSTOM_IMAGE, cover.assetPath, templateId, paperColorArgb)
    }

    private fun createNoteDocument(
        title: String,
        coverStyle: CoverStyle,
        coverImagePath: String?,
        templateId: String,
        paperColorArgb: Long,
    ): InkDocument {
        val baseTemplate = mutablePageTemplates.value.template(templateId) ?: PageTemplateCatalog.blank
        val template = PageTemplateOperations.withPaperColor(baseTemplate, paperColorArgb)
        val document = InkDocument(
            title = title.trim().ifBlank { "Untitled Note" },
            coverStyle = coverStyle,
            coverArgb = NotebookCoverCatalog.defaultArgb(coverStyle),
            coverImagePath = coverImagePath,
            pages = listOf(PageTemplateOperations.newPage(template, "Page 1")),
        )
        store.save(document)
        mutablePageTemplates.value = pageTemplateStore.setNotebookDefault(document.id, baseTemplate.id)
        mutablePageTemplates.value = pageTemplateStore.setNotebookPaperColor(
            document.id,
            (template.background as? PageBackground.Paper)?.colorArgb,
        )
        refresh()
        select(document.id)
        return document
    }
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
    fun select(documentId: String) {
        val document = store.get(documentId) ?: return
        val needsMigration = document.pages.any { page -> page.elements.any { it is PageElement.Fill } }
        mutableState.value = mutableState.value.copy(selectedDocumentId = documentId,
            selectedPageId = document.pages.firstOrNull()?.id, migratingLegacyFills = needsMigration,
            legacyFillMigrationError = null)
        if (!needsMigration) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                LegacyFillMigration(assets).migrate(document) { migrated ->
                    synchronized(store) {
                        check(store.get(documentId) == document) { "The notebook changed while converting fills; please reopen it to retry" }
                        // Old element journal entries must not resurrect the converted Fill on restart.
                        store.recover()
                        store.save(migrated)
                    }
                }
            }
            refresh()
            if (mutableState.value.selectedDocumentId == documentId) {
                mutableState.value = mutableState.value.copy(migratingLegacyFills = false,
                    legacyFillMigrationError = (result as? LegacyFillMigration.Result.Rejected)?.reason)
            }
        }
    }
    fun dismissLegacyFillMigrationError() {
        mutableState.value = mutableState.value.copy(legacyFillMigrationError = null)
    }
    fun selectPage(pageId: String) { mutableState.value = mutableState.value.copy(selectedPageId = pageId) }

    fun rename(documentId: String, title: String) = update(documentId) { it.copy(title = title.ifBlank { it.title }, updatedAt = System.currentTimeMillis()) }
    fun toggleFavorite(documentId: String) = update(documentId) { it.copy(favorite = !it.favorite, updatedAt = System.currentTimeMillis()) }
    fun toggleArchive(documentId: String) = update(documentId) { it.copy(archivedAt = if (it.archivedAt == null) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis()) }
    fun setDocumentTags(documentId: String, tags: List<String>) = update(documentId) { it.copy(tags = normalizeTags(tags), updatedAt = System.currentTimeMillis()) }
    fun trash(documentId: String) = update(documentId) { it.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) }
    fun restore(documentId: String) = update(documentId) { it.copy(trashedAt = null, updatedAt = System.currentTimeMillis()) }
    fun deletePermanently(documentId: String): Boolean {
        if (!store.deletePermanently(documentId)) return false
        selectionRuntime.releaseDocument(documentId)
        DrawingColorStore(getApplication()).remove(documentId)
        refreshSelection()
        scheduleAssetGarbageCollection()
        return true
    }
    fun duplicateDocument(documentId: String) {
        val source = store.get(documentId) ?: return
        val pageIds = source.pages.associate { it.id to java.util.UUID.randomUUID().toString() }
        val copiedPages = source.pages.map { page -> page.copy(id = pageIds.getValue(page.id), elements = page.elements.map(::copyElement)) }
        val copy = source.copy(id = java.util.UUID.randomUUID().toString(), title = "${source.title} copy", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), pages = PdfGotoLinkNavigation.remapDestinations(copiedPages, pageIds))
        store.save(copy); refresh()
    }
    fun moveToFolder(documentId: String, folderId: String?) = update(documentId) {
        LibraryFolderOperations.moveDocument(it, mutableState.value.folders, folderId)
    }
    fun createFolder(name: String, parentId: String? = null) {
        val updated = LibraryFolderOperations.create(mutableState.value.folders, name, parentId)
        if (updated == mutableState.value.folders) return
        saveFolders(updated); refresh()
    }
    fun renameFolder(folderId: String, name: String) {
        val updated = LibraryFolderOperations.rename(mutableState.value.folders, folderId, name)
        if (updated == mutableState.value.folders) return
        saveFolders(updated); refresh()
    }
    fun deleteFolder(folderId: String) {
        val result = LibraryFolderOperations.remove(mutableState.value.folders, store.list(), folderId)
        result.documents.filter { document -> store.get(document.id) != document }.forEach(store::save)
        saveFolders(result.folders); refresh()
    }

    fun addPage(documentId: String, templateId: String? = null, paperColorArgb: Long? = null) = update(documentId) { document ->
        val library = mutablePageTemplates.value
        val template = PageTemplateOperations.withPaperColor(
            library.template(templateId) ?: library.defaultFor(documentId),
            paperColorArgb,
        )
        val page = PageTemplateOperations.newPage(template, "Page ${document.pages.size + 1}")
        mutableState.value = mutableState.value.copy(selectedPageId = page.id)
        document.copy(pages = document.pages + page, updatedAt = System.currentTimeMillis())
    }

    fun appendPageUsingTemplate(documentId: String, pageId: String) = update(documentId) { document ->
        val source = document.pages.firstOrNull { it.id == pageId } ?: return@update document
        val page = InkPage(
            title = "Page ${document.pages.size + 1}",
            width = source.width,
            height = source.height,
            background = source.background,
            backgroundRotationQuarterTurns = source.backgroundRotationQuarterTurns,
            crop = source.crop,
        )
        mutableState.value = mutableState.value.copy(selectedPageId = page.id)
        document.copy(pages = document.pages + page, updatedAt = System.currentTimeMillis())
    }

    fun duplicatePage(documentId: String, pageId: String) = update(documentId) { document ->
        val source = document.pages.first { it.id == pageId }
        val copiedPageId = java.util.UUID.randomUUID().toString()
        val copy = PdfGotoLinkNavigation.remapDestinations(
            listOf(source.copy(id = copiedPageId, title = "${source.title} copy", elements = source.elements.map(::copyElement))),
            mapOf(source.id to copiedPageId),
        ).single()
        val index = document.pages.indexOf(source) + 1
        val pages = document.pages.toMutableList().apply { add(index, copy) }
        mutableState.value = mutableState.value.copy(selectedPageId = copy.id)
        document.copy(pages = pages, updatedAt = System.currentTimeMillis())
    }

    fun deletePage(documentId: String, pageId: String) {
        val willDelete = store.get(documentId)?.pages?.size?.let { it > 1 } == true
        update(documentId) { document ->
            if (document.pages.size == 1) document else {
                val pages = document.pages.filterNot { it.id == pageId }
                mutableState.value = mutableState.value.copy(selectedPageId = pages.first().id)
                document.copy(pages = pages, updatedAt = System.currentTimeMillis())
            }
        }
        if (willDelete) selectionRuntime.releasePage(documentId, pageId)
        scheduleAssetGarbageCollection()
    }

    fun movePage(documentId: String, pageId: String, delta: Int) = update(documentId) { document ->
        val from = document.pages.indexOfFirst { it.id == pageId }
        val to = (from + delta).coerceIn(0, document.pages.lastIndex)
        if (from < 0 || from == to) document else document.copy(pages = document.pages.toMutableList().apply { add(to, removeAt(from)) }, updatedAt = System.currentTimeMillis())
    }

    fun reorderPages(documentId: String, orderedPageIds: List<String>) = update(documentId) { document ->
        val byId = document.pages.associateBy { it.id }
        if (orderedPageIds.size != document.pages.size || orderedPageIds.toSet() != byId.keys) document
        else document.copy(pages = orderedPageIds.map(byId::getValue), updatedAt = System.currentTimeMillis())
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

    fun applyPageTemplate(documentId: String, pageId: String, templateId: String, paperColorArgb: Long? = null) = update(documentId) { document ->
        val template = PageTemplateOperations.withPaperColor(
            mutablePageTemplates.value.template(templateId) ?: return@update document,
            paperColorArgb,
        )
        document.copy(
            pages = document.pages.map { page -> if (page.id == pageId) PageTemplateOperations.apply(page, template) else page },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun applyPageTemplateToPages(
        documentId: String,
        pageIds: Set<String>,
        templateId: String,
        paperColorArgb: Long? = null,
    ) = update(documentId) { document ->
        val targets = pageIds.intersect(document.pages.mapTo(linkedSetOf(), InkPage::id))
        if (targets.isEmpty()) return@update document
        val template = PageTemplateOperations.withPaperColor(
            mutablePageTemplates.value.template(templateId) ?: return@update document,
            paperColorArgb,
        )
        document.copy(
            pages = PageTemplateOperations.applyToPages(document.pages, targets, template),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun importPageTemplates(uri: Uri, displayName: String): List<PageTemplate> = assets.importPageTemplates(uri, displayName).also { imported ->
        mutablePageTemplates.value = pageTemplateStore.add(imported)
    }

    fun createCustomPageTemplate(name: String, width: Int, height: Int, paper: PageBackground.Paper): PageTemplate? {
        val template = PageTemplateOperations.customPaper(name, width, height, paper) ?: return null
        val saved = pageTemplateStore.add(listOf(template))
        mutablePageTemplates.value = saved
        return saved.template(template.id)
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

    fun setNotebookDefaultTemplate(documentId: String, templateId: String?, paperColorArgb: Long? = null) {
        mutablePageTemplates.value = pageTemplateStore.setNotebookDefault(documentId, templateId)
        mutablePageTemplates.value = pageTemplateStore.setNotebookPaperColor(documentId, paperColorArgb)
    }

    fun deletePageTemplate(templateId: String) {
        mutablePageTemplates.value = pageTemplateStore.delete(templateId)
        scheduleAssetGarbageCollection()
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
        RasterLayerDocumentOperations.clearUnlocked(page)
    }
    fun setPageCrop(documentId: String, pageId: String, crop: PageCrop) = updatePage(documentId, pageId) { page ->
        page.copy(crop = PageCropTransform.normalized(crop))
    }
    fun rotatePageClockwise(documentId: String, pageId: String) = update(documentId) { document ->
        val rotated = document.pages.map { page -> if (page.id == pageId) PageGeometry.rotateClockwise(page) else page }
        document.copy(pages = PdfGotoLinkNavigation.rotateDestinationCoordinates(rotated, pageId), updatedAt = System.currentTimeMillis())
    }

    fun applySelectionLayerTransfer(documentId: String, pageId: String, sourceId: String,
        sourceReference: RasterTileSurfaceRef?, targetId: String, target: InkLayer?) =
        updatePage(documentId, pageId) { page ->
            val layers = page.layers.filterNot { it.id == targetId }.map {
                if (it.id == sourceId && sourceReference != null) it.copy(rasterTileSurface = sourceReference) else it
            }.toMutableList()
            if (target != null) layers.add((layers.indexOfFirst { it.id == sourceId } + 1).coerceIn(0, layers.size), target)
            page.copy(layers = layers)
        }

    fun setSavedSelections(documentId: String, pageId: String, selections: List<SavedSelection>) =
        updatePage(documentId, pageId) { it.copy(savedSelections = selections) }

    fun applyLayerInsertion(documentId: String, pageId: String, edit: LayerInsertionEdit, undo: Boolean): Boolean {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        val changed = edit.apply(page, undo) ?: return false
        updatePage(documentId, pageId) { changed }
        return true
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

    fun applyLayerAppearanceEdit(documentId: String, pageId: String, edit: LayerAppearanceEdit, undo: Boolean): Boolean {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        val changed = edit.apply(page, undo) ?: return false
        updatePage(documentId, pageId) { changed }
        return true
    }

    fun setLayerRasterTileSurface(
        documentId: String,
        pageId: String,
        layerId: String,
        reference: RasterTileSurfaceRef?,
        rotationQuarterTurns: Int = 0,
        layerElements: app.inkloom.document.LayerElementEdit? = null,
    ) = updatePage(documentId, pageId) { page ->
        page.copy(elements = layerElements?.apply(page.elements) ?: page.elements, layers = page.layers.map { layer ->
            if (layer.id != layerId || !layer.isPaintSurfaceLayer()) layer
            else layer.copy(
                rasterTileSurface = reference,
                rasterRotationQuarterTurns = rotationQuarterTurns.mod(4),
            )
        })
    }

    fun toggleLayerAlphaLock(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        val existingContentIds = page.elements.filter { it.layerId == layerId }.map(PageElement::id)
        page.copy(layers = page.layers.map { layer ->
            if (layer.id != layerId || layer.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) layer
            else if (layer.alphaLock) layer.copy(alphaLock = false, alphaLockBaseContentIds = emptyList())
            else layer.copy(alphaLock = true, alphaLockBaseContentIds = existingContentIds)
        })
    }

    fun toggleLayerClippedToBelow(documentId: String, pageId: String, layerId: String) = updateLayerStructure(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) it.copy(clippedToBelow = !it.clippedToBelow) else it })
    }

    fun toggleLayerReference(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId && it.role != LayerRole.BACKGROUND) it.copy(reference = !it.reference) else it })
    }

    fun toggleLayerDraft(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == layerId && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) it.copy(draft = !it.draft) else it })
    }

    fun moveLayerToFolder(documentId: String, pageId: String, layerId: String, folderId: String?) = updateLayerStructure(documentId, pageId) { page ->
        LayerHierarchy.moveToFolder(page, layerId, folderId)
    }

    fun toggleLayerFolderCollapsed(documentId: String, pageId: String, folderId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == folderId && it.role == LayerRole.FOLDER) it.copy(collapsed = !it.collapsed) else it })
    }

    fun moveLayer(documentId: String, pageId: String, layerId: String, delta: Int) = updateLayerStructure(documentId, pageId) { page ->
        LayerHierarchy.moveBy(page, layerId, delta)
    }

    fun duplicateLayer(documentId: String, pageId: String, layerId: String): String? {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return null
        val result = LayerOperations.duplicate(page, layerId) ?: return null
        updatePage(documentId, pageId) { result.page }
        return result.activeLayerId
    }

    fun applyRasterLayerMerge(documentId: String, pageId: String, edit: RasterLayerMergeEdit, undo: Boolean, expected: InkPage?): Boolean {
        val current = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        if (expected != null && current != expected) return false
        if (current.layers.none { it.id == edit.target.id }) return false
        updatePage(documentId, pageId) { edit.apply(it, undo) }
        return true
    }

    fun deleteLayer(documentId: String, pageId: String, layerId: String) = updateLayerStructure(documentId, pageId) { page ->
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return@updateLayerStructure page
        when (layer.role) {
            LayerRole.FOLDER -> LayerHierarchy.removeFolder(page, layerId)
            LayerRole.CUSTOM -> page.copy(
                layers = page.layers.filterNot { it.id == layerId },
                elements = page.elements.map { element -> if (element.layerId == layerId) element.withLayer(LayerIds.OBJECTS) else element },
            )
            else -> page
        }
    }

    fun addElement(documentId: String, pageId: String, element: PageElement) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        if (page.layers.none { it.id == element.layerId && it.visible && !it.locked }) return
        store.append(RecoveryJournalEntry.ElementUpserted(documentId, pageId, element)); refreshSelection()
    }
    fun removeElement(documentId: String, pageId: String, elementId: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val element = page.elements.firstOrNull { it.id == elementId } ?: return
        if (page.layers.firstOrNull { it.id == element.layerId }?.locked == true) return
        store.append(RecoveryJournalEntry.ElementRemoved(documentId, pageId, elementId)); refreshSelection()
        scheduleAssetGarbageCollection()
    }

    private fun scheduleAssetGarbageCollection(delayMillis: Long = 500L) {
        assetGarbageCollectionJob?.cancel()
        assetGarbageCollectionJob = viewModelScope.launch(Dispatchers.IO) {
            if (delayMillis > 0L) delay(delayMillis)
            store.awaitRealtimeWrites()
            // GC consumes a document snapshot. A newer realtime tile commit may replace and
            // release one of that snapshot's immutable manifests before tracing begins. Treat
            // that as a stale collection attempt and retry on the next scheduled pass; asset GC
            // must never take down the editor process.
            runCatching {
                assets.garbageCollectUnreferencedAssets(store.list(), mutablePageTemplates.value)
            }
        }
    }

    fun duplicateElement(documentId: String, pageId: String, elementId: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val source = page.elements.firstOrNull { it.id == elementId } ?: return
        if (page.layers.firstOrNull { it.id == source.layerId }?.locked == true) return
        val copy = copyElement(source).offsetBy(.03f, .03f)
        store.append(RecoveryJournalEntry.ElementUpserted(documentId, pageId, copy)); refreshSelection()
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
                        customCovers = (existingTemplates.customCovers + restored.pageTemplates.customCovers).distinctBy(NotebookCoverTemplate::id),
                        favoriteIds = existingTemplates.favoriteIds + restored.pageTemplates.favoriteIds,
                        notebookDefaults = existingTemplates.notebookDefaults + restored.pageTemplates.notebookDefaults,
                        notebookPaperColors = existingTemplates.notebookPaperColors + restored.pageTemplates.notebookPaperColors,
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

    fun recognizePageImages(
        documentId: String,
        pageId: String,
        languageTag: String,
        onComplete: (Result<Int>) -> Unit = {},
    ) {
        if (pageId in mutableState.value.imageOcrPageIds) return
        val snapshot = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        mutableState.value = mutableState.value.copy(imageOcrPageIds = mutableState.value.imageOcrPageIds + pageId)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    recognizeImages(snapshot, languageTag)
                }
            }
            result.getOrNull()?.let { blocks ->
                val fingerprint = ImageOcrIndex.fingerprint(snapshot)
                updatePage(documentId, pageId) { current ->
                    if (ImageOcrIndex.fingerprint(current) != fingerprint) current
                    else current.copy(
                        recognizedTextBlocks = current.recognizedTextBlocks.filterNot { it.source == RecognizedTextSource.IMAGE_OCR } + blocks,
                        imageOcrFingerprint = fingerprint,
                    )
                }
            }
            mutableState.value = mutableState.value.copy(imageOcrPageIds = mutableState.value.imageOcrPageIds - pageId)
            onComplete(result.map(List<RecognizedTextBlock>::size))
        }
    }

    fun recognizeDocumentText(
        documentId: String,
        languageTag: String,
        onComplete: (Result<Int>) -> Unit = {},
    ) {
        if (documentId in mutableState.value.textRecognitionDocumentIds) return
        val snapshot = store.get(documentId) ?: return
        mutableState.value = mutableState.value.copy(textRecognitionDocumentIds = mutableState.value.textRecognitionDocumentIds + documentId)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    snapshot.pages.associate { page ->
                        page.id to recognizeImages(page, languageTag)
                    }
                }
            }
            result.getOrNull()?.let { recognizedPages ->
                update(documentId) { current ->
                    current.copy(pages = current.pages.map { page ->
                        val snapshotPage = snapshot.pages.firstOrNull { it.id == page.id } ?: return@map page
                        val recognized = recognizedPages[page.id] ?: return@map page
                        val imageFingerprint = ImageOcrIndex.fingerprint(snapshotPage)
                        val imagesStillCurrent = ImageOcrIndex.fingerprint(page) == imageFingerprint
                        page.copy(
                            recognizedTextBlocks = page.recognizedTextBlocks.filterNot { block ->
                                block.source == RecognizedTextSource.IMAGE_OCR && imagesStillCurrent
                            } + (if (imagesStillCurrent) recognized else emptyList()),
                            imageOcrFingerprint = if (imagesStillCurrent) imageFingerprint else page.imageOcrFingerprint,
                        )
                    }, updatedAt = System.currentTimeMillis())
                }
            }
            mutableState.value = mutableState.value.copy(textRecognitionDocumentIds = mutableState.value.textRecognitionDocumentIds - documentId)
            onComplete(result.map { pages -> pages.values.sumOf(List<RecognizedTextBlock>::size) })
        }
    }

    fun convertRecognizedHandwritingToText(documentId: String, pageId: String, blockId: String, replaceInk: Boolean) {
        updatePage(documentId, pageId) { page ->
            val block = page.recognizedTextBlocks.firstOrNull {
                it.id == blockId && it.source == RecognizedTextSource.HANDWRITING
            } ?: return@updatePage page
            val bounds = block.bounds
            val element = PageElement.Text(
                x = bounds.left,
                y = bounds.top,
                width = ((bounds.right - bounds.left) * page.width).coerceAtLeast(160f),
                height = ((bounds.bottom - bounds.top) * page.height).coerceAtLeast(72f),
                text = block.text,
                textSize = ((bounds.bottom - bounds.top) * page.height * .72f).coerceIn(24f, 96f),
            )
            page.copy(
                elements = page.elements + element,
                recognizedTextBlocks = page.recognizedTextBlocks.filterNot { it.id == blockId },
                handwritingIndexFingerprint = page.handwritingIndexFingerprint,
            )
        }
    }

    private fun updateLayerStructure(documentId: String, pageId: String, transform: (InkPage) -> InkPage): app.inkloom.document.LayerGroupRejection? {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId }
            ?: return app.inkloom.document.LayerGroupRejection.INVALID_TARGET
        val candidate = transform(page)
        app.inkloom.document.LayerGroupPolicy.rejection(candidate.layers)?.let { return it }
        if (candidate == page) return null
        var rejection: app.inkloom.document.LayerGroupRejection? = null
        updatePage(documentId, pageId) { current ->
            val changed = transform(current)
            rejection = app.inkloom.document.LayerGroupPolicy.rejection(changed.layers)
            if (rejection == null) changed else current
        }
        return rejection
    }

    private fun update(id: String, transform: (InkDocument) -> InkDocument) { store.get(id)?.let(transform)?.let(store::save); refreshSelection() }
    private fun updatePage(documentId: String, pageId: String, transform: (InkPage) -> InkPage) = update(documentId) { document ->
        document.copy(pages = document.pages.map { if (it.id == pageId) transform(it) else it }, updatedAt = System.currentTimeMillis())
    }
    private fun refresh(recovered: Int = mutableState.value.recoveredOperations) { mutableState.value = mutableState.value.copy(documents = store.list(), folders = loadFolders(), recoveredOperations = recovered) }
    private fun refreshSelection() { refresh(); mutableState.value.selectedDocumentId?.let { id -> if (store.get(id) == null) mutableState.value = mutableState.value.copy(selectedDocumentId = null, selectedPageId = null) } }

    private suspend fun recognizeImages(page: InkPage, languageTag: String): List<RecognizedTextBlock> = buildList {
        (page.background as? PageBackground.Image)?.assetPath?.let(::File)?.takeIf(File::isFile)?.let { file ->
            addAll(textRecognition.recognizeImage(file, "background", languageTag))
        }
        page.elements.filterIsInstance<PageElement.Image>().forEach { image ->
            val file = File(image.assetPath)
            if (file.isFile) {
                addAll(textRecognition.recognizeImage(file, image.id, languageTag).mapNotNull { block ->
                    RecognizedTextGeometry.mapImageBlock(page, image, block)
                })
            }
        }
    }

    private fun copyElement(element: PageElement): PageElement = when (element) {
        is PageElement.Text -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Sticky -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Shape -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Image -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Fill -> element.copy(id = java.util.UUID.randomUUID().toString())
        is PageElement.Link -> element.copy(id = java.util.UUID.randomUUID().toString())
    }

    private fun PageElement.withLayer(layerId: String): PageElement = when (this) {
        is PageElement.Text -> copy(layerId = layerId)
        is PageElement.Sticky -> copy(layerId = layerId)
        is PageElement.Shape -> copy(layerId = layerId)
        is PageElement.Image -> copy(layerId = layerId)
        is PageElement.Fill -> copy(layerId = layerId)
        is PageElement.Link -> copy(layerId = layerId)
    }

    private fun PageElement.offsetBy(dx: Float, dy: Float): PageElement = when (this) {
        is PageElement.Text -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Sticky -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Shape -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Image -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
        is PageElement.Fill -> copy(x = (x + dx).coerceIn(0f, .95f), y = (y + dy).coerceIn(0f, .95f))
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
