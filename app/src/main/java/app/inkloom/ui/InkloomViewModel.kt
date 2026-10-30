package app.inkloom.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.inkloom.document.*
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.DrawingColorStore
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.storage.RecoveryJournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
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

data class PdfImportProgress(
    val completed: Int,
    val total: Int,
    val committing: Boolean = false,
    val addingPages: Boolean = false,
    val fileIndex: Int = 1,
    val fileCount: Int = 1,
)

/** Width the ink is rendered at for the handwriting search index. */
private const val HANDWRITING_OCR_WIDTH = 1600
/** How long the ink on a page stays still before it is indexed for search. */
private const val HANDWRITING_INDEX_DELAY_MS = 6_000L

class InkloomViewModel(application: Application) : AndroidViewModel(application) {
    // Page edits (every pen-up) go to the journal at once and into the notebook's checkpoint in the
    // background once the ink has settled for a moment.
    private val store = FileDocumentStore(application.filesDir, checkpointDelayMillis = 3_000L)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val indexFile = File(application.filesDir, "library.json")
    private val backupService = LibraryBackupService(application.filesDir)
    private val pageTemplateStore = PageTemplateStore(application.filesDir)
    private val notebookResumeStore = NotebookResumeStore(application)
    val assets = DocumentAssetService(application)
    val selectionRuntime = DocumentSelectionRuntime()
    private val textRecognition = OnDeviceTextRecognitionService(application)
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state = mutableState.asStateFlow()
    private val mutablePageTemplates = MutableStateFlow(pageTemplateStore.load())
    val pageTemplates = mutablePageTemplates.asStateFlow()
    private val mutablePdfImportProgress = MutableStateFlow<PdfImportProgress?>(null)
    val pdfImportProgress = mutablePdfImportProgress.asStateFlow()
    private var pdfImportJob: Job? = null
    private val pendingPdfIndexDocumentIds = mutableSetOf<String>()
    private var assetGarbageCollectionJob: Job? = null

    override fun onCleared() {
        // Page edits are already durable in the journal; the next start checkpoints them, so
        // leaving the editor never waits for a pending checkpoint.
        store.close()
        selectionRuntime.clear()
        super.onCleared()
    }

    private val inspectedDocumentUpgrades = mutableSetOf<String>()
    private val pendingDocumentUpgrades = mutableSetOf<String>()
    private val documentUpgradeErrors = mutableMapOf<String, String>()

    init {
        // A new or emptied Library stays empty: sample notebooks would return after the user deleted them.
        val recovered = store.recover()
        refresh(recovered)
        scheduleAssetGarbageCollection(delayMillis = 0L)
    }

    fun createNote(
        title: String = "Untitled Note",
        coverStyle: CoverStyle = CoverStyle.BLACK,
        templateId: String = PageTemplateCatalog.blank.id,
        paperColorArgb: Long = PaperColorCatalog.WHITE,
    ): InkDocument = createNoteDocument(title, coverStyle, null, templateId, paperColorArgb)

    fun importNotebookCover(uri: Uri, name: String, crop: PageCrop): NotebookCoverTemplate {
        val cover = NotebookCoverTemplate(name = name.trim().ifBlank { "Cover" }.take(80), assetPath = assets.importCoverImage(uri, crop))
        mutablePageTemplates.value = pageTemplateStore.addCover(cover)
        return cover
    }

    fun deleteNotebookCover(id: String) {
        mutablePageTemplates.value = pageTemplateStore.deleteCover(id)
        scheduleAssetGarbageCollection()
    }

