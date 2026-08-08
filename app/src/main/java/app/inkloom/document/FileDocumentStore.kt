package app.inkloom.document

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FileDocumentStore(private val root: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val journalJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val documentsDir = File(root, "documents")
    private val journalFile = File(root, "journal.jsonl")
    private val documents = linkedMapOf<String, InkDocument>()
    private var pendingJournalEntries = 0

    init {
        documentsDir.mkdirs()
        documentsDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<InkDocument>(it.readText()) }.getOrNull() }
            ?.forEach { documents[it.id] = it }
        // The journal is the durable source of truth between checkpoints. Replaying it into the
        // in-memory index makes startup and secondary readers see the latest ink without forcing
        // every pen-up to rewrite and parse the whole document library on the UI thread.
        val recovered = readJournal()
        recovered.forEach(::applyEntry)
        pendingJournalEntries = recovered.size
    }

    @Synchronized
    fun list(): List<InkDocument> = documents.values.sortedByDescending { it.updatedAt }

    @Synchronized
    fun get(id: String): InkDocument? = documents[id]

    @Synchronized
    fun save(document: InkDocument) {
        atomicWrite(documentFile(document.id), json.encodeToString(document))
        documents[document.id] = document
    }

    @Synchronized
    fun deletePermanently(id: String): Boolean {
        documents.remove(id)
        return documentFile(id).delete()
    }

    @Synchronized
    fun append(entry: JournalEntry) {
        journalFile.parentFile?.mkdirs()
        journalFile.appendText(journalJson.encodeToString<JournalEntry>(entry) + "\n")
        applyEntry(entry)
        pendingJournalEntries++
    }

    @Synchronized
    fun recover(): Int {
        val recovered = pendingJournalEntries
        if (recovered == 0) return 0
        documents.values.forEach { document -> atomicWrite(documentFile(document.id), json.encodeToString(document)) }
        compactJournal()
        pendingJournalEntries = 0
        return recovered
    }

    @Synchronized
    fun compactJournal() {
        atomicWrite(journalFile, "")
        pendingJournalEntries = 0
    }

    private fun readJournal(): List<JournalEntry> = if (!journalFile.isFile) emptyList() else
        journalFile.readLines().mapNotNull { runCatching { journalJson.decodeFromString<JournalEntry>(it) }.getOrNull() }

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
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            target.writeText(content)
            temporary.delete()
        }
    }
}
