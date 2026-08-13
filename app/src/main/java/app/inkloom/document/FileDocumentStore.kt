package app.inkloom.document

import app.inkloom.engine.v3.storage.DocumentCheckpointCodec
import app.inkloom.engine.v3.storage.RecoveryJournalCodec
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FileDocumentStore(private val root: File) {
    private val documentsDir = File(root, "documents")
    private val journalFile = File(root, "journal.jsonl")
    private val documents = linkedMapOf<String, InkDocument>()
    private var pendingJournalEntries = 0
    private var observedDocumentFiles = emptyMap<String, Pair<Long, Long>>()
    private var observedJournalLength = 0L
    private var nextJournalSequence = 1L
    private val realtimeJournalWriter: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { operation ->
            Thread(operation, "Inkloom-journal-writer").apply { isDaemon = true }
        }
    }

    init {
        documentsDir.mkdirs()
        documentsDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { runCatching { DocumentCheckpointCodec.decode(it.readText()) }.getOrNull() }
            ?.forEach { documents[it.id] = it }
        // The journal is the durable source of truth between checkpoints. Replaying it into the
        // in-memory index makes startup and secondary readers see the latest ink without forcing
        // every pen-up to rewrite and parse the whole document library on the UI thread.
        val recovered = readJournal()
        recovered.forEach(::applyEntry)
        pendingJournalEntries = recovered.size
        captureObservedFiles()
    }

    @Synchronized
    fun list(): List<InkDocument> {
        refreshExternalChanges()
        return documents.values.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun get(id: String): InkDocument? {
        refreshExternalChanges()
        return documents[id]
    }

    @Synchronized
    fun save(document: InkDocument) {
        atomicWrite(documentFile(document.id), DocumentCheckpointCodec.encode(document))
        documents[document.id] = document
        captureObservedFiles()
    }

    @Synchronized
    fun deletePermanently(id: String): Boolean {
        documents.remove(id)
        return documentFile(id).delete().also { captureObservedFiles() }
    }

    fun append(entry: JournalEntry) {
        awaitRealtimeWrites()
        synchronized(this) {
            persistJournalEntry(entry)
            applyEntry(entry)
            pendingJournalEntries++
        }
    }

    /**
     * Applies high-frequency ink edits to the in-memory document immediately and serializes them
     * on one ordered writer thread. Imported brushes carry large immutable parameter snapshots;
     * encoding those snapshots during ACTION_UP used to block the next stylus contact.
     */
    fun appendRealtime(entry: JournalEntry) {
        synchronized(this) {
            applyEntry(entry)
            pendingJournalEntries++
        }
        realtimeJournalWriter.execute {
            synchronized(this) { persistJournalEntry(entry) }
        }
    }

    fun recover(): Int {
        awaitRealtimeWrites()
        return synchronized(this) {
            val recovered = pendingJournalEntries
            if (recovered == 0) return@synchronized 0
            documents.values.forEach { document -> atomicWrite(documentFile(document.id), DocumentCheckpointCodec.encode(document)) }
            compactJournalNow()
            pendingJournalEntries = 0
            captureObservedFiles()
            recovered
        }
    }

    fun compactJournal() {
        awaitRealtimeWrites()
        synchronized(this) { compactJournalNow() }
    }

    internal fun awaitRealtimeWrites() {
        if (!realtimeJournalWriter.isShutdown) realtimeJournalWriter.submit {}.get()
    }

    private fun compactJournalNow() {
        atomicWrite(journalFile, "")
        pendingJournalEntries = 0
        nextJournalSequence = 1L
        observedJournalLength = journalFile.length()
    }

    private fun persistJournalEntry(entry: JournalEntry) {
        journalFile.parentFile?.mkdirs()
        val bytes = (RecoveryJournalCodec.encode(nextJournalSequence++, entry) + "\n").encodeToByteArray()
        FileOutputStream(journalFile, true).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        observedJournalLength = journalFile.length()
    }

    private fun readJournal(): List<JournalEntry> {
        if (!journalFile.isFile) return emptyList()
        val entries = arrayListOf<JournalEntry>()
        var lastSequence = 0L
        for (line in journalFile.readLines()) {
            if (line.isBlank()) continue
            val decoded = runCatching { RecoveryJournalCodec.decode(line) }.getOrNull() ?: break
            val sequence = decoded.sequence
            if (sequence != null) {
                if (sequence <= lastSequence) break
                lastSequence = sequence
            }
            entries += decoded.entry
        }
        nextJournalSequence = lastSequence + 1L
        return entries
    }

    /** File stats are cheap enough for UI refreshes; JSON is reloaded only when a second store
     * instance (tests, import/export, or recovery tooling) actually changed durable state. */
    private fun refreshExternalChanges() {
        val currentDocuments = documentFileSignatures()
        val currentJournalLength = journalFile.takeIf(File::isFile)?.length() ?: 0L
        if (currentDocuments == observedDocumentFiles && currentJournalLength == observedJournalLength) return
        documents.clear()
        documentsDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { runCatching { DocumentCheckpointCodec.decode(it.readText()) }.getOrNull() }
            ?.forEach { documents[it.id] = it }
        val recovered = readJournal()
        recovered.forEach(::applyEntry)
        pendingJournalEntries = recovered.size
        observedDocumentFiles = currentDocuments
        observedJournalLength = currentJournalLength
    }

    private fun captureObservedFiles() {
        observedDocumentFiles = documentFileSignatures()
        observedJournalLength = journalFile.takeIf(File::isFile)?.length() ?: 0L
    }

    private fun documentFileSignatures(): Map<String, Pair<Long, Long>> = documentsDir
        .listFiles { file -> file.extension == "json" }
        .orEmpty()
        .associate { file -> file.name to (file.lastModified() to file.length()) }

    private fun applyEntry(entry: JournalEntry): InkDocument? {
        val document = documents[entry.documentId] ?: return null
        val pages = document.pages.map { page ->
            if (page.id != entry.pageId) page else when (entry) {
                is JournalEntry.StrokeAdded -> if (page.strokes.any { it.id == entry.stroke.id }) page else page.copy(strokes = page.strokes + entry.stroke)
                is JournalEntry.StrokeRemoved -> page.copy(strokes = page.strokes.filterNot { it.id == entry.strokeId })
                is JournalEntry.StrokesReplaced -> {
                    val replacedIds = entry.removedStrokeIds.toSet() + entry.strokes.map(InkStroke::id)
                    val remaining = page.strokes.filterNot { it.id in replacedIds }.toMutableList()
                    if (entry.replacementIndices.size == entry.strokes.size) {
                        entry.strokes.zip(entry.replacementIndices)
                            .sortedBy { (_, index) -> index }
                            .forEach { (stroke, index) -> remaining.add(index.coerceIn(0, remaining.size), stroke) }
                    } else {
                        remaining += entry.strokes
                    }
                    page.copy(strokes = remaining)
                }
                is JournalEntry.ElementUpserted -> page.copy(elements = page.elements.filterNot { it.id == entry.element.id } + entry.element)
                is JournalEntry.ElementRemoved -> page.copy(elements = page.elements.filterNot { it.id == entry.elementId })
            }
        }
        return document.copy(pages = pages, updatedAt = entry.timestamp).also { documents[it.id] = it }
    }

    private fun documentFile(id: String) = File(documentsDir, "${safeId(id)}.json")
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { error("Invalid document id") }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(content.encodeToByteArray())
            output.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            target.writeText(content)
            temporary.delete()
        }
    }
}
