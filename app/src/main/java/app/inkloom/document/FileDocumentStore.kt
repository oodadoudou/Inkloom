package app.inkloom.document

import app.inkloom.engine.v5.storage.DocumentCheckpointCodec
import app.inkloom.engine.v5.storage.RecoveryJournalEntry
import app.inkloom.engine.v5.storage.RecoveryJournalCodec
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Notebooks on disk: one integrity-checked checkpoint per notebook plus an ordered recovery journal.
 *
 * A page edit (every pen-up) is made durable by appending just that page to the journal; the
 * notebook's checkpoint is rewritten later on a background thread and the journal entries it now
 * covers are dropped. Whole-notebook changes still write the checkpoint straight away.
 *
 * Journal sequences only grow, and each checkpoint records the last one it includes. Replay skips
 * entries a checkpoint already holds, so a crash between writing a checkpoint and trimming the
 * journal never brings back an older page.
 *
 * Locking: writers take [fileLock] before the state lock (`this`), so a long background checkpoint
 * never blocks readers, and a checkpoint is never replaced by an older snapshot. Read-modify-write
 * callers use [exclusive]; `synchronized(store)` around [save] would take the locks the other way
 * round and could deadlock with the background checkpoint.
 */
class FileDocumentStore internal constructor(
    private val root: File,
    /** Delay before dirty notebooks are checkpointed in the background; null writes only on [recover]. */
    private val checkpointDelayMillis: Long? = null,
    private val replaceCheckpoint: (File, File) -> Boolean,
) {
    constructor(root: File) : this(root, null, { temporary, target -> temporary.renameTo(target) })
    /** The app's store: page edits are checkpointed in the background once the ink settles. */
    constructor(root: File, checkpointDelayMillis: Long) : this(root, checkpointDelayMillis, { temporary, target -> temporary.renameTo(target) })

    private val documentsDir = File(root, "documents")
    private val journalFile = File(root, "journal.jsonl")
    private val documents = linkedMapOf<String, InkDocument>()
    /** Notebooks with journal entries that their checkpoint does not include yet. */
    private val dirty = linkedSetOf<String>()
    /** Checkpoint files the background writer is replacing right now. */
    private val ownWrites = mutableSetOf<String>()
    private var observedDocumentFiles = emptyMap<String, Pair<Long, Long>>()
    private var observedJournalLength = 0L
    private var nextJournalSequence = 1L
    private val fileLock = Any()
    private val checkpointer: ScheduledThreadPoolExecutor? = checkpointDelayMillis?.let {
        ScheduledThreadPoolExecutor(1) { runnable -> Thread(runnable, "Inkloom-Checkpoint").apply { isDaemon = true } }.apply {
            removeOnCancelPolicy = true
            executeExistingDelayedTasksAfterShutdownPolicy = false
        }
    }
    private var scheduledCheckpoint: ScheduledFuture<*>? = null
    /** When the edits the pending checkpoint will cover began. */
    private var pendingSinceMillis = 0L

    init {
        documentsDir.mkdirs()
        loadFromDisk()
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

    /** Runs [block] holding both locks in the store's order, for read-modify-write sequences. */
    fun <T> exclusive(block: () -> T): T = synchronized(fileLock) { synchronized(this) { block() } }

    fun save(document: InkDocument) = exclusive {
        // Everything journaled for this notebook so far is superseded by the document being saved.
        val covered = nextJournalSequence - 1L
        atomicWrite(documentFile(document.id)) { DocumentCheckpointCodec.write(document, it, covered) }
        documents[document.id] = document
        dirty.remove(document.id)
        // Trimming is only housekeeping: replay already skips what the checkpoint covers.
        runCatching { dropJournalEntries { it.documentId == document.id && it.sequence <= covered } }
        captureObservedFiles()
    }

    /**
     * Replaces one page: durable at once through the journal, written into the notebook's
     * checkpoint later. This is the pen-up path, so it never encodes the whole notebook.
     */
    fun replacePage(documentId: String, page: InkPage, updatedAt: Long = System.currentTimeMillis()) {
        append(RecoveryJournalEntry.PageReplaced(documentId, page.id, page, updatedAt))
    }

    /**
     * Replaces one page with [transform] of its current state, in one step with respect to other
     * writers. Takes only the state lock, so a pen-up never waits for a checkpoint being written.
     * Returns whether the page changed.
     */
    fun updatePage(documentId: String, pageId: String, transform: (InkPage) -> InkPage): Boolean {
        val changed = synchronized(this) {
            refreshExternalChanges()
            val page = documents[documentId]?.pages?.firstOrNull { it.id == pageId } ?: return false
            val updated = transform(page)
            if (updated == page) return false
            val entry = RecoveryJournalEntry.PageReplaced(documentId, pageId, updated, System.currentTimeMillis())
            DocumentCheckpointCodec.requireNativeV5Page(updated)
            persistJournalEntry(entry)
            applied(entry)?.let { document ->
                documents[document.id] = document
                dirty += document.id
            }
            true
        }
        if (changed) scheduleCheckpoint()
        return changed
    }

    fun deletePermanently(id: String): Boolean = synchronized(fileLock) {
        synchronized(this) {
            // deleteIfExists distinguishes an already absent checkpoint from denied access.
            // The durable delete must succeed before the current library forgets its record.
            try {
                refreshExternalChanges()
                // Flush pending ink for all notebooks before removing this checkpoint.
                recoverLocked()
                java.nio.file.Files.deleteIfExists(documentFile(id).toPath())
            } catch (_: java.io.IOException) {
                return false
            } catch (_: SecurityException) {
                return false
            }
            refreshExternalChanges()
            documents.remove(id)
            captureObservedFiles()
            true
        }
    }

    internal fun append(entry: RecoveryJournalEntry) {
        synchronized(this) {
            val updated = applied(entry)
            // A page a checkpoint would refuse must not reach the journal, or every later
            // checkpoint and every start-up recovery would fail on it.
            updated?.pages?.firstOrNull { it.id == entry.pageId }?.let(DocumentCheckpointCodec::requireNativeV5Page)
            persistJournalEntry(entry)
            if (updated != null) {
                documents[updated.id] = updated
                dirty += updated.id
            }
        }
        scheduleCheckpoint()
    }

    /**
     * Writes every notebook with pending journal entries and empties the journal; returns how many
     * entries it held. A notebook that cannot be written keeps its entries. A damaged tail is cut
     * too, so new entries never land after a line replay stops at.
     */
    fun recover(): Int = exclusive { recoverLocked() }

    /** Waits until notebooks edited so far are in their checkpoints (tests, export, shutdown). */
    internal fun awaitRealtimeWrites() {
        val executor = checkpointer ?: return
        try {
            executor.submit { checkpointDirty() }.get()
        } catch (_: RejectedExecutionException) {
            // Closed: the journal still holds the edits and the next store checkpoints them.
        }
    }

    /**
     * Stops background checkpoints. Edits stay durable in the journal and the next store's
     * [recover] checkpoints them. A checkpoint already being written is waited for, so it cannot
     * land after another store has opened these files.
     */
    fun close() {
        val executor = checkpointer ?: return
        executor.shutdown()
        executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun recoverLocked(): Int {
        val pending = readJournalRecords().size
        if (pending == 0 && journalFile.length() == 0L) return 0
        val covered = nextJournalSequence - 1L
        val failed = dirty.mapNotNull(documents::get).filterNot { document ->
            runCatching { atomicWrite(documentFile(document.id)) { DocumentCheckpointCodec.write(document, it, covered) } }.isSuccess
        }.mapTo(hashSetOf()) { it.id }
        dirty.retainAll(failed)
        if (failed.isEmpty()) atomicWrite(journalFile) { } else dropJournalEntries { it.documentId !in failed }
        captureObservedFiles()
        return pending
    }

    private fun scheduleCheckpoint() {
        val executor = checkpointer ?: return
        val delay = checkpointDelayMillis ?: return
        synchronized(this) {
            if (executor.isShutdown) return
            val now = System.nanoTime() / 1_000_000L
            val previous = scheduledCheckpoint
            if (previous == null || previous.isDone) pendingSinceMillis = now
            previous?.cancel(false)
            // Each pen-up postpones the checkpoint until the ink settles, but steady drawing must
            // not postpone it forever: the journal grows by a page per stroke until then.
            val latest = pendingSinceMillis + delay * MAX_POSTPONE_FACTOR
            val wait = delay.coerceAtMost((latest - now).coerceAtLeast(0L))
            scheduledCheckpoint = try {
                executor.schedule({ checkpointDirty() }, wait, TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) {
                null
            }
        }
    }

    /**
     * Background checkpoint: writes each dirty notebook from an immutable snapshot, then drops the
     * journal entries those snapshots include. A notebook edited again meanwhile, or one that
     * could not be written, stays dirty; its entries stay in the journal.
     */
    private fun checkpointDirty() {
        synchronized(fileLock) {
            val (snapshots, coveredSequence) = synchronized(this) {
                dirty.mapNotNull { id -> documents[id] } to nextJournalSequence - 1L
            }
            if (snapshots.isEmpty()) return
            val failed = hashSetOf<String>()
            val written = snapshots.filter { document ->
                val file = documentFile(document.id)
                // Readers must not mistake this store's own write for another store's change and
                // reload the whole library from disk on the UI thread.
                synchronized(this) { ownWrites += file.name }
                val succeeded = runCatching {
                    atomicWrite(file) { DocumentCheckpointCodec.write(document, it, coveredSequence) }
                }.isSuccess
                synchronized(this) {
                    ownWrites -= file.name
                    observedDocumentFiles = observedDocumentFiles + (file.name to (file.lastModified() to file.length()))
                }
                if (!succeeded) failed += document.id
                succeeded
            }
            synchronized(this) {
                written.forEach { document -> if (documents[document.id] === document) dirty -= document.id }
                runCatching { dropJournalEntries { record -> record.sequence <= coveredSequence && record.documentId !in failed } }
                captureObservedFiles()
            }
        }
    }

    private class JournalRecord(val sequence: Long, val entry: RecoveryJournalEntry, val line: String) {
        val documentId get() = entry.documentId
    }

    /** Rewrites the journal without the records [drop] selects; later records keep their order. */
    private fun dropJournalEntries(drop: (JournalRecord) -> Boolean) {
        if (journalFile.length() == 0L) return
        val records = readJournalRecords()
        val kept = records.filterNot(drop)
        if (kept.size == records.size) return
        atomicWrite(journalFile) { output -> kept.forEach { output.write((it.line + "\n").encodeToByteArray()) } }
        observedJournalLength = journalFile.length()
    }

    private fun persistJournalEntry(entry: RecoveryJournalEntry) {
        journalFile.parentFile?.mkdirs()
        val bytes = (RecoveryJournalCodec.encode(nextJournalSequence++, entry) + "\n").encodeToByteArray()
        FileOutputStream(journalFile, true).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        observedJournalLength = journalFile.length()
    }

    /** Verified records in order; replay stops at the first damaged or out-of-order line. */
    private fun readJournalRecords(): List<JournalRecord> {
        if (!journalFile.isFile) return emptyList()
        val records = arrayListOf<JournalRecord>()
        var lastSequence = 0L
        for (line in journalFile.readLines()) {
            if (line.isBlank()) continue
            val decoded = runCatching { RecoveryJournalCodec.decode(line) }.getOrNull() ?: break
            if (decoded.sequence <= lastSequence) break
            lastSequence = decoded.sequence
            records += JournalRecord(decoded.sequence, decoded.entry, line)
        }
        return records
    }

    private fun loadFromDisk() {
        documents.clear()
        dirty.clear()
        val checkpointSequences = hashMapOf<String, Long>()
        documentsDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { runCatching { DocumentCheckpointCodec.read(it) }.getOrNull() }
            ?.forEach { stored ->
                documents[stored.document.id] = stored.document
                checkpointSequences[stored.document.id] = stored.journalSequence
            }
        // The journal is the durable source of truth between checkpoints. Replaying it into the
        // in-memory index makes startup and secondary readers see the latest ink without forcing
        // every pen-up to rewrite and parse the whole document library on the UI thread.
        val journal = readJournalRecords()
        journal.forEach { record ->
            if (record.sequence <= (checkpointSequences[record.documentId] ?: 0L)) return@forEach
            applied(record.entry)?.let { document ->
                documents[document.id] = document
                dirty += document.id
            }
        }
        nextJournalSequence = maxOf(journal.lastOrNull()?.sequence ?: 0L, checkpointSequences.values.maxOrNull() ?: 0L) + 1L
    }

    /** File stats are cheap enough for UI refreshes; JSON is reloaded only when a second store
     * instance (tests, import/export, or recovery tooling) actually changed durable state. */
    private fun refreshExternalChanges() {
        val currentDocuments = documentFileSignatures()
        val currentJournalLength = journalFile.takeIf(File::isFile)?.length() ?: 0L
        if (currentDocuments.filterKeys { it !in ownWrites } == observedDocumentFiles.filterKeys { it !in ownWrites } &&
            currentJournalLength == observedJournalLength) return
        loadFromDisk()
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

    /** The notebook with [entry] applied, or null when it is not in the library. */
    private fun applied(entry: RecoveryJournalEntry): InkDocument? {
        val document = documents[entry.documentId] ?: return null
        val pages = document.pages.map { page ->
            if (page.id != entry.pageId) page else when (entry) {
                is RecoveryJournalEntry.ElementUpserted -> page.copy(elements = page.elements.filterNot { it.id == entry.element.id } + entry.element)
                is RecoveryJournalEntry.ElementRemoved -> page.copy(elements = page.elements.filterNot { it.id == entry.elementId })
                is RecoveryJournalEntry.PageReplaced -> entry.page
            }
        }
        return document.copy(pages = pages, updatedAt = entry.timestamp)
    }

    private fun documentFile(id: String) = File(documentsDir, "${safeId(id)}.json")
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { error("Invalid document id") }

    private fun atomicWrite(target: File, write: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                val buffered = output.buffered(1 shl 16)
                write(buffered)
                buffered.flush()
                output.fd.sync()
            }
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
        if (!replaceCheckpoint(temporary, target)) {
            temporary.delete()
            throw java.io.IOException("Could not atomically replace ${target.name}; the previous checkpoint was preserved")
        }
    }

    private companion object {
        /** Steady drawing delays the checkpoint by at most this many settle delays. */
        const val MAX_POSTPONE_FACTOR = 6L
        const val CLOSE_TIMEOUT_SECONDS = 10L
    }
}