    fun changeNotebookCover(documentId: String, style: CoverStyle, coverId: String?): Boolean {
        val path = if (style == CoverStyle.CUSTOM_IMAGE) {
            val cover = mutablePageTemplates.value.customCovers.firstOrNull { it.id == coverId } ?: return false
            cover.assetPath.takeIf { File(it).isFile } ?: return false
        } else null
        if (store.get(documentId) == null) return false
        update(documentId) { document ->
            document.copy(coverStyle = style, coverArgb = NotebookCoverCatalog.defaultArgb(style),
                coverImagePath = path, updatedAt = System.currentTimeMillis())
        }
        scheduleAssetGarbageCollection()
        return true
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
    fun importPdfAsync(uri: Uri, title: String, onComplete: (Result<InkDocument>) -> Unit) {
        if (pdfImportJob?.isActive == true) {
            onComplete(Result.failure(IllegalStateException("A PDF import is already running")))
            return
        }
        mutablePdfImportProgress.value = PdfImportProgress(0, 0)
        pdfImportJob = viewModelScope.launch {
            val result = try {
                val document = withContext(Dispatchers.IO) {
                    val running = coroutineContext
                    assets.importPdf(uri, title, indexText = false,
                        onProgress = { completed, total -> mutablePdfImportProgress.value = PdfImportProgress(completed, total) },
                        checkCancelled = { running.ensureActive() })
                }
                coroutineContext.ensureActive()
                mutablePdfImportProgress.value = PdfImportProgress(document.pages.size, document.pages.size, committing = true)
                withContext(Dispatchers.IO + NonCancellable) { store.save(document) }
                Result.success(document)
            } catch (failure: Throwable) { Result.failure(failure) }
            mutablePdfImportProgress.value = null
            result.onSuccess { document -> refresh(); select(document.id); indexPdfText(document.id) }
            onComplete(result)
            pdfImportJob = null
        }
    }
    fun cancelPdfImport() { if (mutablePdfImportProgress.value?.committing != true) pdfImportJob?.cancel(CancellationException("PDF import cancelled")) }
    fun importArtworkAsync(uri: Uri, title: String, onComplete: (Result<InkDocument>) -> Unit) {
        if (pdfImportJob?.isActive == true) return
        mutablePdfImportProgress.value = PdfImportProgress(0, 0)
        pdfImportJob = viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                val document = assets.importArtwork(uri, title)
                coroutineContext.ensureActive()
                store.save(document)
                document
            } }
            result.getOrNull()?.let { refresh(); select(it.id) }
            mutablePdfImportProgress.value = null
            onComplete(result)
        }
    }

    fun importImage(uri: Uri, title: String) = assets.importImage(uri, title).also { store.save(it); refresh(); select(it.id) }
    fun appendFilesAsync(documentId: String, files: List<Pair<Uri, String>>, onComplete: (Result<Int>) -> Unit) {
        if (files.isEmpty()) return
        if (pdfImportJob?.isActive == true) {
            onComplete(Result.failure(IllegalStateException("Another document import is already running")))
            return
        }
        if (store.get(documentId)?.takeIf { it.trashedAt == null } == null) {
            onComplete(Result.failure(IllegalStateException("The target notebook is unavailable")))
            return
        }
        mutablePdfImportProgress.value = PdfImportProgress(0, 0, addingPages = true)
        pdfImportJob = viewModelScope.launch {
            val result = try {
                val pages = withContext(Dispatchers.IO) {
                    val running = coroutineContext
                    buildList {
                        files.forEachIndexed { index, (uri, name) ->
                            running.ensureActive()
                            mutablePdfImportProgress.value = PdfImportProgress(0, 0, addingPages = true, fileIndex = index + 1, fileCount = files.size)
                            addAll(assets.importAdditionalPages(uri, name,
                                onProgress = { completed, total -> mutablePdfImportProgress.value = PdfImportProgress(completed, total, addingPages = true, fileIndex = index + 1, fileCount = files.size) },
                                checkCancelled = { running.ensureActive() }))
                        }
                    }
                }
                coroutineContext.ensureActive()
                mutablePdfImportProgress.value = PdfImportProgress(pages.size, pages.size, committing = true, addingPages = true)
                withContext(Dispatchers.IO + NonCancellable) {
                    store.exclusive {
                        val latest = store.get(documentId)?.takeIf { it.trashedAt == null }
                            ?: error("The target notebook is unavailable")
                        store.save(AppendImportedPages.apply(latest, pages))
                    }
                }
                Result.success(pages.first().id to pages.size)
            } catch (failure: Throwable) { Result.failure(failure) }
            mutablePdfImportProgress.value = null
            result.onSuccess { (firstPageId, count) ->
                refresh()
                if (mutableState.value.selectedDocumentId == documentId) selectPage(firstPageId)
                indexPdfText(documentId)
                onComplete(Result.success(count))
            }.onFailure { failure ->
                scheduleAssetGarbageCollection()
                onComplete(Result.failure(failure))
            }
            pdfImportJob = null
        }
    }
    fun importBundle(uri: Uri) = assets.importBundle(uri).also { store.save(it); refresh(); select(it.id) }
    fun insertImage(uri: Uri) {
        val documentId = mutableState.value.selectedDocumentId ?: return
        val pageId = mutableState.value.selectedPageId ?: return
        val crop = store.get(documentId)?.pages?.firstOrNull { it.id == pageId }?.crop ?: PageCrop()
        addElement(documentId, pageId, PageCropTransform.unproject(assets.importPageImage(uri), crop))
    }
    val fileObjects by lazy { app.inkloom.document.FileObjects(getApplication(), assets) }

    // CSP material palette: registered pictures and page templates.
    private val materialStore = app.inkloom.document.MaterialStore(application.filesDir)
    private val mutableMaterials = MutableStateFlow(materialStore.list())
    val materials = mutableMaterials.asStateFlow()

    fun registerLayerAsMaterial(documentId: String, pageId: String, layerId: String, name: String, onDone: (String?) -> Unit) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return onDone("The page is no longer open")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                materialStore.add(app.inkloom.document.MaterialImages.register(assets, getApplication<Application>().cacheDir, page, layerId, name))
            } }.onSuccess { mutableMaterials.value = it; onDone(null) }.onFailure { onDone(it.message) }
        }
    }

    fun registerPageAsTemplate(documentId: String, pageId: String, name: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        mutableMaterials.value = materialStore.add(app.inkloom.document.Material(name = name,
            kind = app.inkloom.document.MaterialKind.TEMPLATE, page = page))
    }

    /** An image material goes on a new layer, at most half the page wide. */
    fun placeMaterial(materialId: String) {
        val material = mutableMaterials.value.firstOrNull { it.id == materialId && it.imageAssetPath != null } ?: return
        val documentId = mutableState.value.selectedDocumentId ?: return
        val pageId = mutableState.value.selectedPageId ?: return
        updatePage(documentId, pageId) { page ->
            val visibleWidth = PageCropTransform.visibleWidth(page)
            val width = minOf(material.imageWidth.toFloat(), visibleWidth * .5f).coerceAtLeast(16f)
            val height = width * material.imageHeight / material.imageWidth.coerceAtLeast(1)
            val layer = InkLayer(name = material.name)
            val image = PageElement.Image(x = .25f, y = .2f, width = width, height = height, assetPath = requireNotNull(material.imageAssetPath), layerId = layer.id)
            page.copy(layers = page.layers + layer, elements = page.elements + (PageCropTransform.unproject(image, page.crop) as PageElement.Image))
        }
    }

    /** ED-22: CSP's Register material > Animation: the animation folder and its cels. */
    fun registerAnimationMaterial(documentId: String, pageId: String, folderId: String, name: String): String? {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return "The page is no longer open"
        val template = runCatching { app.inkloom.document.AnimationEdits.subtree(page, folderId) }.getOrElse { return it.message }
        mutableMaterials.value = materialStore.add(app.inkloom.document.Material(name = name, kind = app.inkloom.document.MaterialKind.ANIMATION, page = template))
        return null
    }

    /** An animation material's folder goes on the current page, above its layers. */
    fun placeAnimationMaterial(materialId: String) {
        val template = mutableMaterials.value.firstOrNull { it.id == materialId && it.kind == app.inkloom.document.MaterialKind.ANIMATION }?.page ?: return
        val documentId = mutableState.value.selectedDocumentId ?: return
        val pageId = mutableState.value.selectedPageId ?: return
        updatePage(documentId, pageId) { page -> app.inkloom.document.AnimationEdits.place(page, template) }
    }

    /** A new page after the current one, made from a template material. */
    fun addPageFromTemplate(documentId: String, materialId: String) {
        val template = mutableMaterials.value.firstOrNull { it.id == materialId }?.page ?: return
        val currentId = mutableState.value.selectedPageId
        var added: String? = null
        update(documentId) { document ->
            val page = app.inkloom.document.MaterialStore.pageFromTemplate(template, "Page ${document.pages.size + 1}").also { added = it.id }
            val index = document.pages.indexOfFirst { it.id == currentId }.let { if (it < 0) document.pages.size else it + 1 }
            document.copy(pages = document.pages.toMutableList().apply { add(index, page) }, updatedAt = System.currentTimeMillis())
        }
        added?.let { selectPage(it) }
    }

    fun deleteMaterial(materialId: String) { mutableMaterials.value = materialStore.delete(materialId); scheduleAssetGarbageCollection() }

    /** CSP Create file object: the linked image goes on a new layer named after its file. */
    fun insertFileObject(uri: Uri) {
        val documentId = mutableState.value.selectedDocumentId ?: return
        val pageId = mutableState.value.selectedPageId ?: return
        val placed = fileObjects.place(uri)
        updatePage(documentId, pageId) { page ->
            val layer = InkLayer(name = placed.source?.name?.substringBeforeLast('.')?.ifBlank { null } ?: "File object")
            page.copy(layers = page.layers + layer,
                elements = page.elements + (PageCropTransform.unproject(placed, page.crop) as PageElement.Image).copy(layerId = layer.id))
        }
    }

    /** CSP Update file objects: re-reads every changed linked file on the page; returns the updated count and missing names. */
    fun updateFileObjects(documentId: String, pageId: String, onDone: (Int, List<String>) -> Unit) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return onDone(0, emptyList())
        val linked = page.elements.filterIsInstance<PageElement.Image>().filter { it.source != null }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { linked.map { it.id to fileObjects.refresh(it) } }
            val updated = results.mapNotNull { (_, result) -> (result as? app.inkloom.document.FileObjects.Refresh.Updated)?.image }
            if (updated.isNotEmpty()) updatePage(documentId, pageId) { current ->
                current.copy(elements = current.elements.map { element -> updated.firstOrNull { it.id == element.id } ?: element })
            }
            onDone(updated.size, results.mapNotNull { (it.second as? app.inkloom.document.FileObjects.Refresh.Missing)?.name })
        }
    }

    fun select(documentId: String) {
        val document = store.get(documentId) ?: return
        mutableState.value = mutableState.value.copy(selectedDocumentId = documentId,
            selectedPageId = notebookResumeStore.restore(document) ?: document.pages.firstOrNull()?.id,
            migratingLegacyFills = documentId in pendingDocumentUpgrades,
            legacyFillMigrationError = documentUpgradeErrors[documentId])
    }

    /** Import/startup boundary. Ordinary notebook switching never scans or reinterprets old data. */
    private fun queueDocumentUpgrades(documents: List<InkDocument>) {
        documents.filter { inspectedDocumentUpgrades.add(it.id) }.forEach { document ->
            if (document.pages.none { page -> page.elements.any { it is PageElement.Fill } }) return@forEach
            pendingDocumentUpgrades += document.id
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    LegacyFillMigration(assets).migrate(document) { migrated -> store.exclusive {
                        check(store.get(document.id) == document) { "The notebook changed during the upgrade; restart to retry" }
                        store.recover()
                        store.save(migrated)
                    } }
                }
                pendingDocumentUpgrades -= document.id
                if(result is LegacyFillMigration.Result.Rejected) documentUpgradeErrors[document.id]=result.reason
                refresh()
                if(mutableState.value.selectedDocumentId==document.id) mutableState.value=mutableState.value.copy(
                    migratingLegacyFills=false,legacyFillMigrationError=documentUpgradeErrors[document.id])
            }
        }
    }

    fun dismissLegacyFillMigrationError() {
        mutableState.value = mutableState.value.copy(legacyFillMigrationError = null)
    }
    fun selectPage(pageId: String) {
        val documentId=mutableState.value.selectedDocumentId ?: return
        if(store.get(documentId)?.pages?.none { it.id==pageId } != false) return
        mutableState.value = mutableState.value.copy(selectedPageId = pageId)
        notebookResumeStore.remember(documentId,pageId)
    }

    fun rename(documentId: String, title: String) = update(documentId) { it.copy(title = title.ifBlank { it.title }, updatedAt = System.currentTimeMillis()) }
    fun toggleFavorite(documentId: String) = update(documentId) { it.copy(favorite = !it.favorite, updatedAt = System.currentTimeMillis()) }
    fun toggleArchive(documentId: String) = update(documentId) { it.copy(archivedAt = if (it.archivedAt == null) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis()) }
    fun setDocumentTags(documentId: String, tags: List<String>) = update(documentId) { it.copy(tags = normalizeTags(tags), updatedAt = System.currentTimeMillis()) }
    fun trash(documentId: String) = update(documentId) { it.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) }
    fun restore(documentId: String) = update(documentId) { document -> document.copy(trashedAt=null,folderTrashBatchId=null,
        folderId=document.folderId?.takeIf{id->mutableState.value.folders.any{it.id==id && it.trashedAt==null}},updatedAt=System.currentTimeMillis()) }
    fun deletePermanently(documentId: String): Boolean {
        if (!store.deletePermanently(documentId)) return false
        notebookResumeStore.forget(documentId)
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
    fun moveNotesToFolder(documentIds: Set<String>, folderId: String?): Boolean {
        val existing = store.list()
        val moved = LibraryFolderOperations.moveDocuments(existing, mutableState.value.folders, documentIds, folderId) ?: return false
        val changed = moved.mapIndexedNotNull { index, document -> existing[index].takeIf { it != document }?.let { old -> old to document } }
        val published = mutableListOf<InkDocument>()
        try {
            changed.forEach { (old, updated) -> store.save(updated); published += old }
        } catch (_: Exception) {
            published.asReversed().forEach { old -> runCatching { store.save(old) } }
            refresh()
            return false
        }
        refresh()
        return true
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
    fun moveFolders(folderIds: Set<String>, destinationId: String?): Boolean {
        val current = mutableState.value.folders
        val moved = LibraryFolderOperations.moveFolders(current, folderIds, destinationId) ?: return false
        return runCatching { if (moved != current) saveFolders(moved); refresh(); true }
            .getOrElse { refresh(); false }
    }

    fun deleteFolders(folderIds: Set<String>): Boolean {
        val originalFolders = mutableState.value.folders
        val originalDocuments = store.list()
        val result = LibraryFolderOperations.trashMany(originalFolders, originalDocuments, folderIds) ?: return false
        val changed = result.documents.mapIndexedNotNull { index, document ->
            originalDocuments[index].takeIf { it != document }?.let { old -> old to document }
        }
        val published = mutableListOf<InkDocument>()
        return try {
            changed.forEach { (old, updated) -> store.save(updated); published += old }
            saveFolders(result.folders)
            refresh()
            true
        } catch (_: Exception) {
            published.asReversed().forEach { old -> runCatching { store.save(old) } }
            runCatching { saveFolders(originalFolders) }
            refresh()
            false
        }
    }
    fun restoreFolder(folderId:String):Boolean {
        val oldFolders=mutableState.value.folders;val oldDocuments=store.list()
        val restored=LibraryFolderOperations.restore(oldFolders,oldDocuments,folderId)?:return false
        val saved=mutableListOf<InkDocument>()
        return try {
            restored.documents.zip(oldDocuments).filter{(next,old)->next!=old}.forEach{(next,old)->store.save(next);saved+=old}
            saveFolders(restored.folders);refresh();true
        }catch(_:Exception){saved.asReversed().forEach{runCatching{store.save(it)}};runCatching{saveFolders(oldFolders)};refresh();false}
    }

    fun deleteFolder(folderId: String) {
        deleteFolders(setOf(folderId))
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

    fun insertPageNextTo(documentId: String, pageId: String, after: Boolean) = update(documentId) { document ->
        val sourceIndex = document.pages.indexOfFirst { it.id == pageId }
        if (sourceIndex < 0) return@update document
        val source = document.pages[sourceIndex]
        val page = PageTemplateOperations.newBlankPageBeside(source, "Page ${document.pages.size + 1}")
        val insertionIndex = sourceIndex + if (after) 1 else 0
        mutableState.value = mutableState.value.copy(selectedPageId = page.id)
        document.copy(
            pages = document.pages.toMutableList().apply { add(insertionIndex, page) },
            updatedAt = System.currentTimeMillis(),
        )
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
        val document = store.get(documentId) ?: return
        if (document.pages.size <= 1 || document.pages.none { it.id == pageId }) return
        val active = mutableState.value.selectedDocumentId == documentId
        val visible = if (active) mutableState.value.selectedPageId else notebookResumeStore.restore(document)
        val next = PageBatchOperations.pageAfterRemoval(document.pages.map { it.id }, setOf(pageId), visible)
        // Publish navigation only after the document was saved successfully.
        store.save(document.copy(pages = document.pages.filterNot { it.id == pageId }, updatedAt = System.currentTimeMillis()))
        if (active) mutableState.value = mutableState.value.copy(selectedPageId = next)
        next?.let { notebookResumeStore.remember(documentId, it) }
        refreshSelection()
        selectionRuntime.releasePage(documentId, pageId)
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
        transferPages(sourceDocumentId, setOf(pageId), targetDocumentId, copy)
    }

    fun transferPages(sourceDocumentId: String, pageIds: Set<String>, targetDocumentId: String, copy: Boolean): Boolean {
        if (sourceDocumentId == targetDocumentId) return false
        val source = store.get(sourceDocumentId) ?: return false
        val target = store.get(targetDocumentId) ?: return false
        val result = PageTransfer.applyMany(source, target, pageIds, copy) ?: return false
        try {
            store.save(result.target)
            if (!copy) store.save(result.source)
        } catch (_: Exception) {
            runCatching { store.save(target) }
            refresh()
            return false
        }
        refresh()
        mutableState.value = mutableState.value.copy(selectedDocumentId = targetDocumentId, selectedPageId = result.transferredPageId)
        notebookResumeStore.remember(targetDocumentId,result.transferredPageId)
        return true
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

    fun saveImagePaperTemplate(template: PageTemplate): PageTemplate {
        require(template.background is PageBackground.Image && template.name.isNotBlank())
        require(template.width in 32f..20_000f && template.height in 32f..20_000f)
        mutablePageTemplates.value = pageTemplateStore.add(listOf(template))
        return requireNotNull(mutablePageTemplates.value.template(template.id))
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
    fun applyPageGeometry(documentId: String, pageId: String, edit: app.inkloom.document.PageGeometryEdit, undo: Boolean): Boolean {
        val current = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        val next = edit.apply(current, undo) ?: return false
        updatePage(documentId, pageId) { next }
        return true
    }

    /** A canvas rotation also turns the destinations of PDF links that point at the page. */
    fun applyCanvasOrientation(documentId: String, pageId: String, edit: app.inkloom.document.PageGeometryEdit,
                               clockwiseQuarterTurns: Int, undo: Boolean): Boolean {
        val current = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        val next = edit.apply(current, undo) ?: return false
        val turns = (if (undo) -clockwiseQuarterTurns else clockwiseQuarterTurns).mod(4)
        update(documentId) { document ->
            var pages = document.pages.map { page -> if (page.id == pageId) app.inkloom.document.LayerSoloStructure.reconcile(page, next) else page }
            repeat(turns) { pages = PdfGotoLinkNavigation.rotateDestinationCoordinates(pages, pageId) }
            document.copy(pages = pages, updatedAt = System.currentTimeMillis())
        }
        return true
    }

    fun setPageCrop(documentId: String, pageId: String, crop: PageCrop) = updatePage(documentId, pageId) { page ->
        page.copy(crop = PageCropTransform.normalized(crop))
    }
    /** Same preparation as the canvas command, so layer masks turn with the page. */
    fun rotatePageClockwise(documentId: String, pageId: String) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { assets.prepareCanvasOrientation(page, app.inkloom.document.CanvasOrientationChange.ROTATE_CLOCKWISE) }
                    .use { prepared -> applyCanvasOrientation(documentId, pageId, prepared.edit, 1, undo = false) }
            }.onFailure { android.util.Log.e("InkloomPages", "Page rotation failed", it) }
        }
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
        vector: app.inkloom.document.VectorLayerContent? = null,
    ) {
        updatePage(documentId, pageId) { page ->
            page.copy(elements = layerElements?.apply(page.elements) ?: page.elements, layers = page.layers.map { layer ->
                if (layer.id != layerId || !layer.isPaintSurfaceLayer()) layer
                else layer.copy(
                    rasterTileSurface = reference,
                    rasterRotationQuarterTurns = rotationQuarterTurns.mod(4),
                    // Lines only update a layer that is still a vector layer.
                    vector = if (layer.vector != null && vector != null) vector else layer.vector,
                )
            })
        }
        scheduleHandwritingIndex(documentId, pageId)
    }

    private val handwritingIndexJobs = mutableMapOf<String, Job>()

    /**
     * Keeps handwriting searchable without a manual step, as GoodNotes and Notability do: once the
     * ink on a page has been still for a few seconds, the page's ink is read again on the device.
     */
    private fun scheduleHandwritingIndex(documentId: String, pageId: String) {
        handwritingIndexJobs.remove(pageId)?.cancel()
        handwritingIndexJobs[pageId] = viewModelScope.launch {
            delay(HANDWRITING_INDEX_DELAY_MS)
            val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return@launch
            val fingerprint = HandwritingIndex.fingerprint(page)
            if (fingerprint == page.handwritingIndexFingerprint) return@launch
            val language = if (java.util.Locale.getDefault().language == "zh") "zh-Hans" else "en-US"
            val recognized = withContext(Dispatchers.IO) { runCatching { recognizeHandwriting(page, language) }.getOrNull() } ?: return@launch
            updatePage(documentId, pageId) { current ->
                if (HandwritingIndex.fingerprint(current) != fingerprint) current
                else current.copy(handwritingIndexFingerprint = fingerprint, recognizedTextBlocks =
                    current.recognizedTextBlocks.filterNot { it.source == RecognizedTextSource.HANDWRITING } + recognized)
            }
        }
    }

    /** A layer's rulers and their settings; null removes them. */
    fun setLayerRulers(documentId: String, pageId: String, layerId: String, rulers: app.inkloom.document.LayerRulers?) =
        updatePage(documentId, pageId) { page -> page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(rulers = rulers) else it }) }

    fun toggleLayerAlphaLock(documentId: String, pageId: String, layerId: String) = updatePage(documentId, pageId) { page ->
        val existingContentIds = page.elements.filter { it.layerId == layerId }.map(PageElement::id)
        page.copy(layers = page.layers.map { layer ->
            if (layer.id != layerId || !layer.isPaintSurfaceLayer()) layer
            else if (layer.alphaLock) layer.copy(alphaLock = false, alphaLockBaseContentIds = emptyList())
            else layer.copy(alphaLock = true, alphaLockBaseContentIds = existingContentIds)
        })
    }

    fun toggleLayerDrawingAssist(documentId: String, pageId: String, layerId: String, guide: app.inkloom.document.LayerDrawingGuide) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { layer ->
            if (layer.id != layerId || !layer.isPaintSurfaceLayer()) layer
            else if (layer.drawingAssist) layer.copy(drawingAssist = false)
            else layer.copy(drawingAssist = true, drawingGuide = guide)
        })
    }

    fun updateLayerDrawingGuide(documentId: String, pageId: String, layerId: String, guide: app.inkloom.document.LayerDrawingGuide) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { layer ->
            if (layer.id == layerId && layer.drawingAssist && layer.isPaintSurfaceLayer()) layer.copy(drawingGuide = guide) else layer
        })
    }

    fun toggleLayerFolderCollapsed(documentId: String, pageId: String, folderId: String) = updatePage(documentId, pageId) { page ->
        page.copy(layers = page.layers.map { if (it.id == folderId && it.role == LayerRole.FOLDER) it.copy(collapsed = !it.collapsed) else it })
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
        if (!edit.canApply(current, undo)) return false
        updatePage(documentId, pageId) { edit.apply(it, undo) }
        return true
    }

    fun applyRasterLayerFilter(documentId: String, pageId: String, edit: RasterLayerFilterEdit,
        undo: Boolean, expected: InkPage?): Boolean {
        val current = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        if (expected != null && current != expected) return false
        val changed = edit.apply(current, undo) ?: return false
        updatePage(documentId, pageId) { changed }
        return true
    }

    fun applyLayerTreeEdit(documentId: String, pageId: String, edit: LayerTreeEdit, undo: Boolean,
        expected: InkPage?): Boolean {
        val current = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return false
        if (expected != null && current != expected) return false
        val changed = edit.apply(current, undo) ?: return false
        updatePage(documentId, pageId) { changed }
        return true
    }

    fun deleteLayer(documentId: String, pageId: String, layerId: String) = updateLayerStructure(documentId, pageId) { page ->
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return@updateLayerStructure page
        when (layer.role) {
            LayerRole.FOLDER -> LayerHierarchy.removeFolder(page, layerId)
            LayerRole.CUSTOM, LayerRole.ADJUSTMENT -> page.copy(
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
            runCatching { store.awaitRealtimeWrites() }
            // GC consumes a document snapshot. A newer realtime tile commit may replace and
            // release one of that snapshot's immutable manifests before tracing begins. Treat
            // that as a stale collection attempt and retry on the next scheduled pass; asset GC
            // must never take down the editor process.
            runCatching {
                assets.garbageCollectUnreferencedAssets(store.list() + app.inkloom.document.MaterialStore.asDocument(materialStore.list()),
                    mutablePageTemplates.value)
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
    /** Writes a notebook's PDF in the background, so long notebooks do not freeze the window. */
    fun exportDocumentPdf(documentId: String, uri: Uri, onComplete: (Result<Unit>) -> Unit) =
        exportInBackground(documentId, onComplete) { assets.exportPdf(it, uri) }
    fun exportDocumentBundle(documentId: String, uri: Uri, onComplete: (Result<Unit>) -> Unit) =
        exportInBackground(documentId, onComplete) { assets.exportBundle(it, uri) }
    fun writeDocumentPdf(documentId: String, file: java.io.File, onComplete: (Result<Unit>) -> Unit) =
        exportInBackground(documentId, onComplete) { document -> file.outputStream().use { assets.exportPdf(document, it) } }
    private fun exportInBackground(documentId: String, onComplete: (Result<Unit>) -> Unit, write: (InkDocument) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { write(store.get(documentId) ?: error("This notebook is no longer available")) }
            }
            onComplete(result)
        }
    }
    fun exportCurrentPage(uri: Uri, options: app.inkloom.document.PageRasterExportOptions = app.inkloom.document.PageRasterExportOptions(), onComplete: (Result<Unit>) -> Unit) {
        val page = currentPage() ?: run {
            onComplete(Result.failure(IllegalStateException("No page is open for export")))
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { assets.exportPageRaster(page, uri, options) } }
            onComplete(result)
        }
    }

    /** An animation folder of the current page as an animated GIF or MP4 video [width] pixels wide. */
    fun exportAnimation(uri: Uri, folderId: String, width: Int, format: app.inkloom.document.AnimationExportFormat, onComplete: (Result<Unit>) -> Unit) {
        val page = currentPage() ?: run { onComplete(Result.failure(IllegalStateException("No page is open for export"))); return }
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (format) {
                        app.inkloom.document.AnimationExportFormat.GIF ->
                            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open the file" }.use { out ->
                                app.inkloom.document.AnimationExport.writeGif(assets, page, folderId, width, out.buffered())
                            }
                        app.inkloom.document.AnimationExportFormat.MP4 ->
                            requireNotNull(resolver.openFileDescriptor(uri, "rwt")) { "Could not open the file" }.use { file ->
                                app.inkloom.document.AnimationVideoExport.write(assets, page, folderId, width, file.fileDescriptor)
                            }
                    }
                }
            }
            onComplete(result)
        }
    }

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

    fun restoreLibraryBackup(uri: Uri, recoverIntactItems: Boolean = false, onComplete: (Result<LibraryRestoreResult>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val restored = getApplication<Application>().contentResolver.openInputStream(uri)?.use { backupService.restore(it, recoverIntactItems) }
                        ?: error("Selected backup is unreadable")
                    val existingFolders = loadFolders()
                    val existingTemplates = pageTemplateStore.load()
                    require(restored.documents.none { store.get(it.id) != null }) { "Backup document ID conflicts with the current library" }
                    val published = mutableListOf<String>()
                    try {
                        restored.documents.forEach { document -> store.save(document); published += document.id }
                        saveFolders(existingFolders + restored.folders)
                        mutablePageTemplates.value = pageTemplateStore.save(existingTemplates.copy(
                            customTemplates = (existingTemplates.customTemplates + restored.pageTemplates.customTemplates).distinctBy(PageTemplate::id),
                            customCovers = (existingTemplates.customCovers + restored.pageTemplates.customCovers).distinctBy(NotebookCoverTemplate::id),
                            favoriteIds = existingTemplates.favoriteIds + restored.pageTemplates.favoriteIds,
                            notebookDefaults = existingTemplates.notebookDefaults + restored.pageTemplates.notebookDefaults,
                            notebookPaperColors = existingTemplates.notebookPaperColors + restored.pageTemplates.notebookPaperColors,
                        ))
                    } catch (failure: Throwable) {
                        published.forEach { id -> if (!store.deletePermanently(id)) failure.addSuppressed(IllegalStateException("Could not roll back restored notebook $id")) }
                        runCatching { saveFolders(existingFolders) }.onFailure(failure::addSuppressed)
                        runCatching { mutablePageTemplates.value = pageTemplateStore.save(existingTemplates) }.onFailure(failure::addSuppressed)
                        throw failure
                    }
                    restored
                }
            }
            val accepted = result.getOrNull()
            if (accepted != null) {
                refresh()
                val firstDocument = accepted.documents.firstOrNull()
                if (firstDocument != null) select(firstDocument.id)
            }
            onComplete(result)
        }
    }

    fun indexPdfText(documentId: String) {
        if (Build.VERSION.SDK_INT < 35) return
        if (documentId in mutableState.value.pdfIndexingDocumentIds) {
            pendingPdfIndexDocumentIds += documentId
            return
        }
        val document = store.get(documentId) ?: return
        if (document.pages.none { it.background is PageBackground.Pdf && it.pdfTextIndexVersion == 0 }) return
        mutableState.value = mutableState.value.copy(pdfIndexingDocumentIds = mutableState.value.pdfIndexingDocumentIds + documentId)
        viewModelScope.launch {
            try {
                val indexed = withContext(Dispatchers.IO) { store.get(documentId)?.let(assets::indexPdfText) }
                if (indexed != null) withContext(Dispatchers.IO) {
                    store.exclusive {
                        val latest = store.get(documentId) ?: return@exclusive
                        val indexedPages = indexed.pages.associateBy(InkPage::id)
                        val mergedPages = latest.pages.map { current ->
                            val indexedPage = indexedPages[current.id] ?: return@map current
                            if (current.background != indexedPage.background || current.backgroundRotationQuarterTurns != indexedPage.backgroundRotationQuarterTurns) current
                            else current.copy(
                                pdfTextBlocks = indexedPage.pdfTextBlocks,
                                pdfTextIndexVersion = indexedPage.pdfTextIndexVersion,
                                pdfGotoLinks = if (current.pdfLinkIndexVersion > 0) current.pdfGotoLinks else indexedPage.pdfGotoLinks,
                                pdfLinkIndexVersion = maxOf(current.pdfLinkIndexVersion, indexedPage.pdfLinkIndexVersion),
                            )
                        }
                        if (mergedPages != latest.pages) store.save(latest.copy(pages = mergedPages))
                    }
                }
            } finally {
                refreshSelection()
                mutableState.value = mutableState.value.copy(pdfIndexingDocumentIds = mutableState.value.pdfIndexingDocumentIds - documentId)
                if (pendingPdfIndexDocumentIds.remove(documentId)) indexPdfText(documentId)
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
                        page.id to (recognizeImages(page, languageTag) + recognizeHandwriting(page, languageTag))
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
                        val inkFingerprint = HandwritingIndex.fingerprint(snapshotPage)
                        val inkStillCurrent = HandwritingIndex.fingerprint(page) == inkFingerprint
                        fun current(source: RecognizedTextSource) =
                            if (source == RecognizedTextSource.IMAGE_OCR) imagesStillCurrent else inkStillCurrent
                        page.copy(
                            recognizedTextBlocks = page.recognizedTextBlocks.filterNot { block -> current(block.source) } +
                                recognized.filter { block -> current(block.source) },
                            imageOcrFingerprint = if (imagesStillCurrent) imageFingerprint else page.imageOcrFingerprint,
                            handwritingIndexFingerprint = if (inkStillCurrent) inkFingerprint else page.handwritingIndexFingerprint,
                        )
                    }, updatedAt = System.currentTimeMillis())
                }
            }
            mutableState.value = mutableState.value.copy(textRecognitionDocumentIds = mutableState.value.textRecognitionDocumentIds - documentId)
            onComplete(result.map { pages -> pages.values.sumOf(List<RecognizedTextBlock>::size) })
        }
    }

    /** Adds a recognised handwriting line as a text object; the ink stays (lasso > Convert to text replaces it). */
    fun convertRecognizedHandwritingToText(documentId: String, pageId: String, blockId: String) {
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
    /** One page changes: only that page is written (to the journal), never the whole notebook. */
    private fun updatePage(documentId: String, pageId: String, transform: (InkPage) -> InkPage) {
        val page = store.get(documentId)?.pages?.firstOrNull { it.id == pageId } ?: return
        val updated = app.inkloom.document.LayerSoloStructure.reconcile(page, transform(page))
        if (updated == page) return
        store.replacePage(documentId, updated)
        refreshSelection()
    }
    private fun refresh(recovered: Int = mutableState.value.recoveredOperations) {
        val documents=store.list()
        queueDocumentUpgrades(documents)
        mutableState.value=mutableState.value.copy(documents=documents,folders=loadFolders(),recoveredOperations=recovered,
            migratingLegacyFills=mutableState.value.selectedDocumentId in pendingDocumentUpgrades)
    }
    private fun refreshSelection() {
        refresh()
        mutableState.value.selectedDocumentId?.let { id ->
            val document=store.get(id)
            if(document==null) mutableState.value=mutableState.value.copy(selectedDocumentId=null,selectedPageId=null)
            else {
                val pageId=mutableState.value.selectedPageId?.takeIf { candidate -> document.pages.any { it.id==candidate } }
                    ?: document.pages.firstOrNull()?.id
                mutableState.value=mutableState.value.copy(selectedPageId=pageId)
                pageId?.let { notebookResumeStore.remember(id,it) }
            }
        }
    }

    private suspend fun recognizeImages(page: InkPage, languageTag: String): List<RecognizedTextBlock> = buildList {
        if (page.background is PageBackground.Image || (page.background is PageBackground.Pdf && page.pdfTextBlocks.isEmpty())) {
            val bitmap = assets.renderBackgroundForOcr(page)
            try {
                val crop = PageCropTransform.normalized(page.crop)
                addAll(textRecognition.recognizeBitmap(bitmap, "background", languageTag).map { block ->
                    val b = block.bounds
                    block.copy(bounds = PdfTextBounds(crop.left + b.left*(crop.right-crop.left), crop.top + b.top*(crop.bottom-crop.top),
                        crop.left + b.right*(crop.right-crop.left), crop.top + b.bottom*(crop.bottom-crop.top)))
                })
            } finally { bitmap.recycle() }
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

    /**
     * Indexes the page's handwriting for search: only the ink layers, on plain paper, so PDF text,
     * images and text objects (indexed elsewhere) are not read twice.
     */
    private suspend fun recognizeHandwriting(page: InkPage, languageTag: String): List<RecognizedTextBlock> {
        if (page.layers.none { it.isPaintSurfaceLayer() && it.visible && it.rasterTileSurface != null }) return emptyList()
        val inkOnly = page.copy(background = PageBackground.Paper(), backgroundFrame = null, elements = emptyList())
        val bitmap = assets.renderPageBitmap(inkOnly, HANDWRITING_OCR_WIDTH)
        return try {
            val crop = PageCropTransform.normalized(page.crop)
            textRecognition.recognizeBitmap(bitmap, "handwriting", languageTag).map { block ->
                val b = block.bounds
                block.copy(source = RecognizedTextSource.HANDWRITING, bounds = PdfTextBounds(
                    crop.left + b.left * (crop.right - crop.left), crop.top + b.top * (crop.bottom - crop.top),
                    crop.left + b.right * (crop.right - crop.left), crop.top + b.bottom * (crop.bottom - crop.top)))
            }
        } finally { bitmap.recycle() }
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

    /** Every refresh (each pen-up) asks for the folders; parse library.json only when it changed. */
    private var cachedFolders: Pair<Pair<Long, Long>, List<InkFolder>>? = null
    private fun loadFolders(): List<InkFolder> {
        if (!indexFile.isFile) return emptyList()
        val stamp = indexFile.lastModified() to indexFile.length()
        cachedFolders?.let { (cachedStamp, folders) -> if (cachedStamp == stamp) return folders }
        return runCatching { json.decodeFromString<List<InkFolder>>(indexFile.readText()) }.getOrDefault(emptyList())
            .also { cachedFolders = stamp to it }
    }
    private fun saveFolders(folders: List<InkFolder>) {
        val temporary = File(indexFile.parentFile, ".library.json.tmp")
        temporary.writeText(json.encodeToString(folders))
        if (!temporary.renameTo(indexFile)) { indexFile.writeText(temporary.readText()); temporary.delete() }
        cachedFolders = (indexFile.lastModified() to indexFile.length()) to folders
    }

    private fun normalizeTags(tags: List<String>): List<String> = tags.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase).take(24)
}
